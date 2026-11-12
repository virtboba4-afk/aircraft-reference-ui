package ru.demo.nsi.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.demo.nsi.api.ApiException;
import ru.demo.nsi.integration.auth.AuthClient;

import java.util.Arrays;
import java.util.Locale;

/**
 * LOCAL DEMO: X-Demo-Role / X-Demo-User сохраняются для Swagger.
 * INTEGRATION TODO — команда «Авторизация»: при auth.enabled=true демо-заголовки
 * игнорируются, используется Bearer /verify и /permissions/check (см. AuthClient).
 */
@Component
public class ActorContext {
    private static final String IDENTITY_ATTRIBUTE = ActorContext.class.getName() + ".identity";
    private final HttpServletRequest request;
    private final ObjectProvider<AuthClient> auth;
    private final boolean authEnabled;

    public ActorContext(HttpServletRequest request, ObjectProvider<AuthClient> auth,
                        @Value("${integrations.auth.enabled:false}") boolean authEnabled) {
        this.request = request;
        this.auth = auth;
        this.authEnabled = authEnabled;
    }

    private AuthClient.Identity identity() {
        Object previous = request.getAttribute(IDENTITY_ATTRIBUTE);
        if (previous instanceof AuthClient.Identity cached) return cached;
        AuthClient.Identity verified = auth.getObject().verifyBearer(request.getHeader("Authorization"));
        request.setAttribute(IDENTITY_ATTRIBUTE, verified); // один /verify на HTTP-запрос
        return verified;
    }

    public String role() {
        if (authEnabled) return identity().role().trim().toUpperCase(Locale.ROOT);
        String demo = request.getHeader("X-Demo-Role");
        return demo == null ? "TECHNICIAN" : demo.trim().toUpperCase(Locale.ROOT);
    }

    public String actorId() {
        if (authEnabled) return identity().userId();
        String demo = request.getHeader("X-Demo-User");
        return demo == null || demo.isBlank() ? "demo-" + role().toLowerCase(Locale.ROOT) : demo;
    }

    public String requestId() {
        Object generated = request.getAttribute(RequestCorrelationFilter.ATTRIBUTE);
        if (generated instanceof String id) return id;
        String header = request.getHeader("X-Request-ID");
        return header == null || header.isBlank() ? null : header;
    }

    // TODO(команда Авторизация): сверить права с фактическим справочником permissions.
    public void read() { check("reference.read", "TECHNICIAN", "REFERENCE_EDITOR", "SUPERVISOR"); }
    public void editor() { check("reference.record.write", "REFERENCE_EDITOR", "SUPERVISOR"); }
    public void supervisor() { check("reference.directory.create", "SUPERVISOR"); }
    public void submitVersion() { check("reference.version.submit", "REFERENCE_EDITOR", "SUPERVISOR"); }
    public void approveVersion() { check("reference.version.approve", "SUPERVISOR"); }
    public void auditRead() { check("reference.audit.read", "SUPERVISOR"); }

    private void check(String permission, String... allowedRoles) {
        String current = role();
        if (Arrays.stream(allowedRoles).noneMatch(current::equals)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Недостаточно прав: " + current);
        }
        // В демо не выполняем сетевых вызовов. В интеграционном режиме проверка обязательна.
        if (authEnabled && !auth.getObject().hasPermission(identity().userId(), permission)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Отсутствует разрешение " + permission);
        }
    }
}
