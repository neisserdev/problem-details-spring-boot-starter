package dev.neisser.exceptions.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import dev.neisser.exceptions.ProblemJsonWriter;
import dev.neisser.exceptions.security.SecurityAccessDeniedHandler;
import dev.neisser.exceptions.security.SecurityAuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

/**
 * Autoconfiguración del módulo de seguridad: {@link ProblemJsonWriter},
 * {@link SecurityAccessDeniedHandler} y {@link SecurityAuthenticationEntryPoint}.
 *
 * <p>Se desactiva por completo, sin dejar ningún bean a medias, si:
 * <ul>
 *   <li>Spring Security no está en el classpath ({@code @ConditionalOnClass}), o</li>
 *   <li>el proyecto anfitrión fija {@code problem-details.security.enabled=false}
 *       (p. ej. porque no usa Spring Security, o prefiere sus propios handlers de
 *       401/403 de filtros).</li>
 * </ul>
 *
 * <p>{@code matchIfMissing = true}: si la propiedad no se declara, el módulo se
 * activa por defecto en cuanto Spring Security está presente — es el
 * comportamiento que tenía el código antes de extraerse a starter.
 */
@AutoConfiguration(after = ProblemDetailsAutoConfiguration.class)
@ConditionalOnClass(AccessDeniedException.class)
@ConditionalOnProperty(prefix = "problem-details.security", name = "enabled", matchIfMissing = true)
public class ProblemDetailsSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ProblemJsonWriter problemJsonWriter(JsonMapper jsonMapper) {
        return new ProblemJsonWriter(jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean(AccessDeniedHandler.class)
    SecurityAccessDeniedHandler securityAccessDeniedHandler(ProblemJsonWriter writer) {
        return new SecurityAccessDeniedHandler(writer);
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationEntryPoint.class)
    SecurityAuthenticationEntryPoint securityAuthenticationEntryPoint(ProblemJsonWriter writer) {
        return new SecurityAuthenticationEntryPoint(writer);
    }
}
