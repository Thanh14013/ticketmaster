package com.ticketmaster.event.infrastructure.persistence.repository;

import com.ticketmaster.event.domain.model.Venue;
import com.ticketmaster.event.domain.repository.VenueRepository;
import com.ticketmaster.event.infrastructure.persistence.entity.VenueJpaEntity;
import com.ticketmaster.event.infrastructure.persistence.mapper.VenueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter implementation của {@link VenueRepository}.
 */
@Repository
@RequiredArgsConstructor
public class VenueJpaRepository implements VenueRepository {

    private final SpringDataVenueRepository springDataRepository;
    private final VenueMapper               venueMapper;

    @Override
    public Venue save(Venue venue) {
        return venueMapper.toDomain(springDataRepository.save(venueMapper.toEntity(venue)));
    }

    @Override
    public Optional<Venue> findById(String id) {
        return springDataRepository.findById(id).map(venueMapper::toDomain);
    }

    @Override
    public List<Venue> findByCity(String city) {
        return springDataRepository.findByCityIgnoreCase(city).stream()
                .map(venueMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(String id) {
        return springDataRepository.existsById(id);
    }
}

interface SpringDataVenueRepository extends JpaRepository<VenueJpaEntity, String> {
    List<VenueJpaEntity> findByCityIgnoreCase(String city);
}