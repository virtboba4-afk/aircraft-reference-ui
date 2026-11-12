package ru.demo.nsi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.demo.nsi.domain.OperationType;

import java.util.*;

public interface OperationTypeRepository extends JpaRepository<OperationType, UUID> {
    Optional<OperationType> findByCode(String code);
}
