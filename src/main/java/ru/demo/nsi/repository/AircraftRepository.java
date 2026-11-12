package ru.demo.nsi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.demo.nsi.domain.Aircraft;
import java.util.*;

public interface AircraftRepository extends JpaRepository<Aircraft, UUID> {
    Optional<Aircraft> findByTailNumber(String tailNumber);
    List<Aircraft> findByAircraftTypeId(UUID aircraftTypeId);
}
