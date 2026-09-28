package io.github.neisserdev.problemdetails.autoconfigure;

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
import io.github.neisserdev.problemdetails.security.SecurityAccessDeniedHandler;
import io.github.neisserdev.problemdetails.security.SecurityAuthenticationEntryPoint;

import tools.jackson.databind.json.JsonMapper;

/**
 * Configuraciones que dependen de Jackson 3 y Spring Security, separadas para
 * que no se carguen si esas librerías no están en el classpath.
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
    static class EscrituraJson {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(JsonMapper.class)
        ProblemJsonWriter problemJsonWriter(JsonMapper jsonMapper, ProblemDetailsFactory fabrica) {
            return new ProblemJsonWriter(jsonMapper, fabrica);
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
    static class FiltrosDeSeguridad {

        @Bean
        @ConditionalOnMissingBean
        SecurityAuthenticationEntryPoint problemDetailsAuthenticationEntryPoint(
                ProblemJsonWriter writer, ProblemDetailsProperties propiedades) {
            return new SecurityAuthenticationEntryPoint(writer, propiedades.getSecurity().getWwwAuthenticate());
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
        static class CableadoAutomatico {

            @Bean
            @Order(Ordered.HIGHEST_PRECEDENCE)
            Customizer<ExceptionHandlingConfigurer<HttpSecurity>> problemDetailsExceptionHandlingCustomizer(
                    SecurityAuthenticationEntryPoint entryPoint, SecurityAccessDeniedHandler accessDeniedHandler) {
                return excepciones -> excepciones
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler);
            }
        }
    }
}
