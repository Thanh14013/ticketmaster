package com.ticketmaster.event.domain.repository;

import com.ticketmaster.event.domain.model.Venue;

import java.util.List;
import java.util.Optional;

/**
 * Domain Repository interface cho {@link Venue} aggregate.
 */
public interface VenueRepository {

    Venue save(Venue venue);

    Optional<Venue> findById(String id);

    List<Venue> findByCity(String city);

    boolean existsById(String id);
}