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
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.neisserdev.problemdetails.GlobalExceptionHandler;
import io.github.neisserdev.problemdetails.ProblemDetailsFactory;
import io.github.neisserdev.problemdetails.TraceIdProvider;

/**
 * Auto-configuration for Spring MVC applications.
 *
 * <p>Registers {@link ProblemDetailsFactory} and {@link GlobalExceptionHandler}
 * when the application does not define its own. {@code ProblemJsonWriter} and
 * the security components are registered from {@link ProblemDetailsConfigurations}.
 *
 * <p>It runs after Jackson and before Spring MVC, so the Spring Boot Problem
 * Details handler is not registered.
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
        beforeName = "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(ResponseEntityExceptionHandler.class)
@EnableConfigurationProperties(ProblemDetailsProperties.class)
@ImportRuntimeHints(ProblemDetailsRuntimeHints.class)
@Import({
        ProblemDetailsConfigurations.JsonWriting.class,
        ProblemDetailsConfigurations.SecurityFilters.class,
        ProblemDetailsConfigurations.Tracing.class
})
public final class ProblemDetailsAutoConfiguration {

    ProblemDetailsAutoConfiguration() {
    }

    // The context delegates to the application MessageSource
    @Bean
    @ConditionalOnMissingBean
    ProblemDetailsFactory problemDetailsFactory(ProblemDetailsProperties properties, ApplicationContext context,
                                                ObjectProvider<TraceIdProvider> traceIdProviders) {
        TraceIdProvider provider = properties.getTraceId().isEnabled() ? traceIdProviders.getIfAvailable() : null;
        return new ProblemDetailsFactory(properties.getBaseTypeUrl(), context, provider,
                properties.getLanguage().getLocale());
    }

    @Bean
    @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)
    GlobalExceptionHandler problemDetailsExceptionHandler(ProblemDetailsFactory factory,
                                                          ProblemDetailsProperties properties) {
        return new GlobalExceptionHandler(factory, properties.getSecurity().isEnabled());
    }
}
