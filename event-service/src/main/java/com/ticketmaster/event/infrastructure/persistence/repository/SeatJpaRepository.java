package com.ticketmaster.event.infrastructure.persistence.repository;

import com.ticketmaster.event.domain.model.Seat;
import com.ticketmaster.event.domain.model.SeatStatus;
import com.ticketmaster.event.domain.repository.SeatRepository;
import com.ticketmaster.event.infrastructure.persistence.entity.SeatJpaEntity;
import com.ticketmaster.event.infrastructure.persistence.mapper.SeatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter implementation của {@link SeatRepository}.
 */
@Repository
@RequiredArgsConstructor
public class SeatJpaRepository implements SeatRepository {

    private final SpringDataSeatRepository springDataRepository;
    private final SeatMapper               seatMapper;

    @Override
    public Seat save(Seat seat) {
        return seatMapper.toDomain(springDataRepository.save(seatMapper.toEntity(seat)));
    }

    @Override
    public List<Seat> saveAll(List<Seat> seats) {
        List<SeatJpaEntity> entities = seats.stream().map(seatMapper::toEntity).collect(Collectors.toList());
        return springDataRepository.saveAll(entities).stream()
                .map(seatMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Seat> findById(String id) {
        return springDataRepository.findById(id).map(seatMapper::toDomain);
    }

    @Override
    public List<Seat> findByEventId(String eventId) {
        return springDataRepository.findByEventId(eventId).stream()
                .map(seatMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Seat> findByEventIdAndSectionId(String eventId, String sectionId) {
        return springDataRepository.findByEventIdAndSectionId(eventId, sectionId).stream()
                .map(seatMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countByEventIdAndStatus(String eventId, SeatStatus status) {
        return springDataRepository.countByEventIdAndStatus(eventId, status);
    }
}

interface SpringDataSeatRepository extends JpaRepository<SeatJpaEntity, String> {
    List<SeatJpaEntity> findByEventId(String eventId);
    List<SeatJpaEntity> findByEventIdAndSectionId(String eventId, String sectionId);
    long countByEventIdAndStatus(String eventId, SeatStatus status);
}