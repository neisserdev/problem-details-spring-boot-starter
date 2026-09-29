package io.github.neisserdev.problemdetails.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
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
import io.github.neisserdev.problemdetails.TraceIdProvider;

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
        ProblemDetailsConfigurations.JsonWriting.class,
        ProblemDetailsConfigurations.SecurityFilters.class,
        ProblemDetailsConfigurations.Tracing.class
})
public final class ProblemDetailsAutoConfiguration {

    ProblemDetailsAutoConfiguration() {
    }

    // El contexto delega en el MessageSource de la aplicación
    @Bean
    @ConditionalOnMissingBean
    ProblemDetailsFactory problemDetailsFactory(ProblemDetailsProperties properties, ApplicationContext context,
                                                ObjectProvider<TraceIdProvider> traceIdProviders) {
        TraceIdProvider provider = properties.getTraceId().isEnabled() ? traceIdProviders.getIfAvailable() : null;
        return new ProblemDetailsFactory(properties.getBaseTypeUrl(), context, provider);
    }

    @Bean
    @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)
    GlobalExceptionHandler problemDetailsExceptionHandler(ProblemDetailsFactory factory,
                                                          ProblemDetailsProperties properties) {
        return new GlobalExceptionHandler(factory, properties.getSecurity().isEnabled());
    }
}
