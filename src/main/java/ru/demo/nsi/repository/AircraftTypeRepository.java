package ru.demo.nsi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.demo.nsi.domain.AircraftType;

import java.util.*;

public interface AircraftTypeRepository extends JpaRepository<AircraftType, UUID> {
    Optional<AircraftType> findByCode(String code);
}
