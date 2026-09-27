package io.github.neisserdev.problemdetails.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.neisserdev.problemdetails.GlobalExceptionHandler;
import io.github.neisserdev.problemdetails.ProblemDetailsFactory;

/**
 * Autoconfiguración del starter para aplicaciones Spring MVC (servlet).
 *
 * <p>Registra, si la aplicación no define los suyos:
 * <ul>
 *   <li>{@link ProblemDetailsFactory}, con la base de {@code type} configurada.</li>
 *   <li>{@link GlobalExceptionHandler}, salvo que ya exista otro
 *       {@link ResponseEntityExceptionHandler} (por ejemplo una subclase propia
 *       del manejador de la librería).</li>
 *   <li>{@code ProblemJsonWriter}, si hay un {@code JsonMapper} de Jackson 3 en el contexto.</li>
 *   <li>El entry point 401, el manejador 403 y su conexión automática a
 *       Spring Security, si está en el classpath y
 *       {@code problem-details.security.enabled} no es {@code false}.</li>
 * </ul>
 *
 * <p>Se ejecuta después de la autoconfiguración de Jackson, porque necesita
 * saber si existe el {@code JsonMapper}, y antes de la de Spring MVC, para que
 * el {@code ProblemDetailsExceptionHandler} de Spring Boot (el que activa
 * {@code spring.mvc.problemdetails.enabled}) se retire al ver este manejador.
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
        beforeName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(ResponseEntityExceptionHandler.class)
@EnableConfigurationProperties(ProblemDetailsProperties.class)
@Import({
        ProblemDetailsConfigurations.EscrituraJson.class,
        ProblemDetailsConfigurations.FiltrosDeSeguridad.class
})
public final class ProblemDetailsAutoConfiguration {

    ProblemDetailsAutoConfiguration() {
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemDetailsFactory problemDetailsFactory(ProblemDetailsProperties propiedades) {
        return new ProblemDetailsFactory(propiedades.getBaseTypeUrl());
    }

    @Bean
    @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)
    GlobalExceptionHandler problemDetailsExceptionHandler(ProblemDetailsFactory fabrica,
                                                          ProblemDetailsProperties propiedades) {
        return new GlobalExceptionHandler(fabrica, propiedades.getSecurity().isEnabled());
    }
}
