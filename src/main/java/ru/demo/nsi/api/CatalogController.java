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

/** Предметные REST-ручки. GET типов ВС / бортов / операций размещены в ExternalController. */
@RestController
@RequestMapping("/api/v1/references")
@Tag(name="3. Предметные CRUD и связи",description="Аэропорты → стоянки; типы ВС → борта; типы ВС ↔ операции. PATCH без новой версии.")
public class CatalogController {
    private final CatalogService catalog;
    private final ActorContext actor;
    private final IdempotencyService idem;
    public CatalogController(CatalogService catalog,ActorContext actor,IdempotencyService idem){
        this.catalog=catalog;this.actor=actor;this.idem=idem;
    }

    @Operation(summary="C-01: Список аэропортов")
    @GetMapping("/airports")
    public Object airports(@RequestParam(required=false)String search,
                           @RequestParam(defaultValue="50")int limit,@RequestParam(defaultValue="0")int offset){
        actor.read();return catalog.airports(search,limit,offset);
    }
    @Operation(summary="C-02: Карточка аэропорта")
    @GetMapping("/airports/{id}")
    public Object airport(@PathVariable UUID id){actor.read();return catalog.airportDetails(id);}
    @Operation(summary="C-03: Создать аэропорт (SUPERVISOR)")
    @PostMapping("/airports")
    public ResponseEntity<Object> createAirport(@Valid @RequestBody AirportCreate req){
        actor.supervisor();return idem.run(req,201,()->catalog.createAirport(req));
    }
    @Operation(summary="C-04: Изменить только переданные поля аэропорта")
    @PatchMapping("/airports/{id}")
    public ResponseEntity<Object> patchAirport(@PathVariable UUID id,@RequestBody AirportPatch req){
        actor.editor();return idem.run(req,200,()->catalog.patchAirport(id,req));
    }
    @Operation(summary="C-05: Стоянки конкретного аэропорта")
    @GetMapping("/airports/{airportId}/parking-stands")
    public Object stands(@PathVariable UUID airportId,@RequestParam(defaultValue="50")int limit,
                         @RequestParam(defaultValue="0")int offset){
        actor.read();return catalog.parkingStands(airportId,limit,offset);
    }
    @Operation(summary="C-06: Создать стоянку, привязанную к аэропорту")
    @PostMapping("/airports/{airportId}/parking-stands")
    public ResponseEntity<Object> createStand(@PathVariable UUID airportId,@Valid @RequestBody ParkingStandCreate req){
        actor.editor();return idem.run(req,201,()->catalog.createStand(airportId,req));
    }
    @Operation(summary="C-07: Карточка стоянки")
    @GetMapping("/parking-stands/{id}")
    public Object stand(@PathVariable UUID id){actor.read();return catalog.parkingStand(id);}
    @Operation(summary="C-08: Частично изменить стоянку без пересоздания")
    @PatchMapping("/parking-stands/{id}")
    public ResponseEntity<Object> patchStand(@PathVariable UUID id,@RequestBody ParkingStandPatch req){
        actor.editor();return idem.run(req,200,()->catalog.patchStand(id,req));
    }

    @Operation(summary="C-09: Создать тип ВС (SUPERVISOR)")
    @PostMapping("/aircraft-types")
    public ResponseEntity<Object> createType(@Valid @RequestBody AircraftTypeCreate req){
        actor.supervisor();return idem.run(req,201,()->catalog.createAircraftType(req));
    }
    @Operation(summary="C-10: Изменить только название, категорию, описание или статус типа ВС")
    @PatchMapping("/aircraft-types/{id}")
    public ResponseEntity<Object> patchType(@PathVariable UUID id,@RequestBody AircraftTypePatch req){
        actor.editor();return idem.run(req,200,()->catalog.patchAircraftType(id,req));
    }
    @Operation(summary="C-11: Самолёты указанного типа ВС")
    @GetMapping("/aircraft-types/{id}/aircraft")
    public Object aircraftOfType(@PathVariable UUID id,@RequestParam(defaultValue="50")int limit,
                                 @RequestParam(defaultValue="0")int offset){
        actor.read();return catalog.aircraftByType(id,limit,offset);
    }
    @Operation(summary="C-12: Создать конкретный самолёт со ссылкой на тип ВС")
    @PostMapping("/aircraft")
    public ResponseEntity<Object> createAircraft(@Valid @RequestBody AircraftCreate req){
        actor.editor();return idem.run(req,201,()->catalog.createAircraft(req));
    }
    @Operation(summary="C-13: Частично изменить самолёт, в том числе FK на тип ВС")
    @PatchMapping("/aircraft/{id}")
    public ResponseEntity<Object> patchAircraft(@PathVariable UUID id,@RequestBody AircraftPatch req){
        actor.editor();return idem.run(req,200,()->catalog.patchAircraft(id,req));
    }

    @Operation(summary="C-14: Создать тип операции (SUPERVISOR)")
    @PostMapping("/operation-types")
    public ResponseEntity<Object> createOperation(@Valid @RequestBody OperationTypeCreate req){
        actor.supervisor();return idem.run(req,201,()->catalog.createOperationType(req));
    }
    @Operation(summary="C-15: Изменить только переданные поля типа операции")
    @PatchMapping("/operation-types/{id}")
    public ResponseEntity<Object> patchOperation(@PathVariable UUID id,@RequestBody OperationTypePatch req){
        actor.editor();return idem.run(req,200,()->catalog.patchOperationType(id,req));
    }
    @Operation(summary="C-16: Временные нормативы для одного типа ВС",description="Моковые ориентиры, НЕ заменяют утверждённые procedure_stages")
    @GetMapping("/aircraft-types/{id}/operation-norms")
    public Object operationNorms(@PathVariable UUID id,@RequestParam(defaultValue="50")int limit,
                                 @RequestParam(defaultValue="0")int offset){
        actor.read();return catalog.operationNorms(id,limit,offset);
    }
    @Operation(summary="C-17: Привязать тип операции и базовую длительность к типу ВС")
    @PostMapping("/aircraft-types/{id}/operation-norms")
    public ResponseEntity<Object> createNorm(@PathVariable UUID id,@Valid @RequestBody OperationNormCreate req){
        actor.editor();return idem.run(req,201,()->catalog.createOperationNorm(id,req));
    }
    @Operation(summary="C-18: Получить базовый норматив операции")
    @GetMapping("/operation-norms/{id}")
    public Object norm(@PathVariable UUID id){actor.read();return catalog.operationNorm(id);}
    @Operation(summary="C-19: Изменить только время/описание базового норматива без новой версии",
        description="Аудит обновится, но ранее опубликованный шаблон и время его этапов останутся неизменными")
    @PatchMapping("/operation-norms/{id}")
    public ResponseEntity<Object> patchNorm(@PathVariable UUID id,@RequestBody OperationNormPatch req){
        actor.editor();return idem.run(req,200,()->catalog.patchOperationNorm(id,req));
    }
}
