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
 * Configuraciones que dependen de librerías opcionales (Jackson 3 y Spring
 * Security). Viven en clases separadas porque las condiciones
 * {@code @ConditionalOnClass} se evalúan leyendo el bytecode sin cargar la
 * clase: si Spring Security no está en el classpath, estas clases nunca se
 * cargan y sus referencias a tipos de seguridad no provocan errores.
 *
 * <p>El orden de {@code @Import} en {@link ProblemDetailsAutoConfiguration}
 * importa: la configuración de seguridad pregunta si existe el
 * {@link ProblemJsonWriter}, así que la de escritura tiene que procesarse antes.
 */
final class ProblemDetailsConfigurations {

    private ProblemDetailsConfigurations() {
    }

    /**
     * Escritor de problem+json para filtros. No depende de la bandera de
     * seguridad: también lo usan filtros propios (límite de peticiones,
     * validación de origen...) aunque la integración con Spring Security esté
     * desactivada.
     */
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

    /**
     * Entry point 401 y manejador 403. Se desactiva con
     * {@code problem-details.security.enabled=false}.
     *
     * <p>Exige también Jackson en el classpath aunque la condición sobre el
     * bean ya lo implique: las condiciones de clase se evalúan antes que las de
     * bean, y así {@code ProblemJsonWriter} nunca se carga sin Jackson.
     */
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

        /**
         * Conexión automática con la cadena de filtros. Spring Security (7.0+)
         * aplica a cada {@code HttpSecurity} los beans
         * {@code Customizer<ExceptionHandlingConfigurer<HttpSecurity>>} antes de
         * entregarlo al {@code SecurityFilterChain} de la aplicación. Con la
         * precedencia más alta se aplica primero, de modo que cualquier
         * {@code http.exceptionHandling(...)} explícito de la aplicación lo
         * sobrescribe.
         */
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
