package ru.demo.nsi.integration.logging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * INTEGRATION TODO — команда «Логирование».
 * ВАЖНО: URL /api/logging/events — лишь предлагаемая схема, не контракт команды.
 * enabled=false: компонент отсутствует; внешний HTTP-запрос не выполняется.
 * Для production нужна согласованная схема, авторизация сервис-сервис и надёжная доставка.
 */
@Component
@ConditionalOnProperty(prefix="integrations.logging", name="enabled", havingValue="true")
public class LoggingClient {
    private final RestClient rest;
    private final String eventsPath;

    public LoggingClient(RestClient.Builder builder,
                         @Value("${integrations.logging.base-url}") String baseUrl,
                         @Value("${integrations.logging.events-path}") String eventsPath) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.rest = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.eventsPath = eventsPath;
    }

    public void send(ReferenceAuditEvent event) {
        // TODO(команда Логирование): после получения контракта заменить предполагаемый маршрут и payload.
        rest.post().uri(eventsPath).body(event).retrieve().toBodilessEntity();
    }
}
