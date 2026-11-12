package ru.demo.nsi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.demo.nsi.api.ApiException;
import ru.demo.nsi.api.CatalogRequests.*;
import ru.demo.nsi.config.ActorContext;
import ru.demo.nsi.domain.*;
import ru.demo.nsi.repository.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static ru.demo.nsi.service.NsiService.obj;
import static ru.demo.nsi.service.NsiService.page;

/** Типизированные предметные данные, не требующие новой бизнес-версии при обычном PATCH.
 * Опубликованные процедуры по-прежнему версионируются отдельно. */
@Service
public class CatalogService {
    private final AirportRepository airports;
    private final ParkingStandRepository stands;
    private final AircraftTypeRepository types;
    private final AircraftRepository aircraft;
    private final OperationTypeRepository operations;
    private final OperationNormRepository norms;
    private final AuditRepository audits;
    private final ActorContext actor;
    private final ObjectMapper mapper;

    public CatalogService(AirportRepository airports, ParkingStandRepository stands,
                          AircraftTypeRepository types, AircraftRepository aircraft,
                          OperationTypeRepository operations, OperationNormRepository norms,
                          AuditRepository audits, ActorContext actor, ObjectMapper mapper) {
        this.airports=airports; this.stands=stands; this.types=types;
        this.aircraft=aircraft; this.operations=operations; this.norms=norms;
        this.audits=audits; this.actor=actor; this.mapper=mapper;
    }

    private ApiException notFound(String entity) { return new ApiException(HttpStatus.NOT_FOUND, entity+" не найден(а)"); }
    private ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
    private ApiException invalid(String message) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
    private Airport airport(UUID id) { return airports.findById(id).orElseThrow(() -> notFound("Аэропорт")); }
    private ParkingStand stand(UUID id) { return stands.findById(id).orElseThrow(() -> notFound("Стоянка")); }
    private AircraftType type(UUID id) { return types.findById(id).orElseThrow(() -> notFound("Тип ВС")); }
    private Aircraft plane(UUID id) { return aircraft.findById(id).orElseThrow(() -> notFound("Воздушное судно")); }
    private OperationType operation(UUID id) { return operations.findById(id).orElseThrow(() -> notFound("Операция")); }
    private OperationNorm norm(UUID id) { return norms.findById(id).orElseThrow(() -> notFound("Норматив")); }

