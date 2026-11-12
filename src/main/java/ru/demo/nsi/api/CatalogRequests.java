package ru.demo.nsi.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.*;
import java.util.UUID;

/** DTO новых предметных API; JSON использует snake_case, как и ответы старого сервиса. */
public final class CatalogRequests {
    private CatalogRequests() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AirportCreate(@NotBlank String iataCode, @NotBlank String name,
                                @NotBlank String city, @NotBlank String country, String description) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AirportPatch(String name, String city, String country, String description, String status) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ParkingStandCreate(@NotBlank String code, String description) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ParkingStandPatch(String code, String description, String status) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AircraftTypeCreate(@NotBlank String code, @NotBlank String name,
                                     @NotBlank String category, String description) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AircraftTypePatch(String name, String category, String description, String status) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AircraftCreate(@NotBlank String tailNumber, @NotNull UUID aircraftTypeId, String description) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AircraftPatch(String tailNumber, UUID aircraftTypeId, String description, String status) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OperationTypeCreate(@NotBlank String code, @NotBlank String name, String description) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OperationTypePatch(String name, String description, String status) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OperationNormCreate(@NotNull UUID operationTypeId,
                                      @NotNull @Positive Integer durationSeconds, String description) {}
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OperationNormPatch(@Positive Integer durationSeconds, String description) {}
}
