package ru.demo.nsi.integration.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Пишет метаданные в stdout; ПОСЛЕ фиксации транзакции опционально передаёт событие
 * сервису логирования. При недоступном logging не отменяет изменение справочника.
 * Локальная таблица audit_log остаётся источником данных аудита.
 */
@Component
public class AuditEventListener {
    private static final Logger LOG = LoggerFactory.getLogger(AuditEventListener.class);
    private final ObjectProvider<LoggingClient> externalLogging;

    public AuditEventListener(ObjectProvider<LoggingClient> externalLogging) {
        this.externalLogging = externalLogging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAudit(ReferenceAuditEvent event) {
        LOG.info("nsi_audit eventId={} eventType={} entityType={} entityId={} actorId={} requestId={}",
                event.eventId(), event.eventType(), event.entityType(), event.entityId(),
                event.userId(), event.requestId());
        LoggingClient client = externalLogging.getIfAvailable();
        if (client != null) {
            try {
                client.send(event);
            } catch (Exception ex) {
                // TODO(команда Логирование): согласовать outbox/очередь для гарантированной доставки.
                LOG.warn("Внешнее логирование временно недоступно: eventId={}", event.eventId());
            }
        }
    }
}
