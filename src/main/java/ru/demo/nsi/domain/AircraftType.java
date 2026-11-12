package ru.demo.nsi.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Типизированная сущность; все FK поддерживаются ограничениями PostgreSQL. */
@Entity @Table(name="aircraft_types")
public class AircraftType {
    @Id
    public UUID id;
    public String code;
    public String name;
    public String category;
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
