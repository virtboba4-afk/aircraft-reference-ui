package ru.demo.nsi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import ru.demo.nsi.config.ActorContext;
import ru.demo.nsi.service.NsiService;
import ru.demo.nsi.service.CatalogService;
import java.util.*;

@RestController @RequestMapping("/api/v1/references")
@Tag(name="1. Внешнее API (сервис — сервис)",description="Типизированные справочники и утверждённые версии процедур для команды чек-листа")
public class ExternalController {
    private final NsiService nsi;private final CatalogService catalog;private final ActorContext actor;
    public ExternalController(NsiService nsi,CatalogService catalog,ActorContext actor){this.nsi=nsi;this.catalog=catalog;this.actor=actor;}
    @Operation(summary="E-01: Типы воздушных судов")
    @GetMapping("/aircraft-types")
    public Object aircraftTypes(@RequestParam(required=false)String search,@RequestParam(required=false)String category,
            @RequestParam(defaultValue="50")int limit,@RequestParam(defaultValue="0")int offset){
        actor.read();return catalog.aircraftTypes(search,category,limit,offset);
    }
    @Operation(summary="E-02: Тип ВС по логическому ID")
    @GetMapping("/aircraft-types/{id}")
    public Object aircraftType(@PathVariable UUID id){actor.read();return catalog.aircraftType(id);}
    @Operation(summary="E-03: Реестр бортов")
    @GetMapping("/aircraft")
    public Object aircraft(@RequestParam(name="aircraft_type_code",required=false)String type,
            @RequestParam(name="tail_number",required=false)String tail,
            @RequestParam(defaultValue="50")int limit,@RequestParam(defaultValue="0")int offset){
        actor.read();return catalog.aircraft(tail,type,limit,offset);
    }
    @Operation(summary="E-04: Карточка борта")
    @GetMapping("/aircraft/{id}")
    public Object aircraftOne(@PathVariable UUID id){actor.read();return catalog.planeDetails(id);}
    @Operation(summary="E-05: Типовые операции")
    @GetMapping("/operation-types")
    public Object operations(@RequestParam(required=false)String search,@RequestParam(required=false)String status,
            @RequestParam(defaultValue="50")int limit,@RequestParam(defaultValue="0")int offset){
        actor.read();return catalog.operationTypes(search,status,limit,offset);
    }
    @Operation(summary="E-06: Конкретная типовая операция")
    @GetMapping("/operation-types/{id}")
    public Object operationOne(@PathVariable UUID id){actor.read();return catalog.operationType(id);}
    @Operation(summary="E-07: Доступные шаблоны процедур")
    @GetMapping("/procedure-templates")
    public Object templates(@RequestParam(name="aircraft_type_code",required=false)String type,
            @RequestParam(name="procedure_code",required=false)String proc,
            @RequestParam(defaultValue="50")int limit,@RequestParam(defaultValue="0")int offset){
        actor.read();return nsi.publicList("PROCEDURE_TEMPLATE",proc,"aircraft_type_code",type,limit,offset);
    }
    @Operation(summary="E-09: Подобрать единственный утверждённый шаблон с этапами",
        description="Используйте перед созданием нового чек-листа. Никогда не выбирает DRAFT/REVIEW/ARCHIVED.")
    @GetMapping("/procedure-templates/resolve")
    public Object resolve(@RequestParam(name="aircraft_type_code")String aircraft,
            @RequestParam(name="procedure_code")String procedure){actor.read();return nsi.resolve(aircraft,procedure);}
    @Operation(summary="E-08: Полная карточка опубликованного шаблона")
    @GetMapping("/procedure-templates/{id}")
    public Object templateOne(@PathVariable UUID id){actor.read();return nsi.publicItem("PROCEDURE_TEMPLATE",id);}
    @Operation(summary="E-10: Неизменяемый снимок точной версии",
        description="ARCHIVED доступен в демо только роли SUPERVISOR. Снимок не означает разрешение создать новый чек-лист.")
    @GetMapping("/snapshots/{referenceVersionId}")
    public Object snapshot(@PathVariable UUID referenceVersionId){actor.read();return nsi.snapshot(referenceVersionId);}
    @Operation(summary="E-11: Манифест опубликованных версий для офлайн-кэша")
    @GetMapping("/sync/manifest")
    public Object manifest(){actor.read();return nsi.manifest();}
    @Operation(summary="E-12: Можно ли использовать версию для НОВОГО чек-листа")
    @GetMapping("/versions/{versionId}/status")
    public Object status(@PathVariable UUID versionId){actor.read();return nsi.status(versionId);}
}
