package ru.demo.nsi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.demo.nsi.domain.Airport;

import java.util.*;

public interface AirportRepository extends JpaRepository<Airport, UUID> {
    Optional<Airport> findByIataCode(String iataCode);
}
