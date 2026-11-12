package ru.demo.nsi.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import ru.demo.nsi.integration.logging.ReferenceAuditEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.demo.nsi.api.*;
import ru.demo.nsi.api.Requests.*;
import ru.demo.nsi.config.ActorContext;
import ru.demo.nsi.domain.*;
import ru.demo.nsi.repository.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NsiService {
    private final DirectoryRepository directories;
    private final VersionRepository versions;
    private final RecordRepository records;
    private final StageRepository stages;
    private final AuditRepository audits;
    private final ObjectMapper mapper;
    private final ActorContext actor;
    private final CatalogService catalog;
    private final ApplicationEventPublisher events;

    public NsiService(DirectoryRepository directories, VersionRepository versions, RecordRepository records,
                      StageRepository stages, AuditRepository audits, ObjectMapper mapper, ActorContext actor,
                      CatalogService catalog, ApplicationEventPublisher events) {
        this.directories = directories;
        this.versions = versions;
        this.records = records;
        this.stages = stages;
        this.audits = audits;
        this.mapper = mapper;
        this.actor = actor;
        this.catalog = catalog;
        this.events = events;
    }

    private ApiException bad(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    private ApiException missing(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    private DirectoryEntity directory(UUID id) {
        return directories.findById(id).orElseThrow(() -> missing("Справочник не найден"));
    }

    private ReferenceVersion version(UUID dir, UUID id) {
        return versions.findByIdAndDirectoryId(id, dir).orElseThrow(() -> missing("Версия не найдена в справочнике"));
    }

    private void ensureVersionedKind(UUID dir) {
        String kind = directory(dir).kind;
        if (Set.of("AIRCRAFT_TYPE", "AIRCRAFT", "OPERATION_TYPE").contains(kind))
            throw conflict("Старый справочник " + kind + " доступен только для истории. Изменяйте сущности через предметный API (PATCH/POST).");
    }

    private ReferenceVersion editable(UUID dir, UUID id) {
        ensureVersionedKind(dir);
        ReferenceVersion v = version(dir, id);
        if (v.status != ReferenceVersion.Status.DRAFT) throw conflict("Изменять записи разрешено только в DRAFT");
        return v;
    }

    private DirectoryRecord record(UUID v, UUID id) {
        return records.findByIdAndVersionId(id, v).orElseThrow(() -> missing("Запись не найдена в указанной версии"));
    }

    public static Map<String, Object> obj(Object... args) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) m.put((String) args[i], args[i + 1]);
        return m;
    }

    public static Map<String, Object> page(List<?> items, int total, int limit, int offset) {
        return obj("items", items, "total", total, "limit", limit, "offset", offset);
    }

    private <T> List<T> slice(List<T> items, int limit, int offset) {
        return items.stream().skip(Math.max(0, offset)).limit(Math.min(200, Math.max(1, limit))).toList();
    }

    private String normalize(String raw) {
        return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> directoryView(DirectoryEntity d) {
        return obj("id", d.id, "code", d.code, "kind", d.kind, "name", d.name, "description", d.description,
                "revision", d.revision, "legacy_read_only", Set.of("AIRCRAFT_TYPE", "AIRCRAFT", "OPERATION_TYPE").contains(d.kind), "active_version_id", versions.findByDirectoryIdAndStatus(d.id, ReferenceVersion.Status.APPROVED)
                        .map(v -> v.id).orElse(null), "updated_at", d.updatedAt);
    }

    private Map<String, Object> versionView(ReferenceVersion v) {
        return obj("id", v.id, "directory_id", v.directoryId, "version_number", v.versionNumber, "status", v.status,
                "base_version_id", v.baseVersionId, "change_reason", v.changeReason, "created_by", v.createdBy,
                "approved_by", v.approvedBy, "created_at", v.createdAt, "published_at", v.publishedAt);
    }

    private Map<String, Object> stageView(ProcedureStage s) {
        return obj("id", s.id, "logical_id", s.logicalId, "sequence_number", s.sequenceNumber,
                "operation_code", s.operationCode, "description", s.description,
                "expected_duration_seconds", s.expectedDurationSeconds, "required_role", s.requiredRole, "mandatory", s.mandatory);
    }

    private Map<String, Object> recordView(DirectoryRecord r) {
        return obj("id", r.id, "logical_id", r.logicalId, "reference_version_id", r.versionId, "code", r.code,
                "name", r.name, "description", r.description, "data", r.data,
                "stages", stages.findByRecordIdOrderBySequenceNumber(r.id).stream().map(this::stageView).toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> directories(String search, String kind, int limit, int offset) {
        List<DirectoryEntity> all = directories.findAll().stream()
                .filter(d -> !"TECHNICIAN".equals(actor.role()) ||
                        versions.findByDirectoryIdAndStatus(d.id, ReferenceVersion.Status.APPROVED).isPresent())
                .filter(d -> (kind == null || d.kind.equalsIgnoreCase(kind)) &&
                        (search == null || d.code.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)) ||
                                d.name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))).sorted(Comparator.comparing(d -> d.code)).toList();
        return page(slice(all, limit, offset).stream().map(this::directoryView).toList(), all.size(), limit, offset);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> directoryDetails(UUID id) {
        DirectoryEntity d = directory(id);
        if ("TECHNICIAN".equals(actor.role()) &&
                versions.findByDirectoryIdAndStatus(d.id, ReferenceVersion.Status.APPROVED).isEmpty())
            throw missing("Неопубликованный справочник недоступен");
        return directoryView(d);
    }

    @Transactional
    public Map<String, Object> createDirectory(DirectoryCreate req) {
        String code = normalize(req.code()), kind = normalize(req.kind());
        if (!Set.of("PROCEDURE_TEMPLATE", "GENERIC").contains(kind))
            throw bad("Для типов ВС, бортов и операций используйте отдельные предметные API. Допустимы kind=PROCEDURE_TEMPLATE или GENERIC");
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{1,79}"))
            throw bad("Код: латинские буквы, цифры и подчёркивание");
        if (directories.findByCode(code).isPresent()) throw conflict("Справочник с таким кодом уже существует");
        DirectoryEntity d = new DirectoryEntity();
        d.id = UUID.randomUUID();
        d.code = code;
        d.kind = kind;
        d.name = req.name().trim();
        d.description = req.description();
        d.createdAt = Instant.now();
        d.updatedAt = d.createdAt;
        directories.save(d);
        ReferenceVersion v = newDraft(d.id, 1, null, "Первоначальный черновик");
        audit("DIRECTORY", d.id, v.id, "CREATE", null, directoryView(d), null);
        return obj("directory_id", d.id, "draft_version_id", v.id, "status", v.status);
    }

    @Transactional
    public Map<String, Object> patchDirectory(UUID id, DirectoryPatch req, String ifMatch) {
        ensureVersionedKind(id);
        DirectoryEntity d = directory(id);
        if (ifMatch == null || !ifMatch.equals("\"" + d.revision + "\""))
            throw new ApiException(HttpStatus.PRECONDITION_FAILED, "If-Match должен совпадать с ETag справочника");
        Map<String, Object> old = directoryView(d);
        if (req.name() != null) {
            if (req.name().isBlank()) throw bad("Название пустое");
            d.name = req.name().trim();
        }
        if (req.description() != null) d.description = req.description();
        d.updatedAt = Instant.now();
        directories.saveAndFlush(d);
        Map<String, Object> response = directoryView(d);
        audit("DIRECTORY", d.id, null, "UPDATE", old, response, null);
        return response;
    }

    private ReferenceVersion newDraft(UUID dir, int number, UUID base, String reason) {
        ReferenceVersion v = new ReferenceVersion();
        v.id = UUID.randomUUID();
        v.directoryId = dir;
        v.versionNumber = number;
        v.status = ReferenceVersion.Status.DRAFT;
        v.baseVersionId = base;
        v.changeReason = reason;
        v.createdBy = actor.actorId();
        v.createdAt = Instant.now();
        v.updatedAt = v.createdAt;
        return versions.save(v);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> versions(UUID dir, int limit, int offset, String status) {
        directory(dir);
        var all = versions.findByDirectoryIdOrderByVersionNumberDesc(dir).stream()
                .filter(v -> status == null || v.status.name().equalsIgnoreCase(status)).toList();
        return page(slice(all, limit, offset).stream().map(this::versionView).toList(), all.size(), limit, offset);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> versionDetails(UUID dir, UUID id) {
        directory(dir);
        return versionView(version(dir, id));
    }

    @Transactional
    public Map<String, Object> createVersion(UUID dir, VersionCreate req) {
        ensureVersionedKind(dir);
        directory(dir);
        List<ReferenceVersion> all = versions.findByDirectoryIdOrderByVersionNumberDesc(dir);
        if (all.stream().anyMatch(v -> v.status == ReferenceVersion.Status.DRAFT || v.status == ReferenceVersion.Status.REVIEW))
            throw conflict("Закройте текущий DRAFT/REVIEW перед созданием следующего");
        ReferenceVersion active = versions.findByDirectoryIdAndStatus(dir, ReferenceVersion.Status.APPROVED).orElse(null);
        ReferenceVersion from = req != null && req.sourceVersionId() != null ? version(dir, req.sourceVersionId()) : active;
        if (from != null && from.status == ReferenceVersion.Status.DRAFT)
            throw conflict("Источник копирования не должен быть DRAFT");
        int number = all.stream().mapToInt(v -> v.versionNumber).max().orElse(0) + 1;
        ReferenceVersion to = newDraft(dir, number, active == null ? null : active.id, req == null ? null : req.changeReason());
        if (from != null) {
            for (DirectoryRecord prev : records.findByVersionIdOrderByCode(from.id)) {
                DirectoryRecord copy = new DirectoryRecord();
                copy.id = UUID.randomUUID();
                copy.logicalId = prev.logicalId;
                copy.versionId = to.id;
                copy.code = prev.code;
                copy.name = prev.name;
                copy.description = prev.description;
                copy.data = prev.data.deepCopy();
                copy.createdAt = Instant.now();
                records.save(copy);
                for (ProcedureStage stage : stages.findByRecordIdOrderBySequenceNumber(prev.id)) {
                    ProcedureStage s = new ProcedureStage();
                    s.id = UUID.randomUUID();
                    s.logicalId = stage.logicalId;
                    s.recordId = copy.id;
                    s.sequenceNumber = stage.sequenceNumber;
                    s.operationCode = stage.operationCode;
                    s.description = stage.description;
                    s.expectedDurationSeconds = stage.expectedDurationSeconds;
                    s.requiredRole = stage.requiredRole;
                    s.mandatory = stage.mandatory;
                    stages.save(s);
                }
            }
        }
        audit("REFERENCE_VERSION", to.id, to.id, "CREATE", null, versionView(to), null);
        return versionView(to);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> records(UUID dir, UUID ver, String search, int limit, int offset) {
        version(dir, ver);
        var all = records.findByVersionIdOrderByCode(ver).stream().filter(r -> search == null || r.code.toLowerCase(Locale.ROOT)
                .contains(search.toLowerCase(Locale.ROOT)) || r.name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))).toList();
        return page(slice(all, limit, offset).stream().map(this::recordView).toList(), all.size(), limit, offset);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> record(UUID dir, UUID ver, UUID id) {
        version(dir, ver);
        return recordView(record(ver, id));
    }

    private void validate(DirectoryEntity d, String code, String name, JsonNode data) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) throw bad("Код и название обязательны");
        if (data == null || !data.isObject()) throw bad("Поле data должно быть JSON-объектом");
        switch (d.kind) {
            case "AIRCRAFT_TYPE" -> required(data, "category");
            case "AIRCRAFT" -> required(data, "aircraft_type_code");
            case "OPERATION_TYPE" -> {
                JsonNode x = data.get("default_duration_seconds");
                if (x == null || !x.isIntegralNumber() || x.asInt() <= 0)
                    throw bad("default_duration_seconds: целое положительное число");
            }
            case "PROCEDURE_TEMPLATE" -> {
                required(data, "aircraft_type_code");
                required(data, "procedure_code");
            }
            default -> {
            }
        }
    }

    private void required(JsonNode data, String name) {
        if (!data.has(name) || !data.get(name).isTextual() || data.get(name).asText().isBlank())
            throw bad("Обязательное поле data." + name);
    }

    @Transactional
    public Map<String, Object> createRecord(UUID dir, UUID ver, RecordCreate req) {
        editable(dir, ver);
        DirectoryEntity d = directory(dir);
        validate(d, req.code(), req.name(), req.data());
        String code = normalize(req.code());
        if (records.findByVersionIdAndCode(ver, code).isPresent()) throw conflict("Код уже есть в этой версии");
        DirectoryRecord r = new DirectoryRecord();
        r.id = UUID.randomUUID();
        r.logicalId = UUID.randomUUID();
        r.versionId = ver;
        r.code = code;
        r.name = req.name().trim();
        r.description = req.description();
        r.data = req.data().deepCopy();
        r.createdAt = Instant.now();
        records.save(r);
        Map<String, Object> view = recordView(r);
        audit("DIRECTORY_RECORD", r.logicalId, ver, "CREATE", null, view, null);
        return view;
    }

    @Transactional
    public Map<String, Object> patchRecord(UUID dir, UUID ver, UUID id, RecordPatch req) {
        editable(dir, ver);
        DirectoryRecord r = record(ver, id);
        Map<String, Object> old = recordView(r);
        String code = req.code() == null ? r.code : normalize(req.code());
        String name = req.name() == null ? r.name : req.name().trim();
        JsonNode data = req.data() == null ? r.data : req.data();
        validate(directory(dir), code, name, data);
        if (!code.equals(r.code) && records.findByVersionIdAndCode(ver, code).isPresent())
            throw conflict("Код уже есть в этой версии");
        r.code = code;
        r.name = name;
        if (req.description() != null) r.description = req.description();
        r.data = data.deepCopy();
        records.save(r);
        Map<String, Object> view = recordView(r);
        audit("DIRECTORY_RECORD", r.logicalId, ver, "UPDATE", old, view, null);
        return view;
    }

    @Transactional
    public Object deleteRecord(UUID dir, UUID ver, UUID id) {
        editable(dir, ver);
        DirectoryRecord r = record(ver, id);
        Map<String, Object> old = recordView(r);
        stages.deleteAll(stages.findByRecordIdOrderBySequenceNumber(r.id));
        records.delete(r);
        audit("DIRECTORY_RECORD", r.logicalId, ver, "DELETE", old, null, null);
        return null;
    }

    private void ensureProcedure(UUID dir) {
        if (!"PROCEDURE_TEMPLATE".equals(directory(dir).kind))
            throw bad("Этапы доступны только для PROCEDURE_TEMPLATE");
    }

    private void validStage(int seq, String operation, int duration) {
        if (seq < 1 || duration < 1 || operation == null || operation.isBlank())
            throw bad("Некорректный номер, код операции или длительность этапа");
    }

    @Transactional
    public Map<String, Object> createStage(UUID dir, UUID ver, UUID recordId, StageCreate req) {
        editable(dir, ver);
        ensureProcedure(dir);
        record(ver, recordId);
        validStage(req.sequenceNumber(), req.operationCode(), req.expectedDurationSeconds());
        if (stages.findByRecordIdAndSequenceNumber(recordId, req.sequenceNumber()).isPresent())
            throw conflict("Номер этапа уже занят");
        ProcedureStage s = new ProcedureStage();
        s.id = UUID.randomUUID();
        s.logicalId = UUID.randomUUID();
        s.recordId = recordId;
        s.sequenceNumber = req.sequenceNumber();
        s.operationCode = normalize(req.operationCode());
        s.description = req.description();
        s.expectedDurationSeconds = req.expectedDurationSeconds();
        s.requiredRole = req.requiredRole() == null ? "TECHNICIAN" : normalize(req.requiredRole());
        s.mandatory = req.mandatory() == null || req.mandatory();
        stages.save(s);
        Map<String, Object> view = stageView(s);
        audit("PROCEDURE_STAGE", s.logicalId, ver, "CREATE", null, view, null);
        return view;
    }

    @Transactional
    public Map<String, Object> patchStage(UUID dir, UUID ver, UUID recordId, UUID stageId, StagePatch req) {
        editable(dir, ver);
        ensureProcedure(dir);
        record(ver, recordId);
        ProcedureStage s = stages.findByIdAndRecordId(stageId, recordId).orElseThrow(() -> missing("Этап не найден"));
        Map<String, Object> old = stageView(s);
        int seq = req.sequenceNumber() == null ? s.sequenceNumber : req.sequenceNumber();
        int duration = req.expectedDurationSeconds() == null ? s.expectedDurationSeconds : req.expectedDurationSeconds();
        String operation = req.operationCode() == null ? s.operationCode : normalize(req.operationCode());
        validStage(seq, operation, duration);
        if (seq != s.sequenceNumber && stages.findByRecordIdAndSequenceNumber(recordId, seq).isPresent())
            throw conflict("Номер этапа уже занят");
        s.sequenceNumber = seq;
        s.expectedDurationSeconds = duration;
        s.operationCode = operation;
        if (req.description() != null) s.description = req.description();
        if (req.requiredRole() != null) s.requiredRole = normalize(req.requiredRole());
        if (req.mandatory() != null) s.mandatory = req.mandatory();
        stages.save(s);
        Map<String, Object> view = stageView(s);
        audit("PROCEDURE_STAGE", s.logicalId, ver, "UPDATE", old, view, null);
        return view;
    }

    @Transactional
    public Object deleteStage(UUID dir, UUID ver, UUID recordId, UUID stageId) {
        editable(dir, ver);
        ensureProcedure(dir);
        record(ver, recordId);
        ProcedureStage s = stages.findByIdAndRecordId(stageId, recordId).orElseThrow(() -> missing("Этап не найден"));
        Map<String, Object> old = stageView(s);
        stages.delete(s);
        audit("PROCEDURE_STAGE", s.logicalId, ver, "DELETE", old, null, null);
        return null;
    }

    private boolean operationExists(String code) {
        return catalog.activeOperationCode(code);
    }

    private boolean aircraftTypeExists(String code) {
        return catalog.activeAircraftTypeCode(code);
    }

    private List<DirectoryRecord> approvedRecordsOfKind(String kind) {
        List<DirectoryRecord> all = new ArrayList<>();
        for (DirectoryEntity d : directories.findByKind(kind)) {
            versions.findByDirectoryIdAndStatus(d.id, ReferenceVersion.Status.APPROVED)
                    .ifPresent(v -> all.addAll(records.findByVersionIdOrderByCode(v.id)));
        }
        return all;
    }

    private void validateForPublication(UUID dir, UUID ver) {
        DirectoryEntity d = directory(dir);
        for (DirectoryRecord r : records.findByVersionIdOrderByCode(ver)) {
            validate(d, r.code, r.name, r.data);
            if (d.kind.equals("AIRCRAFT") && !aircraftTypeExists(r.data.path("aircraft_type_code").asText()))
                throw bad("Тип ВС " + r.data.path("aircraft_type_code").asText() + " отсутствует в опубликованном каталоге");
            if (d.kind.equals("PROCEDURE_TEMPLATE")) {
                if (!aircraftTypeExists(r.data.path("aircraft_type_code").asText()))
                    throw bad("Тип ВС в шаблоне не опубликован");
                List<ProcedureStage> ss = stages.findByRecordIdOrderBySequenceNumber(r.id);
                if (ss.isEmpty()) throw bad("Процедура " + r.code + " не содержит этапов");
                for (ProcedureStage s : ss) {
                    if (!operationExists(s.operationCode))
                        throw bad("Операция " + s.operationCode + " отсутствует в опубликованном каталоге");
                    if (!catalog.operationAllowedForAircraftType(r.data.path("aircraft_type_code").asText(), s.operationCode))
                        throw bad("Операция " + s.operationCode + " не привязана к типу ВС " + r.data.path("aircraft_type_code").asText());
                }
            }
        }
    }

    @Transactional
    public Map<String, Object> submit(UUID dir, UUID ver) {
        ReferenceVersion v = editable(dir, ver);
        validateForPublication(dir, ver);
        Map<String, Object> old = versionView(v);
        v.status = ReferenceVersion.Status.REVIEW;
        v.updatedAt = Instant.now();
        versions.save(v);
        Map<String, Object> out = versionView(v);
        audit("REFERENCE_VERSION", v.id, v.id, "SUBMIT", old, out, null);
        return out;
    }

    @Transactional
    public Map<String, Object> approve(UUID dir, UUID ver) {
        ensureVersionedKind(dir);
        directories.lockById(dir).orElseThrow(() -> missing("Справочник не найден"));
        ReferenceVersion v = version(dir, ver);
        if (v.status != ReferenceVersion.Status.REVIEW) throw conflict("Утверждать можно только REVIEW");
        ReferenceVersion active = versions.findByDirectoryIdAndStatus(dir, ReferenceVersion.Status.APPROVED).orElse(null);
        if (!Objects.equals(v.baseVersionId, active == null ? null : active.id))
            throw conflict("DRAFT основан на устаревшей версии; нужен новый черновик");
        validateForPublication(dir, ver);
        if (active != null) {
            Map<String, Object> oldActive = versionView(active);
            active.status = ReferenceVersion.Status.ARCHIVED;
            active.updatedAt = Instant.now();
            versions.saveAndFlush(active);
            audit("REFERENCE_VERSION", active.id, active.id, "ARCHIVE", oldActive, versionView(active), "Заменена новой утверждённой версией");
        }
        Map<String, Object> old = versionView(v);
        v.status = ReferenceVersion.Status.APPROVED;
        v.approvedBy = actor.actorId();
        v.publishedAt = Instant.now();
        v.updatedAt = v.publishedAt;
        versions.saveAndFlush(v);
        Map<String, Object> out = versionView(v);
        audit("REFERENCE_VERSION", v.id, v.id, "APPROVE", old, out, null);
        return out;
    }

    @Transactional
    public Map<String, Object> reject(UUID dir, UUID ver, String reason) {
        ensureVersionedKind(dir);
        ReferenceVersion v = version(dir, ver);
        if (v.status != ReferenceVersion.Status.REVIEW) throw conflict("Отклонить можно только REVIEW");
        if (reason == null || reason.isBlank()) throw bad("При отклонении необходима причина");
        Map<String, Object> old = versionView(v);
        v.status = ReferenceVersion.Status.DRAFT;
        v.updatedAt = Instant.now();
        versions.save(v);
        Map<String, Object> out = versionView(v);
        audit("REFERENCE_VERSION", v.id, v.id, "REJECT", old, out, reason);
        return out;
    }

    @Transactional
    public Map<String, Object> archive(UUID dir, UUID ver, String reason) {
        ensureVersionedKind(dir);
        ReferenceVersion v = version(dir, ver);
        if (v.status != ReferenceVersion.Status.APPROVED) throw conflict("Архивировать вручную можно только APPROVED");
        Map<String, Object> old = versionView(v);
        v.status = ReferenceVersion.Status.ARCHIVED;
        v.updatedAt = Instant.now();
        versions.saveAndFlush(v);
        Map<String, Object> out = versionView(v);
        audit("REFERENCE_VERSION", v.id, v.id, "ARCHIVE", old, out, reason);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> diff(UUID dir, UUID from, UUID to) {
        version(dir, from);
        version(dir, to);
        Map<UUID, Map<String, Object>> a = records.findByVersionIdOrderByCode(from).stream().collect(Collectors.toMap(r -> r.logicalId, this::recordComparable));
        Map<UUID, Map<String, Object>> b = records.findByVersionIdOrderByCode(to).stream().collect(Collectors.toMap(r -> r.logicalId, this::recordComparable));
        List<Object> added = new ArrayList<>(), removed = new ArrayList<>(), modified = new ArrayList<>();
        for (var entry : b.entrySet()) {
            if (!a.containsKey(entry.getKey())) added.add(entry.getValue());
            else if (!a.get(entry.getKey()).equals(entry.getValue()))
                modified.add(obj("logical_id", entry.getKey(), "before", a.get(entry.getKey()), "after", entry.getValue()));
        }
        for (var entry : a.entrySet()) if (!b.containsKey(entry.getKey())) removed.add(entry.getValue());
        return obj("from_version_id", from, "to_version_id", to, "added", added, "removed", removed, "modified", modified);
    }

    private Map<String, Object> recordComparable(DirectoryRecord r) {
        return obj("logical_id", r.logicalId, "code", r.code, "name", r.name, "description", r.description,
                "data", r.data, "stages", stages.findByRecordIdOrderBySequenceNumber(r.id).stream().map(s ->
                        obj("logical_id", s.logicalId, "sequence_number", s.sequenceNumber, "operation_code", s.operationCode,
                                "description", s.description, "expected_duration_seconds", s.expectedDurationSeconds,
                                "required_role", s.requiredRole, "mandatory", s.mandatory)).toList());
    }

    @Transactional
    public Map<String, Object> importRecords(UUID dir, UUID ver, ImportRequest req) {
        editable(dir, ver);
        DirectoryEntity d = directory(dir);
        if (req.items() == null || req.items().isEmpty() || req.items().size() > 500)
            throw bad("Размер импорта: от 1 до 500 записей");
        if (req.mode() != null && !"UPSERT".equalsIgnoreCase(req.mode()))
            throw bad("MVP поддерживает только mode=UPSERT");
        Set<String> incoming = new HashSet<>();
        for (RecordCreate item : req.items()) {
            validate(d, item.code(), item.name(), item.data());
            if (!incoming.add(normalize(item.code()))) throw bad("Повтор кода во входном файле: " + item.code());
        }
        if (Boolean.TRUE.equals(req.dryRun()))
            return obj("dry_run", true, "total", req.items().size(), "valid", req.items().size(), "errors", List.of());
        int count = 0;
        for (RecordCreate item : req.items()) {
            DirectoryRecord existing = records.findByVersionIdAndCode(ver, normalize(item.code())).orElse(null);
            if (existing == null) {
                createRecord(dir, ver, item);
            } else {
                patchRecord(dir, ver, existing.id, new RecordPatch(item.code(), item.name(), item.description(), item.data()));
            }
            count++;
        }
        return obj("dry_run", false, "imported", count, "errors", List.of());
    }

    private void audit(String entityType, UUID entityId, UUID versionId, String op, Object oldValue, Object newValue, String reason) {
        AuditLog a = new AuditLog();
        a.id = UUID.randomUUID();
        a.actorId = actor.actorId();
        a.entityType = entityType;
        a.entityId = entityId;
        a.versionId = versionId;
        a.operation = op;
        a.oldValue = oldValue == null ? null : mapper.valueToTree(oldValue);
        a.newValue = newValue == null ? null : mapper.valueToTree(newValue);
        a.reason = reason;
        a.requestId = actor.requestId();
        a.createdAt = Instant.now();
        audits.save(a);
        // Для команды Логирование: без передачи содержимого нормативов и данных пользователя.
        events.publishEvent(new ReferenceAuditEvent(a.id, "reference-service", "REFERENCE_" + op,
                entityType, entityId, versionId, a.actorId, a.requestId, a.createdAt));
    }

    private Map<String, Object> auditView(AuditLog a) {
        return obj("id", a.id, "actor_id", a.actorId, "entity_type", a.entityType, "entity_id", a.entityId,
                "reference_version_id", a.versionId, "operation", a.operation, "old_value", a.oldValue, "new_value", a.newValue,
                "reason", a.reason, "request_id", a.requestId, "created_at", a.createdAt);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> audits(String user, String entity, UUID entityId, int limit, int offset) {
        var all = audits.findAll().stream().filter(a -> (user == null || a.actorId.equals(user)) &&
                        (entity == null || a.entityType.equalsIgnoreCase(entity)) && (entityId == null || a.entityId.equals(entityId)))
                .sorted(Comparator.comparing((AuditLog a) -> a.createdAt).reversed()).toList();
        return page(slice(all, limit, offset).stream().map(this::auditView).toList(), all.size(), limit, offset);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> audit(UUID id) {
        return auditView(audits.findById(id).orElseThrow(() -> missing("Аудиторская запись не найдена")));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> publicList(String kind, String search, String filterKey, String filterValue, int limit, int offset) {
        List<DirectoryRecord> all = approvedRecordsOfKind(kind).stream()
                .filter(r -> (search == null || r.name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)) || r.code.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))))
                .filter(r -> filterValue == null || filterValue.equalsIgnoreCase(r.data.path(filterKey).asText())).toList();
        return page(slice(all, limit, offset).stream().map(this::publicView).toList(), all.size(), limit, offset);
    }

    private Map<String, Object> publicView(DirectoryRecord r) {
        Map<String, Object> out = obj("id", r.logicalId, "code", r.code, "name", r.name, "description", r.description,
                "reference_version_id", r.versionId);
        if (r.data != null && r.data.isObject()) r.data.properties().forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> publicItem(String kind, UUID logicalId) {
        DirectoryRecord r = approvedRecordsOfKind(kind).stream().filter(x -> x.logicalId.equals(logicalId)).findFirst()
                .orElseThrow(() -> missing("Объект отсутствует в активном справочнике"));
        if (kind.equals("PROCEDURE_TEMPLATE")) return resolved(r);
        return publicView(r);
    }

    private Map<String, Object> resolved(DirectoryRecord r) {
        Map<String, Object> out = publicView(r);
        out.put("template_id", r.logicalId);
        out.put("status", "APPROVED");
        out.put("stages", stages.findByRecordIdOrderBySequenceNumber(r.id).stream().map(this::stageView).toList());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resolve(String aircraftTypeCode, String procedureCode) {
        if (!catalog.activeAircraftTypeCode(aircraftTypeCode))
            throw missing("Указанный тип ВС отсутствует или неактивен");
        var matching = approvedRecordsOfKind("PROCEDURE_TEMPLATE").stream().filter(r ->
                r.data.path("aircraft_type_code").asText().equalsIgnoreCase(aircraftTypeCode) &&
                        r.data.path("procedure_code").asText().equalsIgnoreCase(procedureCode)).toList();
        if (matching.isEmpty()) throw missing("Утверждённый шаблон для заданного типа ВС не найден");
        if (matching.size() > 1)
            throw conflict("Найдено несколько действующих шаблонов — требуется уточнить применимость");
        return resolved(matching.getFirst());
    }

    /**
     * INTEGRATION — команда «Основная логика»: прочитать зафиксированный снимок процедуры
     * для уже существующего чек-листа. Это НЕ метод выбора актуальной версии для нового рейса.
     * Для нового чек-листа использовать resolve(...); у ARCHIVED usable_for_new_checklist=false.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> procedureVersionSnapshot(UUID versionId) {
        ReferenceVersion v = versions.findById(versionId).orElseThrow(() -> missing("Версия не найдена"));
        DirectoryEntity d = directory(v.directoryId);
        if (!"PROCEDURE_TEMPLATE".equals(d.kind))
            throw missing("Версия не относится к шаблонам процедур");
        if (v.status != ReferenceVersion.Status.APPROVED && v.status != ReferenceVersion.Status.ARCHIVED)
            throw missing("Только опубликованные и архивные снимки доступны внешнему сервису");
        boolean current = v.status == ReferenceVersion.Status.APPROVED;
        return obj("reference_version_id", v.id, "directory_code", d.code,
                "version_number", v.versionNumber, "status", v.status,
                "published_at", v.publishedAt, "usable_for_new_checklist", current,
                "checksum", checksum(v), "records", records.findByVersionIdOrderByCode(v.id)
                        .stream().map(this::recordView).toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(UUID versionId) {
        ReferenceVersion v = versions.findById(versionId).orElseThrow(() -> missing("Версия не найдена"));
        if (v.status == ReferenceVersion.Status.DRAFT || v.status == ReferenceVersion.Status.REVIEW)
            throw missing("Черновые данные недоступны во внешнем API");
        if (v.status == ReferenceVersion.Status.ARCHIVED) actor.supervisor();
        DirectoryEntity d = directory(v.directoryId);
        return obj("reference_version_id", v.id, "directory_code", d.code, "status", v.status,
                "checksum", checksum(v), "records", records.findByVersionIdOrderByCode(v.id).stream().map(this::recordView).toList());
    }

    private String checksum(ReferenceVersion v) {
        try {
            var all = records.findByVersionIdOrderByCode(v.id).stream().map(this::recordComparable).toList();
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(all));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> manifest() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ReferenceVersion v : versions.findByStatus(ReferenceVersion.Status.APPROVED)) {
            DirectoryEntity d = directory(v.directoryId);
            result.add(obj("directory_code", d.code, "reference_version_id", v.id, "checksum", checksum(v), "updated_at", v.publishedAt));
        }
        result.sort(Comparator.comparing(x -> x.get("directory_code").toString()));
        return obj("publication_time", Instant.now(), "items", result);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status(UUID id) {
        ReferenceVersion v = versions.findById(id).orElseThrow(() -> missing("Версия не найдена"));
        boolean active = versions.findByDirectoryIdAndStatus(v.directoryId, ReferenceVersion.Status.APPROVED)
                .map(cur -> cur.id.equals(id)).orElse(false);
        return obj("version_id", id, "status", v.status, "is_current_approved", active, "usable_for_new_checklist", active);
    }
}
