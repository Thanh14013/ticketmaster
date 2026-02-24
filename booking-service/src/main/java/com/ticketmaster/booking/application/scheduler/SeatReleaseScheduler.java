package com.ticketmaster.booking.application.scheduler;

import com.ticketmaster.booking.application.command.CancelBookingCommand;
import com.ticketmaster.booking.application.handler.CancelBookingHandler;
import com.ticketmaster.booking.domain.model.Booking;
import com.ticketmaster.booking.domain.model.BookingStatus;
import com.ticketmaster.booking.domain.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.springframework.stereotype.Component;

/**
 * Quartz Job tự động expire booking sau 2 phút và release ghế.
 *
 * <p>Job này được schedule bởi {@link com.ticketmaster.booking.application.handler.CreateBookingHandler}
 * ngay sau khi booking được tạo. Trigger time = {@code booking.expiresAt}.
 *
 * <p><b>Khi nào chạy:</b> Sau đúng 2 phút (hoặc ngay khi scheduler recover nếu bị down).
 *
 * <p><b>@PersistJobDataAfterExecution + @DisallowConcurrentExecution:</b>
 * Đảm bảo job không chạy đồng thời trên nhiều nodes (cluster-safe).
 *
 * <p><b>JobStore:</b> JDBC persistent – job survive khi service restart.
 *
 * <p><b>Logic:</b>
 * <ol>
 *   <li>Load booking từ DB</li>
 *   <li>Nếu booking vẫn PENDING_PAYMENT (chưa được confirm/cancel) → expire</li>
 *   <li>Nếu đã CONFIRMED/CANCELLED → skip (bình thường khi Quartz recover sau restart)</li>
 * </ol>
 */
@Slf4j
@Component
@PersistJobDataAfterExecution
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class SeatReleaseScheduler implements Job {

    private final BookingRepository    bookingRepository;
    private final CancelBookingHandler cancelBookingHandler;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobData  = context.getMergedJobDataMap();
        String bookingId    = jobData.getString("bookingId");

        log.info("[SCHEDULER] SeatReleaseScheduler fired for bookingId={}", bookingId);

        try {
            Booking booking = bookingRepository.findById(bookingId).orElse(null);

            if (booking == null) {
                log.warn("[SCHEDULER] Booking {} not found – skipping", bookingId);
                return;
            }

            // Chỉ expire booking đang PENDING_PAYMENT
            if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
                log.info("[SCHEDULER] Booking {} is {} – no action needed",
                        bookingId, booking.getStatus());
                return;
            }

            // Cancel với systemInitiated=true → expire + release seats + publish Kafka events
            CancelBookingCommand command = CancelBookingCommand.builder()
                    .bookingId(bookingId)
                    .userId(null)
                    .reason("Payment timeout – automatically expired after 2 minutes")
                    .systemInitiated(true)
                    .build();

            cancelBookingHandler.handle(command);

            log.info("[SCHEDULER] Booking {} expired and seats released successfully", bookingId);

        } catch (Exception e) {
            log.error("[SCHEDULER] Failed to expire booking {}: {}", bookingId, e.getMessage(), e);
            // Ném JobExecutionException để Quartz retry
            throw new JobExecutionException(
                    "Failed to expire booking " + bookingId + ": " + e.getMessage(), e, false);
        }
    }
}