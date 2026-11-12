package ru.demo.nsi.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Авторизация: в демо X-Demo-Role; после согласования с командой Авторизация — Bearer JWT.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Модуль Справочники (НСИ) — DEMO", version = "0.2.0",
        description = "31 основных метода + GET исторического снимка для Основной логики. " +
                "Интеграции с Авторизацией и Логированием выключены по умолчанию. " +
                "В демо Authorize: TECHNICIAN / REFERENCE_EDITOR / SUPERVISOR; POST/PATCH требует Idempotency-Key."),
        security = @SecurityRequirement(name = "X-Demo-Role"))
@SecurityScheme(name = "X-Demo-Role", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER, paramName = "X-Demo-Role",
        description = "ТОЛЬКО демонстрация: SUPERVISOR / REFERENCE_EDITOR / TECHNICIAN")
@SecurityScheme(name = "BearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "Будущая интеграция команды Авторизация; использовать только после согласования API.")
public class SwaggerConfig {
    private final boolean authEnabled;

    public SwaggerConfig(@Value("${integrations.auth.enabled:false}") boolean authEnabled) {
        this.authEnabled = authEnabled;
    }

    @Bean
    OpenApiCustomizer idempotencyHeader() {
        return api -> api.getPaths().forEach((path, item) -> {
            Stream.of(item.getPost(), item.getPatch(), item.getDelete()).filter(Objects::nonNull)
                    .forEach(op -> op.addParametersItem(new Parameter().name("Idempotency-Key").in("header")
                            .required(true).description("Уникальный ключ запроса; повтор возвращает исходный ответ")
                            .schema(new StringSchema())));
        });
    }

    @Bean
    OpenApiCustomizer authSchemeForMode() {
        return api -> {
            if (api.getComponents() == null || api.getComponents().getSecuritySchemes() == null) return;
            if (authEnabled) {
                api.getComponents().getSecuritySchemes().remove("X-Demo-Role");
                api.setSecurity(List.of(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("BearerAuth")));
            } else {
                api.getComponents().getSecuritySchemes().remove("BearerAuth");
                api.setSecurity(List.of(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("X-Demo-Role")));
            }
        };
    }
}
