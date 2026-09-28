package io.github.neisserdev.problemdetails.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.neisserdev.problemdetails.GlobalExceptionHandler;
import io.github.neisserdev.problemdetails.ProblemDetailsFactory;

/**
 * Autoconfiguración para aplicaciones Spring MVC.
 *
 * <p>Registra {@link ProblemDetailsFactory} y {@link GlobalExceptionHandler} si
 * la aplicación no define los suyos. {@code ProblemJsonWriter} y los componentes
 * de seguridad se registran desde {@link ProblemDetailsConfigurations}.
 *
 * <p>Se ejecuta después de Jackson y antes de Spring MVC, de modo que el
 * manejador de Problem Details de Spring Boot no se registra.
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

    // El contexto delega en el MessageSource de la aplicación
    @Bean
    @ConditionalOnMissingBean
    ProblemDetailsFactory problemDetailsFactory(ProblemDetailsProperties propiedades, ApplicationContext contexto) {
        return new ProblemDetailsFactory(propiedades.getBaseTypeUrl(), contexto);
    }

    @Bean
    @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)
    GlobalExceptionHandler problemDetailsExceptionHandler(ProblemDetailsFactory fabrica,
                                                          ProblemDetailsProperties propiedades) {
        return new GlobalExceptionHandler(fabrica, propiedades.getSecurity().isEnabled());
    }
}
