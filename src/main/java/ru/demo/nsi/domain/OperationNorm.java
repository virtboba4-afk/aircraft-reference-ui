package ru.demo.nsi.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Типизированная сущность; все FK поддерживаются ограничениями PostgreSQL.
 */
@Entity
@Table(name = "aircraft_type_operation_norms")
public class OperationNorm {
    @Id
    public UUID id;
    @Column(name = "aircraft_type_id")
    public UUID aircraftTypeId;
    @Column(name = "operation_type_id")
    public UUID operationTypeId;
    @Column(name = "duration_seconds")
    public Integer durationSeconds;
    @Column(columnDefinition = "text")
    public String description;
    @Version
    public Long revision;
    @Column(name = "created_at")
    public Instant createdAt;
    @Column(name = "updated_at")
    public Instant updatedAt;
}
