package ru.demo.nsi.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Типизированная сущность; все FK поддерживаются ограничениями PostgreSQL. */
@Entity @Table(name="aircraft")
public class Aircraft {
    @Id
    public UUID id;
    @Column(name="tail_number")
    public String tailNumber;
    @Column(name="aircraft_type_id")
    public UUID aircraftTypeId;
    @Column(columnDefinition="text")
    public String description;
    public String status;
    @Version
    public Long revision;
    @Column(name="created_at")
    public Instant createdAt;
    @Column(name="updated_at")
    public Instant updatedAt;
}
