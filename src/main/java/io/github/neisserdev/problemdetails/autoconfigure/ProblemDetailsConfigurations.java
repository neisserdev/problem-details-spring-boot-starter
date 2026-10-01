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
 * Configurations that depend on Jackson 3, Spring Security and Micrometer Tracing,
 * kept apart so they are not loaded when those libraries are not on the classpath.
 *
 * <p>The order of {@code @Import} in {@link ProblemDetailsAutoConfiguration}
 * matters, the security one needs the {@link ProblemJsonWriter}.
 */
final class ProblemDetailsConfigurations {

    private ProblemDetailsConfigurations() {
    }

    // Independent of problem-details.security.enabled, custom filters use it too
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

    // 401 entry point and 403 handler, disabled with problem-details.security.enabled=false
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

        // Spring Security 7 applies this Customizer to every HttpSecurity.
        // It goes first so an exceptionHandling(...) of the application overrides it.
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

    // The Tracer is resolved on each request, it may be missing even when the class is present
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
