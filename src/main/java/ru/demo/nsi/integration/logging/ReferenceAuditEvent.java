package ru.demo.nsi.integration.logging;

import java.time.Instant;
import java.util.UUID;

/** Событие без старых/новых JSON-значений и без JWT: только метаданные для команды логирования. */
public record ReferenceAuditEvent(
    UUID eventId,
    String service,
    String eventType,
    String entityType,
    UUID entityId,
    UUID referenceVersionId,
    String userId,
    String requestId,
    Instant timestamp
) {}
