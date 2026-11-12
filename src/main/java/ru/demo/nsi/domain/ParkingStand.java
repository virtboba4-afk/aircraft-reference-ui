package ru.demo.nsi.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Типизированная сущность; все FK поддерживаются ограничениями PostgreSQL.
 */
@Entity
@Table(name = "parking_stands")
public class ParkingStand {
    @Id
    public UUID id;
    @Column(name = "airport_id")
    public UUID airportId;
    public String code;
    @Column(columnDefinition = "text")
    public String description;
    public String status;
    @Version
    public Long revision;
    @Column(name = "created_at")
    public Instant createdAt;
    @Column(name = "updated_at")
    public Instant updatedAt;
}
