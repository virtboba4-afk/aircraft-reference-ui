package ru.demo.nsi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.demo.nsi.api.Requests.*;
import ru.demo.nsi.config.ActorContext;
import ru.demo.nsi.domain.DirectoryEntity;
import ru.demo.nsi.repository.DirectoryRepository;
import ru.demo.nsi.service.IdempotencyService;
import ru.demo.nsi.service.NsiService;

import java.util.List;
import java.util.UUID;

/**
 * A small workflow API for the single PROCEDURE_TEMPLATE directory in the teaching MVP.
 * Internal NsiService continues to manage immutable versions and their stages.
 */
@RestController
@RequestMapping("/api/v1/references")
@Tag(name = "3. Шаблоны процедур — версии и аудит",
     description = "DRAFT → REVIEW → APPROVED. В MVP один реестр PROCEDURE_TEMPLATE.")
public class AdminController {
    private final NsiService nsi;
    private final DirectoryRepository directories;
    private final ActorContext actor;
    private final IdempotencyService idem;

    public AdminController(NsiService nsi, DirectoryRepository directories,
                           ActorContext actor, IdempotencyService idem) {
        this.nsi = nsi;
        this.directories = directories;
        this.actor = actor;
        this.idem = idem;
    }

    private UUID procedureDirectoryId() {
        List<DirectoryEntity> found = directories.findByKind("PROCEDURE_TEMPLATE");
        if (found.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Реестр шаблонов процедур не найден");
        }
        if (found.size() != 1) {
            throw new ApiException(HttpStatus.CONFLICT,
                "В учебном API поддерживается один реестр PROCEDURE_TEMPLATE");
        }
        return found.getFirst().id;
    }

    @Operation(summary = "История версий шаблонов процедур")
    @GetMapping("/procedure-templates/versions")
    public Object versions(@RequestParam(required = false) String status,
                           @RequestParam(defaultValue = "50") int limit,
                           @RequestParam(defaultValue = "0") int offset) {
        actor.editor();
        return nsi.versions(procedureDirectoryId(), limit, offset, status);
    }

    @Operation(summary = "Создать новую DRAFT-версию на основе утверждённой")
    @PostMapping("/procedure-templates/versions")
    public ResponseEntity<Object> createVersion(@RequestBody(required = false) VersionCreate req) {
        actor.editor();
        return idem.run(req, 201, () -> nsi.createVersion(procedureDirectoryId(), req));
    }

    @Operation(summary = "Записи и этапы конкретной версии")
    @GetMapping("/procedure-templates/versions/{versionId}/records")
    public Object records(@PathVariable UUID versionId,
                          @RequestParam(required = false) String search,
                          @RequestParam(defaultValue = "50") int limit,
                          @RequestParam(defaultValue = "0") int offset) {
        actor.editor();
        return nsi.records(procedureDirectoryId(), versionId, search, limit, offset);
    }

    @Operation(summary = "Добавить шаблон процедуры в DRAFT")
    @PostMapping("/procedure-templates/versions/{versionId}/records")
    public ResponseEntity<Object> createRecord(@PathVariable UUID versionId,
                                                @Valid @RequestBody RecordCreate req) {
        actor.editor();
        return idem.run(req, 201, () -> nsi.createRecord(procedureDirectoryId(), versionId, req));
    }

    @Operation(summary = "Частично изменить шаблон в DRAFT")
    @PatchMapping("/procedure-templates/versions/{versionId}/records/{recordId}")
    public ResponseEntity<Object> patchRecord(@PathVariable UUID versionId,
                                               @PathVariable UUID recordId,
                                               @RequestBody RecordPatch req) {
        actor.editor();
        return idem.run(req, 200, () -> nsi.patchRecord(procedureDirectoryId(), versionId, recordId, req));
    }

    @Operation(summary = "Добавить этап в черновик процедуры")
    @PostMapping("/procedure-templates/versions/{versionId}/records/{recordId}/stages")
    public ResponseEntity<Object> createStage(@PathVariable UUID versionId,
                                               @PathVariable UUID recordId,
                                               @Valid @RequestBody StageCreate req) {
        actor.editor();
        return idem.run(req, 201, () -> nsi.createStage(procedureDirectoryId(), versionId, recordId, req));
    }

    @Operation(summary = "Частично изменить этап в черновике")
    @PatchMapping("/procedure-templates/versions/{versionId}/records/{recordId}/stages/{stageId}")
    public ResponseEntity<Object> patchStage(@PathVariable UUID versionId,
                                              @PathVariable UUID recordId,
                                              @PathVariable UUID stageId,
                                              @RequestBody StagePatch req) {
        actor.editor();
        return idem.run(req, 200,
            () -> nsi.patchStage(procedureDirectoryId(), versionId, recordId, stageId, req));
    }

    @Operation(summary = "Отправить черновик на согласование")
    @PostMapping("/procedure-templates/versions/{versionId}/submit")
    public ResponseEntity<Object> submit(@PathVariable UUID versionId) {
        actor.editor();
        return idem.run(null, 200, () -> nsi.submit(procedureDirectoryId(), versionId));
    }

    @Operation(summary = "Утвердить версию и архивировать предыдущую")
    @PostMapping("/procedure-templates/versions/{versionId}/approve")
    public ResponseEntity<Object> approve(@PathVariable UUID versionId) {
        actor.supervisor();
        return idem.run(null, 200, () -> nsi.approve(procedureDirectoryId(), versionId));
    }

    @Operation(summary = "Отклонить версию с указанием причины")
    @PostMapping("/procedure-templates/versions/{versionId}/reject")
    public ResponseEntity<Object> reject(@PathVariable UUID versionId,
                                          @RequestBody Reason reason) {
        actor.supervisor();
        return idem.run(reason, 200, () -> nsi.reject(procedureDirectoryId(), versionId, reason.reason()));
    }

    @Operation(summary = "Журнал аудита всех изменений")
    @GetMapping("/audit")
    public Object audit(@RequestParam(name = "user_id", required = false) String user,
                        @RequestParam(name = "entity_type", required = false) String entity,
                        @RequestParam(name = "entity_id", required = false) UUID entityId,
                        @RequestParam(defaultValue = "50") int limit,
                        @RequestParam(defaultValue = "0") int offset) {
        actor.supervisor();
        return nsi.audits(user, entity, entityId, limit, offset);
    }
}
