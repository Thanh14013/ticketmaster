package com.ticketmaster.booking.application.handler;

import com.ticketmaster.booking.application.command.CreateBookingCommand;
import com.ticketmaster.booking.application.kafka.BookingEventProducer;
import com.ticketmaster.booking.domain.model.Booking;
import com.ticketmaster.booking.domain.model.BookingItem;
import com.ticketmaster.booking.domain.repository.BookingRepository;
import com.ticketmaster.booking.domain.service.BookingDomainService;
import com.ticketmaster.booking.infrastructure.persistence.mapper.BookingMapper;
import com.ticketmaster.booking.interfaces.dto.BookingResponse;
import com.ticketmaster.common.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Handler cho use case tạo booking mới – luồng phức tạp nhất trong hệ thống.
 *
 * <p><b>Flow (phải thực hiện đúng thứ tự):</b>
 * <ol>
 *   <li>Validate: số ghế hợp lệ, user không có duplicate booking</li>
 *   <li>Lock seats: lấy Redisson distributed lock cho từng seatId</li>
 *   <li>Build BookingItems (snapshot price từ request – event-service đã validate)</li>
 *   <li>Create Booking aggregate và lưu DB (trong transaction)</li>
 *   <li>Publish {@code seat.status.changed} (AVAILABLE→LOCKED) lên Kafka</li>
 *   <li>Publish {@code booking.created} lên Kafka → payment-service xử lý</li>
 *   <li>Schedule Quartz job release seats sau 2 phút</li>
 * </ol>
 *
 * <p><b>Rollback strategy:</b>
 * <ul>
 *   <li>Nếu lock thất bại (ghế bị giữ) → không tạo booking, trả lỗi</li>
 *   <li>Nếu DB save thất bại → release tất cả locks</li>
 *   <li>Nếu Kafka publish thất bại → booking đã tạo, Quartz sẽ expire sau 2 phút</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateBookingHandler {

    private final BookingDomainService  bookingDomainService;
    private final BookingRepository     bookingRepository;
    private final BookingEventProducer  bookingEventProducer;
    private final BookingMapper         bookingMapper;
    private final Scheduler             quartzScheduler;

    @Value("${booking.seat-lock-ttl-minutes:2}")
    private int seatLockTtlMinutes;

    @Transactional
    public BookingResponse handle(CreateBookingCommand command) {
        log.info("[CREATE_BOOKING] userId={} eventId={} seats={}",
                command.getUserId(), command.getEventId(), command.getSeatIds());

        // 1. Validate
        bookingDomainService.validateSeatCount(command.getSeatIds());
        bookingDomainService.validateUserBookingLimit(command.getUserId(), command.getEventId());

        // 2. Lock seats (Redisson distributed lock)
        // Nếu lock thất bại → exception sẽ propagate, không tạo booking
        bookingDomainService.lockSeats(command.getSeatIds());

        Booking booking;
        try {
            // 3. Build BookingItems
            // Trong production: gọi event-service để lấy seat info (price, row, number)
            // Ở đây dùng placeholder – integration với event-service qua Feign/RestTemplate
            List<BookingItem> items = buildBookingItems(command);

            // 4. Create aggregate và lưu DB
            booking = Booking.create(
                    IdGenerator.newId(),
                    command.getUserId(),
                    command.getUserEmail(),
                    command.getEventId(),
                    "Event " + command.getEventId(), // eventName – lấy từ event-service trong production
                    items,
                    seatLockTtlMinutes
            );
            booking = bookingRepository.save(booking);
            log.info("[CREATE_BOOKING] Booking saved id={}", booking.getId());

        } catch (Exception e) {
            // DB save thất bại → release locks để tránh ghost locks
            log.error("[CREATE_BOOKING] DB save failed, releasing locks: {}", e.getMessage());
            bookingDomainService.releaseSeats(
                    Booking.builder().items(
                            command.getSeatIds().stream()
                                    .map(id -> BookingItem.builder().seatId(id).build())
                                    .toList()
                    ).build()
            );
            throw e;
        }

        // 5. Publish seat status LOCKED events
        for (String seatId : command.getSeatIds()) {
            bookingEventProducer.publishSeatStatusChanged(
                    seatId, command.getEventId(), "AVAILABLE", "LOCKED", booking.getId());
        }

        // 6. Publish booking.created → payment-service sẽ tạo payment intent
        bookingEventProducer.publishBookingCreated(booking);

        // 7. Schedule Quartz job để release seats sau TTL
        scheduleExpireJob(booking);

        return bookingMapper.toResponse(booking);
    }

    // ── Private Helpers ───────────────────────────────────────────

    /**
     * Build BookingItems từ command.
     * TODO: Trong production, gọi event-service API để lấy thông tin ghế thực tế.
     * Hiện tại dùng placeholder để đảm bảo tính hoàn chỉnh của luồng.
     */
    private List<BookingItem> buildBookingItems(CreateBookingCommand command) {
        return command.getSeatIds().stream()
                .map(seatId -> BookingItem.builder()
                        .id(IdGenerator.newId())
                        .bookingId(null) // sẽ set khi save
                        .seatId(seatId)
                        .sectionId("section-unknown") // lấy từ event-service
                        .seatRow("?")                 // lấy từ event-service
                        .seatNumber("?")              // lấy từ event-service
                        .sectionName("Unknown")       // lấy từ event-service
                        .price(BigDecimal.valueOf(100)) // lấy từ event-service
                        .build())
                .toList();
    }

    /**
     * Đăng ký Quartz job để tự động expire booking sau seatLockTtlMinutes phút.
     * Dùng JDBC JobStore để job survive khi service restart.
     */
    private void scheduleExpireJob(Booking booking) {
        try {
            JobDataMap jobData = new JobDataMap();
            jobData.put("bookingId", booking.getId());

            JobDetail job = JobBuilder.newJob(
                            com.ticketmaster.booking.application.scheduler.SeatReleaseScheduler.class)
                    .withIdentity("expire-" + booking.getId(), "booking-expire")
                    .usingJobData(jobData)
                    .storeDurably(false)
                    .build();

            // Trigger sau seatLockTtlMinutes phút
            Date fireAt = Date.from(booking.getExpiresAt());
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger-" + booking.getId(), "booking-expire")
                    .startAt(fireAt)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withMisfireHandlingInstructionFireNow()) // Nếu scheduler down → chạy ngay khi recover
                    .build();

            quartzScheduler.scheduleJob(job, trigger);
            log.info("[QUARTZ] Scheduled expire job for bookingId={} at {}",
                    booking.getId(), booking.getExpiresAt());

        } catch (SchedulerException e) {
            // Không throw – booking đã tạo thành công, worst case ghế sẽ expire bởi TTL Redis lock
            log.error("[QUARTZ] Failed to schedule expire job for bookingId={}: {}",
                    booking.getId(), e.getMessage(), e);
        }
    }
}