    private String text(String s, String field) {
        if (s==null || s.isBlank()) throw invalid(field+" не может быть пустым");
        return s.trim();
    }
    private String upper(String s, String field) { return text(s,field).toUpperCase(Locale.ROOT); }
    private String status(String status) {
        String value=upper(status,"status");
        if (!value.equals("ACTIVE") && !value.equals("INACTIVE")) throw invalid("status должен быть ACTIVE или INACTIVE");
        return value;
    }
    private String code(String s,String field,int min,int max) {
        String value=upper(s,field);
        if (value.length()<min || value.length()>max || !value.matches("[A-Z0-9_-]+"))
            throw invalid(field+": допустимы латинские буквы, цифры, дефис и подчёркивание ("+min+"–"+max+")");
        return value;
    }
    private void change(Object... fields) {
        if (Arrays.stream(fields).allMatch(Objects::isNull)) throw invalid("PATCH должен изменять хотя бы одно поле");
    }
    private void active(String kind,String state) {
        if (!"ACTIVE".equals(state)) throw conflict(kind+" неактивен; новая привязка запрещена");
    }
    private <T> Map<String,Object> listed(List<T> items, Function<T,Map<String,Object>> format, int limit,int offset) {
        if(limit<1 || limit>200 || offset<0) throw invalid("limit должен быть 1–200, offset не может быть отрицательным");
        return page(items.stream().skip(offset).limit(limit).map(format).toList(),items.size(),limit,offset);
    }
    private boolean match(String needle,String... fields) {
        if(needle==null || needle.isBlank()) return true;
        for(String value:fields) if(value!=null && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
    private void audit(String entity,UUID id,String action,Object oldValue,Object newValue) {
        AuditLog log=new AuditLog();log.id=UUID.randomUUID();log.actorId=actor.actorId();
        log.entityType=entity;log.entityId=id;log.operation=action;
        log.oldValue=oldValue==null?null:mapper.valueToTree(oldValue);
        log.newValue=newValue==null?null:mapper.valueToTree(newValue);
        log.requestId=actor.requestId();log.createdAt=Instant.now();audits.save(log);
    }
    private Map<String,Object> airportView(Airport a) {
        return obj("id",a.id,"iata_code",a.iataCode,"name",a.name,"city",a.city,"country",a.country,
            "description",a.description,"status",a.status,"revision",a.revision);
    }
    private Map<String,Object> standView(ParkingStand s) {
        return obj("id",s.id,"airport_id",s.airportId,"airport_iata_code",airport(s.airportId).iataCode,
            "code",s.code,"description",s.description,"status",s.status,"revision",s.revision);
    }
    private Map<String,Object> typeView(AircraftType t) {
        return obj("id",t.id,"code",t.code,"name",t.name,"category",t.category,
            "description",t.description,"status",t.status,"revision",t.revision);
    }
    private Map<String,Object> aircraftView(Aircraft a) {
        AircraftType t=type(a.aircraftTypeId);
        return obj("id",a.id,"tail_number",a.tailNumber,"aircraft_type_id",t.id,"aircraft_type_code",t.code,
            "aircraft_type_name",t.name,"description",a.description,"status",a.status,"revision",a.revision);
    }
    private Map<String,Object> operationView(OperationType o) {
        return obj("id",o.id,"code",o.code,"name",o.name,"description",o.description,"status",o.status,"revision",o.revision);
    }
    private Map<String,Object> normView(OperationNorm n) {
        AircraftType t=type(n.aircraftTypeId);OperationType o=operation(n.operationTypeId);
        return obj("id",n.id,"aircraft_type_id",t.id,"aircraft_type_code",t.code,
            "operation_type_id",o.id,"operation_code",o.code,"operation_name",o.name,
            "duration_seconds",n.durationSeconds,"description",n.description,"revision",n.revision,
            "note","Редактируемое базовое значение; опубликованные procedure_stages НЕ изменяются");
    }

    // --- Аэропорты и принадлежащие им стоянки ---
    @Transactional(readOnly=true)
    public Map<String,Object> airports(String search,int limit,int offset) {
        List<Airport> all=airports.findAll().stream().filter(a->match(search,a.iataCode,a.name,a.city))
            .sorted(Comparator.comparing(a->a.iataCode)).toList();
        return listed(all,this::airportView,limit,offset);
    }
    @Transactional(readOnly=true) public Map<String,Object> airportDetails(UUID id) { return airportView(airport(id)); }
    @Transactional public Map<String,Object> createAirport(AirportCreate r) {
        String iata=upper(r.iataCode(),"iata_code");
        if(!iata.matches("[A-Z]{3}"))throw invalid("iata_code должен содержать три латинские буквы");
        if(airports.findByIataCode(iata).isPresent())throw conflict("Аэропорт с таким кодом существует");
        Airport a=new Airport();a.id=UUID.randomUUID();a.iataCode=iata;a.name=text(r.name(),"name");
        a.city=text(r.city(),"city");a.country=text(r.country(),"country");a.description=r.description();
        a.status="ACTIVE";a.createdAt=Instant.now();a.updatedAt=a.createdAt;
        airports.saveAndFlush(a);var result=airportView(a);audit("AIRPORT",a.id,"CREATE",null,result);return result;
    }
    @Transactional public Map<String,Object> patchAirport(UUID id,AirportPatch r) {
        change(r.name(),r.city(),r.country(),r.description(),r.status());
        Airport a=airport(id);var before=airportView(a);
        if(r.name()!=null)a.name=text(r.name(),"name");
        if(r.city()!=null)a.city=text(r.city(),"city");
        if(r.country()!=null)a.country=text(r.country(),"country");
        if(r.description()!=null)a.description=r.description();
        if(r.status()!=null)a.status=status(r.status());
        a.updatedAt=Instant.now();airports.saveAndFlush(a);
        var result=airportView(a);audit("AIRPORT",a.id,"UPDATE",before,result);return result;
    }
    @Transactional(readOnly=true)
    public Map<String,Object> parkingStands(UUID airportId,int limit,int offset) {
        airport(airportId);return listed(stands.findByAirportIdOrderByCode(airportId),this::standView,limit,offset);
    }
    @Transactional(readOnly=true) public Map<String,Object> parkingStand(UUID id) { return standView(stand(id)); }
    @Transactional public Map<String,Object> createStand(UUID airportId,ParkingStandCreate r) {
        active("Аэропорт",airport(airportId).status);String standCode=code(r.code(),"code",1,30);
        if(stands.findByAirportIdAndCode(airportId,standCode).isPresent())throw conflict("Такая стоянка уже есть в аэропорту");
        ParkingStand s=new ParkingStand();s.id=UUID.randomUUID();s.airportId=airportId;s.code=standCode;
        s.description=r.description();s.status="ACTIVE";s.createdAt=Instant.now();s.updatedAt=s.createdAt;
        stands.saveAndFlush(s);var result=standView(s);audit("PARKING_STAND",s.id,"CREATE",null,result);return result;
    }
    @Transactional public Map<String,Object> patchStand(UUID id,ParkingStandPatch r) {
        change(r.code(),r.description(),r.status());ParkingStand s=stand(id);var before=standView(s);
        if(r.code()!=null){
            String next=code(r.code(),"code",1,30);
            if(!next.equals(s.code) && stands.findByAirportIdAndCode(s.airportId,next).isPresent())
                throw conflict("Стоянка с таким кодом уже есть в аэропорту");
            s.code=next;
        }
        if(r.description()!=null)s.description=r.description();
        if(r.status()!=null)s.status=status(r.status());
        s.updatedAt=Instant.now();stands.saveAndFlush(s);
        var result=standView(s);audit("PARKING_STAND",s.id,"UPDATE",before,result);return result;
    }

    // --- Типы ВС и конкретные борта ---
    @Transactional(readOnly=true)
    public Map<String,Object> aircraftTypes(String search,String category,int limit,int offset) {
        List<AircraftType> all=types.findAll().stream().filter(t->match(search,t.code,t.name))
            .filter(t->category==null || t.category.equalsIgnoreCase(category))
            .sorted(Comparator.comparing(t->t.code)).toList();
        return listed(all,this::typeView,limit,offset);
    }
    @Transactional(readOnly=true) public Map<String,Object> aircraftType(UUID id) {return typeView(type(id));}
    @Transactional public Map<String,Object> createAircraftType(AircraftTypeCreate r) {
        String c=code(r.code(),"code",2,20);if(types.findByCode(c).isPresent())throw conflict("Тип ВС с таким кодом существует");
        AircraftType t=new AircraftType();t.id=UUID.randomUUID();t.code=c;t.name=text(r.name(),"name");
        t.category=upper(r.category(),"category");t.description=r.description();t.status="ACTIVE";
        t.createdAt=Instant.now();t.updatedAt=t.createdAt;
        types.saveAndFlush(t);var result=typeView(t);audit("AIRCRAFT_TYPE",t.id,"CREATE",null,result);return result;
    }
    @Transactional public Map<String,Object> patchAircraftType(UUID id,AircraftTypePatch r) {
        change(r.name(),r.category(),r.description(),r.status());AircraftType t=type(id);var before=typeView(t);
        if(r.name()!=null)t.name=text(r.name(),"name");
        if(r.category()!=null)t.category=upper(r.category(),"category");
        if(r.description()!=null)t.description=r.description();
        if(r.status()!=null)t.status=status(r.status());
        t.updatedAt=Instant.now();types.saveAndFlush(t);
        var result=typeView(t);audit("AIRCRAFT_TYPE",t.id,"UPDATE",before,result);return result;
    }
    @Transactional(readOnly=true)
    public Map<String,Object> aircraft(String tail,String typeCode,int limit,int offset) {
        List<Aircraft> all=aircraft.findAll().stream().filter(a->match(tail,a.tailNumber))
            .filter(a->typeCode==null || type(a.aircraftTypeId).code.equalsIgnoreCase(typeCode))
            .sorted(Comparator.comparing(a->a.tailNumber)).toList();
        return listed(all,this::aircraftView,limit,offset);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> aircraftByType(UUID aircraftTypeId,int limit,int offset) {
        type(aircraftTypeId);return listed(aircraft.findByAircraftTypeId(aircraftTypeId).stream()
            .sorted(Comparator.comparing(a->a.tailNumber)).toList(),this::aircraftView,limit,offset);
    }
    @Transactional(readOnly=true) public Map<String,Object> planeDetails(UUID id) {return aircraftView(plane(id));}
    @Transactional public Map<String,Object> createAircraft(AircraftCreate r) {
        String tail=code(r.tailNumber(),"tail_number",2,30);
        if(aircraft.findByTailNumber(tail).isPresent())throw conflict("Борт с таким номером существует");
        active("Тип ВС",type(r.aircraftTypeId()).status);
        Aircraft a=new Aircraft();a.id=UUID.randomUUID();a.tailNumber=tail;a.aircraftTypeId=r.aircraftTypeId();
        a.description=r.description();a.status="ACTIVE";a.createdAt=Instant.now();a.updatedAt=a.createdAt;
        aircraft.saveAndFlush(a);var result=aircraftView(a);audit("AIRCRAFT",a.id,"CREATE",null,result);return result;
    }
    @Transactional public Map<String,Object> patchAircraft(UUID id,AircraftPatch r) {
        change(r.tailNumber(),r.aircraftTypeId(),r.description(),r.status());Aircraft a=plane(id);var before=aircraftView(a);
        if(r.tailNumber()!=null){
            String tail=code(r.tailNumber(),"tail_number",2,30);
            if(!tail.equals(a.tailNumber)&&aircraft.findByTailNumber(tail).isPresent())throw conflict("Борт с таким номером существует");
            a.tailNumber=tail;
        }
        if(r.aircraftTypeId()!=null){active("Тип ВС",type(r.aircraftTypeId()).status);a.aircraftTypeId=r.aircraftTypeId();}
        if(r.description()!=null)a.description=r.description();
        if(r.status()!=null)a.status=status(r.status());
        a.updatedAt=Instant.now();aircraft.saveAndFlush(a);
        var result=aircraftView(a);audit("AIRCRAFT",a.id,"UPDATE",before,result);return result;
    }

    // --- Операции и типо-зависимые ориентиры времени ---
    @Transactional(readOnly=true)
    public Map<String,Object> operationTypes(String search,String status,int limit,int offset) {
        List<OperationType> all=operations.findAll().stream().filter(o->match(search,o.code,o.name))
            .filter(o->status==null || o.status.equalsIgnoreCase(status))
            .sorted(Comparator.comparing(o->o.code)).toList();
        return listed(all,this::operationView,limit,offset);
    }
    @Transactional(readOnly=true) public Map<String,Object> operationType(UUID id){return operationView(operation(id));}
    @Transactional public Map<String,Object> createOperationType(OperationTypeCreate r) {
        String c=code(r.code(),"code",2,30);if(operations.findByCode(c).isPresent())throw conflict("Операция с таким кодом существует");
        OperationType o=new OperationType();o.id=UUID.randomUUID();o.code=c;o.name=text(r.name(),"name");
        o.description=r.description();o.status="ACTIVE";o.createdAt=Instant.now();o.updatedAt=o.createdAt;
        operations.saveAndFlush(o);var result=operationView(o);audit("OPERATION_TYPE",o.id,"CREATE",null,result);return result;
    }
    @Transactional public Map<String,Object> patchOperationType(UUID id,OperationTypePatch r) {
        change(r.name(),r.description(),r.status());OperationType o=operation(id);var before=operationView(o);
        if(r.name()!=null)o.name=text(r.name(),"name");
        if(r.description()!=null)o.description=r.description();
        if(r.status()!=null)o.status=status(r.status());
        o.updatedAt=Instant.now();operations.saveAndFlush(o);
        var result=operationView(o);audit("OPERATION_TYPE",o.id,"UPDATE",before,result);return result;
    }
    @Transactional(readOnly=true)
    public Map<String,Object> operationNorms(UUID aircraftTypeId,int limit,int offset) {
        type(aircraftTypeId);List<OperationNorm> all=norms.findByAircraftTypeId(aircraftTypeId).stream()
            .sorted(Comparator.comparing(n->operation(n.operationTypeId).code)).toList();
        return listed(all,this::normView,limit,offset);
    }
    @Transactional(readOnly=true) public Map<String,Object> operationNorm(UUID id){return normView(norm(id));}
    @Transactional public Map<String,Object> createOperationNorm(UUID aircraftTypeId,OperationNormCreate r) {
        active("Тип ВС",type(aircraftTypeId).status);active("Операция",operation(r.operationTypeId()).status);
        if(r.durationSeconds()==null || r.durationSeconds()<1)throw invalid("duration_seconds должен быть > 0");
        if(norms.findByAircraftTypeIdAndOperationTypeId(aircraftTypeId,r.operationTypeId()).isPresent())
            throw conflict("Норматив для этой пары тип ВС + операция уже есть");
        OperationNorm n=new OperationNorm();n.id=UUID.randomUUID();n.aircraftTypeId=aircraftTypeId;
        n.operationTypeId=r.operationTypeId();n.durationSeconds=r.durationSeconds();n.description=r.description();
        n.createdAt=Instant.now();n.updatedAt=n.createdAt;
        norms.saveAndFlush(n);var result=normView(n);audit("OPERATION_NORM",n.id,"CREATE",null,result);return result;
    }
    @Transactional public Map<String,Object> patchOperationNorm(UUID id,OperationNormPatch r) {
        change(r.durationSeconds(),r.description());OperationNorm n=norm(id);var before=normView(n);
        if(r.durationSeconds()!=null){if(r.durationSeconds()<1)throw invalid("duration_seconds должен быть > 0");n.durationSeconds=r.durationSeconds();}
        if(r.description()!=null)n.description=r.description();
        n.updatedAt=Instant.now();norms.saveAndFlush(n);
        var result=normView(n);audit("OPERATION_NORM",n.id,"UPDATE",before,result);return result;
    }

    @Transactional(readOnly=true)
    public boolean activeAircraftTypeCode(String code){return types.findByCode(code.toUpperCase(Locale.ROOT)).filter(x->"ACTIVE".equals(x.status)).isPresent();}
    @Transactional(readOnly=true)
    public boolean activeOperationCode(String code){return operations.findByCode(code.toUpperCase(Locale.ROOT)).filter(x->"ACTIVE".equals(x.status)).isPresent();}
    @Transactional(readOnly=true)
    public boolean operationAllowedForAircraftType(String aircraftTypeCode,String operationCode) {
        var t=types.findByCode(aircraftTypeCode.toUpperCase(Locale.ROOT));
        var op=operations.findByCode(operationCode.toUpperCase(Locale.ROOT));
        return t.filter(x->"ACTIVE".equals(x.status)).isPresent()
            && op.filter(x->"ACTIVE".equals(x.status)).isPresent()
            && norms.findByAircraftTypeIdAndOperationTypeId(t.get().id,op.get().id).isPresent();
    }
}
