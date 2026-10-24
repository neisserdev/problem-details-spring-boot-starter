package dev.neisser.exceptions.autoconfigure;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import dev.neisser.exceptions.GlobalExceptionHandler;
import dev.neisser.exceptions.ProblemDetails;
import dev.neisser.exceptions.ProblemDetailsProperties;

/**
 * Autoconfiguración del núcleo del starter: activa el {@link GlobalExceptionHandler}
 * y aplica la propiedad {@code problem-details.base-type-url} a {@link ProblemDetails}.
 *
 * <p>No lleva ningún {@code @ConditionalOnProperty}: el manejo de errores es el
 * propósito del starter, así que se activa siempre que la dependencia está en el
 * classpath, igual que cualquier autoconfiguración estándar de Spring Boot.
 *
 * <p>No declara un {@code JsonMapper} propio: desde Spring Boot 4.0.0-RC1
 * (spring-projects/spring-boot#47298) el {@code JsonMapper} que Spring Boot
 * autoconfigura ya registra {@code ProblemDetailJacksonMixin}, así que
 * {@code ProblemJsonWriter} usa directamente el del contexto.
 */
@AutoConfiguration
@EnableConfigurationProperties(ProblemDetailsProperties.class)
@Import(GlobalExceptionHandler.class)
public class ProblemDetailsAutoConfiguration {

    private final ProblemDetailsProperties properties;

    public ProblemDetailsAutoConfiguration(ProblemDetailsProperties properties) {
        this.properties = properties;
    }

    /**
     * Programa el prefijo del {@code type} antes de que se atienda ninguna
     * petición. {@link ProblemDetails} es una utility class estática —se usa
     * desde código de aplicación sin inyección de dependencias—, así que la
     * única forma de que la propiedad externa le llegue es fijándola una vez
     * al construirse esta autoconfiguración.
     */
    @PostConstruct
    void configurarProblemDetails() {
        ProblemDetails.configurarBaseTypeUrl(properties.getBaseTypeUrl());
    }
}
