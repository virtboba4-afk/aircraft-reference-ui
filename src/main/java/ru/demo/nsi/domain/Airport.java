package ru.demo.nsi.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Типизированная сущность; все FK поддерживаются ограничениями PostgreSQL.
 */
@Entity
@Table(name = "airports")
public class Airport {
    @Id
    public UUID id;
    @Column(name = "iata_code")
    public String iataCode;
    public String name;
    public String city;
    public String country;
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
