package ru.demo.nsi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.demo.nsi.domain.OperationNorm;
import java.util.*;

public interface OperationNormRepository extends JpaRepository<OperationNorm, UUID> {
    List<OperationNorm> findByAircraftTypeId(UUID aircraftTypeId);
    Optional<OperationNorm> findByAircraftTypeIdAndOperationTypeId(UUID aircraftTypeId,UUID operationTypeId);
}
