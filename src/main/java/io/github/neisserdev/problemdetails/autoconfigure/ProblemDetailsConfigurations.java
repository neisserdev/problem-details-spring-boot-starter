package io.github.neisserdev.problemdetails.autoconfigure;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;

import io.github.neisserdev.problemdetails.ProblemDetailsFactory;
import io.github.neisserdev.problemdetails.ProblemJsonWriter;
import io.github.neisserdev.problemdetails.TraceIdProvider;
import io.github.neisserdev.problemdetails.security.SecurityAccessDeniedHandler;
import io.github.neisserdev.problemdetails.security.SecurityAuthenticationEntryPoint;

import tools.jackson.databind.json.JsonMapper;

/**
 * Configuraciones que dependen de Jackson 3, Spring Security y Micrometer Tracing,
 * separadas para que no se carguen si esas librerías no están en el classpath.
 *
 * <p>El orden de {@code @Import} en {@link ProblemDetailsAutoConfiguration} es
 * importante, la de seguridad necesita el {@link ProblemJsonWriter}.
 */
final class ProblemDetailsConfigurations {

    private ProblemDetailsConfigurations() {
    }

    // Independiente de problem-details.security.enabled, lo usan también filtros propios
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JsonMapper.class)
    static class JsonWriting {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(JsonMapper.class)
        ProblemJsonWriter problemJsonWriter(JsonMapper jsonMapper, ProblemDetailsFactory factory) {
            return new ProblemJsonWriter(jsonMapper, factory);
        }
    }

    // Entry point 401 y manejador 403, se desactiva con problem-details.security.enabled=false
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "org.springframework.security.web.AuthenticationEntryPoint",
            "tools.jackson.databind.json.JsonMapper"
    })
    @ConditionalOnBooleanProperty(name = "problem-details.security.enabled", matchIfMissing = true)
    @ConditionalOnBean(ProblemJsonWriter.class)
    static class SecurityFilters {

        @Bean
        @ConditionalOnMissingBean
        SecurityAuthenticationEntryPoint problemDetailsAuthenticationEntryPoint(
                ProblemJsonWriter writer, ProblemDetailsProperties properties) {
            return new SecurityAuthenticationEntryPoint(writer, properties.getSecurity().getWwwAuthenticate());
        }

        @Bean
        @ConditionalOnMissingBean
        SecurityAccessDeniedHandler problemDetailsAccessDeniedHandler(ProblemJsonWriter writer) {
            return new SecurityAccessDeniedHandler(writer);
        }

        // Spring Security 7 aplica este Customizer a cada HttpSecurity.
        // Va primero para que un exceptionHandling(...) de la aplicación lo sobrescriba.
        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(name = "org.springframework.security.config.annotation.web.builders.HttpSecurity")
        static class AutoWiring {

            @Bean
            @Order(Ordered.HIGHEST_PRECEDENCE)
            Customizer<ExceptionHandlingConfigurer<HttpSecurity>> problemDetailsExceptionHandlingCustomizer(
                    SecurityAuthenticationEntryPoint entryPoint, SecurityAccessDeniedHandler accessDeniedHandler) {
                return exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler);
            }
        }
    }

    // El Tracer se resuelve en cada petición, puede no existir aunque la clase esté presente
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    @ConditionalOnBooleanProperty(name = "problem-details.trace-id.enabled", matchIfMissing = true)
    static class Tracing {

        @Bean
        @ConditionalOnMissingBean
        TraceIdProvider problemDetailsTraceIdProvider(ObjectProvider<Tracer> tracer) {
            return () -> {
                Tracer current = tracer.getIfAvailable();
                Span span = current != null ? current.currentSpan() : null;
                return span != null ? span.context().traceId() : null;
            };
        }
    }
}
