package com.ticketmaster.event.interfaces.rest;

import com.ticketmaster.common.dto.ApiResponse;
import com.ticketmaster.common.exception.ResourceNotFoundException;
import com.ticketmaster.common.util.IdGenerator;
import com.ticketmaster.event.domain.model.Venue;
import com.ticketmaster.event.domain.repository.VenueRepository;
import com.ticketmaster.event.infrastructure.persistence.mapper.VenueMapper;
import com.ticketmaster.event.interfaces.dto.VenueResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller cho Venue management.
 * Base path: {@code /api/v1/venues}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
@Tag(name = "Venues", description = "Quản lý địa điểm tổ chức sự kiện")
public class VenueController {

    private final VenueRepository venueRepository;
    private final VenueMapper     venueMapper;

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết venue theo ID")
    public ResponseEntity<ApiResponse<VenueResponse>> getVenue(@PathVariable String id) {
        VenueResponse response = venueRepository.findById(id)
                .map(venueMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Venue", "id", id));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách venues, có thể lọc theo city")
    public ResponseEntity<ApiResponse<List<VenueResponse>>> getVenues(
            @RequestParam(required = false) String city) {

        List<VenueResponse> responses;
        if (city != null && !city.isBlank()) {
            responses = venueRepository.findByCity(city).stream()
                    .map(venueMapper::toResponse)
                    .collect(Collectors.toList());
        } else {
            // Return empty list for now – full scan avoided in production
            responses = List.of();
        }
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }
}