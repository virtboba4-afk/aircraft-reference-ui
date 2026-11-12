package ru.demo.nsi.integration.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.demo.nsi.api.ApiException;

import java.time.Duration;
import java.util.Map;

/**
 * INTEGRATION TODO — команда «Авторизация».
 * Контракт взят из присланной спецификации: POST /api/auth/verify и
 * POST /api/auth/permissions/check. В MVP компонент НЕ создаётся (enabled=false).
 * Перед включением согласовать Bearer JWT / сессионную cookie от BFF, URL и справочник прав.
 */
@Component
@ConditionalOnProperty(prefix = "integrations.auth", name = "enabled", havingValue = "true")
public class AuthClient {
    private final RestClient rest;

    public AuthClient(RestClient.Builder builder,
                      @Value("${integrations.auth.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.rest = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public Identity verifyBearer(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.substring(7).isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Не передан Bearer-токен");
        }
        try {
            // TODO(команда Авторизация): удостовериться, что /verify принимает именно JWT, а не BFF-cookie.
            Identity user = rest.post().uri("/api/auth/verify")
                    .body(Map.of("token", header.substring(7).trim()))
                    .retrieve().body(Identity.class);
            if (user == null || user.userId() == null || user.userId().isBlank() ||
                    user.role() == null || user.role().isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Сервис авторизации вернул неполную личность");
            }
            return user;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 401) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Недействительный токен");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Сервис авторизации вернул ошибку");
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Сервис авторизации недоступен");
        }
    }

    public boolean hasPermission(String userId, String permission) {
        try {
            // TODO(команда Авторизация): подтвердить имена reference.* в таблице permissions.
            PermissionDecision result = rest.post().uri("/api/auth/permissions/check")
                    .body(Map.of("user_id", userId, "permission", permission))
                    .retrieve().body(PermissionDecision.class);
            if (result == null || result.allowed() == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Сервис авторизации не вернул allowed");
            }
            return result.allowed();
        } catch (RestClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Ошибка проверки прав через сервис авторизации");
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Сервис авторизации недоступен");
        }
    }

    public record Identity(@JsonProperty("user_id") String userId, String role) {
    }

    public record PermissionDecision(Boolean allowed) {
    }
}
