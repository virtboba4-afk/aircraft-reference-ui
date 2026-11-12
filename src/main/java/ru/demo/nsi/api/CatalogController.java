package ru.demo.nsi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.demo.nsi.api.CatalogRequests.*;
import ru.demo.nsi.config.ActorContext;
import ru.demo.nsi.service.CatalogService;
import ru.demo.nsi.service.IdempotencyService;

import java.util.UUID;

/**
 * Dedicated CRUD for the relationships discussed at the project review.
 */
@RestController
@RequestMapping("/api/v1/references")
@Tag(name = "2. Авиационные данные — создание и PATCH", description = "Аэропорт → стоянки; тип ВС → борта и нормы")
public class CatalogController {
    private final CatalogService catalog;
    private final ActorContext actor;
    private final IdempotencyService idem;

    public CatalogController(CatalogService catalog, ActorContext actor, IdempotencyService idem) {
        this.catalog = catalog;
        this.actor = actor;
        this.idem = idem;
    }

    @Operation(summary = "Список аэропортов")
    @GetMapping("/airports")
    public Object airports(@RequestParam(required = false) String search,
                           @RequestParam(defaultValue = "50") int limit,
                           @RequestParam(defaultValue = "0") int offset) {
        actor.read();
        return catalog.airports(search, limit, offset);
    }

    @Operation(summary = "Создать аэропорт")
    @PostMapping("/airports")
    public ResponseEntity<Object> createAirport(@Valid @RequestBody AirportCreate req) {
        actor.supervisor();
        return idem.run(req, 201, () -> catalog.createAirport(req));
    }

    @Operation(summary = "Частично обновить аэропорт")
    @PatchMapping("/airports/{id}")
    public ResponseEntity<Object> patchAirport(@PathVariable UUID id, @RequestBody AirportPatch req) {
        actor.editor();
        return idem.run(req, 200, () -> catalog.patchAirport(id, req));
    }

    @Operation(summary = "Стоянки конкретного аэропорта")
    @GetMapping("/airports/{airportId}/parking-stands")
    public Object stands(@PathVariable UUID airportId,
                         @RequestParam(defaultValue = "50") int limit,
                         @RequestParam(defaultValue = "0") int offset) {
        actor.read();
        return catalog.parkingStands(airportId, limit, offset);
    }

    @Operation(summary = "Добавить стоянку в аэропорт")
    @PostMapping("/airports/{airportId}/parking-stands")
    public ResponseEntity<Object> createStand(@PathVariable UUID airportId,
                                              @Valid @RequestBody ParkingStandCreate req) {
        actor.editor();
        return idem.run(req, 201, () -> catalog.createStand(airportId, req));
    }

    @Operation(summary = "Частично обновить стоянку")
    @PatchMapping("/parking-stands/{id}")
    public ResponseEntity<Object> patchStand(@PathVariable UUID id, @RequestBody ParkingStandPatch req) {
        actor.editor();
        return idem.run(req, 200, () -> catalog.patchStand(id, req));
    }

    @Operation(summary = "Создать тип воздушного судна")
    @PostMapping("/aircraft-types")
    public ResponseEntity<Object> createType(@Valid @RequestBody AircraftTypeCreate req) {
        actor.supervisor();
        return idem.run(req, 201, () -> catalog.createAircraftType(req));
    }

    @Operation(summary = "Частично обновить тип ВС")
    @PatchMapping("/aircraft-types/{id}")
    public ResponseEntity<Object> patchType(@PathVariable UUID id, @RequestBody AircraftTypePatch req) {
        actor.editor();
        return idem.run(req, 200, () -> catalog.patchAircraftType(id, req));
    }

    @Operation(summary = "Создать борт, связанный с типом ВС")
    @PostMapping("/aircraft")
    public ResponseEntity<Object> createAircraft(@Valid @RequestBody AircraftCreate req) {
        actor.editor();
        return idem.run(req, 201, () -> catalog.createAircraft(req));
    }

    @Operation(summary = "Частично обновить борт")
    @PatchMapping("/aircraft/{id}")
    public ResponseEntity<Object> patchAircraft(@PathVariable UUID id, @RequestBody AircraftPatch req) {
        actor.editor();
        return idem.run(req, 200, () -> catalog.patchAircraft(id, req));
    }

    @Operation(summary = "Создать вид операции")
    @PostMapping("/operation-types")
    public ResponseEntity<Object> createOperation(@Valid @RequestBody OperationTypeCreate req) {
        actor.supervisor();
        return idem.run(req, 201, () -> catalog.createOperationType(req));
    }

    @Operation(summary = "Частично обновить вид операции")
    @PatchMapping("/operation-types/{id}")
    public ResponseEntity<Object> patchOperation(@PathVariable UUID id, @RequestBody OperationTypePatch req) {
        actor.editor();
        return idem.run(req, 200, () -> catalog.patchOperationType(id, req));
    }

    @Operation(summary = "Базовые нормативы операций для конкретного типа ВС")
    @GetMapping("/aircraft-types/{id}/operation-norms")
    public Object operationNorms(@PathVariable UUID id,
                                 @RequestParam(defaultValue = "50") int limit,
                                 @RequestParam(defaultValue = "0") int offset) {
        actor.read();
        return catalog.operationNorms(id, limit, offset);
    }

    @Operation(summary = "Привязать вид операции и базовое время к типу ВС")
    @PostMapping("/aircraft-types/{id}/operation-norms")
    public ResponseEntity<Object> createNorm(@PathVariable UUID id,
                                             @Valid @RequestBody OperationNormCreate req) {
        actor.editor();
        return idem.run(req, 201, () -> catalog.createOperationNorm(id, req));
    }

    @Operation(summary = "Частично обновить базовый норматив без новой версии",
            description = "Опубликованные этапы процедур сохраняют свои прежние нормативы")
    @PatchMapping("/operation-norms/{id}")
    public ResponseEntity<Object> patchNorm(@PathVariable UUID id, @RequestBody OperationNormPatch req) {
        actor.editor();
        return idem.run(req, 200, () -> catalog.patchOperationNorm(id, req));
    }
}
