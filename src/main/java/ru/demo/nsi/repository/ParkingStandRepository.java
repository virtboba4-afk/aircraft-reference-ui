package ru.demo.nsi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.demo.nsi.domain.ParkingStand;

import java.util.*;

public interface ParkingStandRepository extends JpaRepository<ParkingStand, UUID> {
    List<ParkingStand> findByAirportIdOrderByCode(UUID airportId);

    Optional<ParkingStand> findByAirportIdAndCode(UUID airportId, String code);
}
