package ru.demo.nsi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import ru.demo.nsi.config.ActorContext;
import ru.demo.nsi.service.CatalogService;
import ru.demo.nsi.service.NsiService;

/** Only reads required by the checklist module and the demo UI. */
@RestController
@RequestMapping("/api/v1/references")
@Tag(name = "1. Чтение авиационных справочников", description = "Опубликованные данные и типизированный каталог")
public class ExternalController {
    private final NsiService nsi;
    private final CatalogService catalog;
    private final ActorContext actor;

    public ExternalController(NsiService nsi, CatalogService catalog, ActorContext actor) {
        this.nsi = nsi;
        this.catalog = catalog;
        this.actor = actor;
    }

    @Operation(summary = "Список типов воздушных судов")
    @GetMapping("/aircraft-types")
    public Object aircraftTypes(@RequestParam(required = false) String search,
                                @RequestParam(required = false) String category,
                                @RequestParam(defaultValue = "50") int limit,
                                @RequestParam(defaultValue = "0") int offset) {
        actor.read();
        return catalog.aircraftTypes(search, category, limit, offset);
    }

    @Operation(summary = "Список бортов (можно отфильтровать по типу)")
    @GetMapping("/aircraft")
    public Object aircraft(@RequestParam(name = "aircraft_type_code", required = false) String type,
                           @RequestParam(name = "tail_number", required = false) String tail,
                           @RequestParam(defaultValue = "50") int limit,
                           @RequestParam(defaultValue = "0") int offset) {
        actor.read();
        return catalog.aircraft(tail, type, limit, offset);
    }

    @Operation(summary = "Список видов операций")
    @GetMapping("/operation-types")
    public Object operationTypes(@RequestParam(required = false) String search,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(defaultValue = "50") int limit,
                                 @RequestParam(defaultValue = "0") int offset) {
        actor.read();
        return catalog.operationTypes(search, status, limit, offset);
    }

    @Operation(summary = "Список опубликованных шаблонов процедур")
    @GetMapping("/procedure-templates")
    public Object templates(@RequestParam(name = "aircraft_type_code", required = false) String type,
                            @RequestParam(name = "procedure_code", required = false) String procedure,
                            @RequestParam(defaultValue = "50") int limit,
                            @RequestParam(defaultValue = "0") int offset) {
        actor.read();
        return nsi.publicList("PROCEDURE_TEMPLATE", procedure, "aircraft_type_code", type, limit, offset);
    }

    @Operation(summary = "Актуальная утверждённая процедура со всеми этапами",
               description = "Не возвращает черновые или архивные версии; подходит для формирования нового чек-листа")
    @GetMapping("/procedure-templates/resolve")
    public Object resolve(@RequestParam(name = "aircraft_type_code") String aircraft,
                          @RequestParam(name = "procedure_code") String procedure) {
        actor.read();
        return nsi.resolve(aircraft, procedure);
    }
}
