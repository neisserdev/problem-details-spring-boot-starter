package io.github.neisserdev.problemdetails.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Map;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.neisserdev.problemdetails.ErrorCode;
import io.github.neisserdev.problemdetails.GlobalExceptionHandler;
import io.github.neisserdev.problemdetails.ProblemDetailsFactory;
import io.github.neisserdev.problemdetails.ProblemJsonWriter;
import io.github.neisserdev.problemdetails.TraceIdProvider;
import io.github.neisserdev.problemdetails.security.SecurityAccessDeniedHandler;
import io.github.neisserdev.problemdetails.security.SecurityAuthenticationEntryPoint;

import tools.jackson.databind.json.JsonMapper;

class ProblemDetailsAutoConfigurationTest {

    private static final String CUSTOMIZER = "problemDetailsExceptionHandlingCustomizer";
    private static final String HANDLER = "problemDetailsExceptionHandler";
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, ProblemDetailsAutoConfiguration.class));

    @Test
    void registersEverythingByDefault() {
        contextRunner.run(ctx -> assertThat(ctx)
                .hasSingleBean(ProblemDetailsFactory.class)
                .hasSingleBean(GlobalExceptionHandler.class)
                .hasSingleBean(ProblemJsonWriter.class)
                .hasSingleBean(SecurityAuthenticationEntryPoint.class)
                .hasSingleBean(SecurityAccessDeniedHandler.class)
                .hasBean(CUSTOMIZER));
    }

    @Test
    void theSecurityFlagOnlyDisablesTheSpringSecurityIntegration() {
        contextRunner.withPropertyValues("problem-details.security.enabled=false")
                .run(ctx -> assertThat(ctx)
                        .hasSingleBean(GlobalExceptionHandler.class)
                        .hasSingleBean(ProblemJsonWriter.class)
                        .doesNotHaveBean(SecurityAuthenticationEntryPoint.class)
                        .doesNotHaveBean(SecurityAccessDeniedHandler.class)
                        .doesNotHaveBean(CUSTOMIZER));
    }

    @Test
    void startsWithoutSpringSecurityOnTheClasspath() {
        contextRunner.withClassLoader(new FilteredClassLoader("org.springframework.security"))
                .run(ctx -> assertThat(ctx)
                        .hasNotFailed()
                        .hasSingleBean(GlobalExceptionHandler.class)
                        .hasSingleBean(ProblemJsonWriter.class)
                        .doesNotHaveBean(SecurityAuthenticationEntryPoint.class)
                        .doesNotHaveBean(CUSTOMIZER));
    }

    @Test
    void withoutJsonMapperThereIsNoWriterNorSecurityButThereIsAHandler() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ProblemDetailsAutoConfiguration.class))
                .run(ctx -> assertThat(ctx)
                        .hasNotFailed()
                        .hasSingleBean(GlobalExceptionHandler.class)
                        .doesNotHaveBean(ProblemJsonWriter.class)
                        .doesNotHaveBean(SecurityAuthenticationEntryPoint.class));
    }

    @Test
    void backsOffWhenTheApplicationDefinesItsOwnHandler() {
        contextRunner.withUserConfiguration(CustomHandlerConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ResponseEntityExceptionHandler.class).doesNotHaveBean(HANDLER);
                    assertThat(ctx.getBean(ResponseEntityExceptionHandler.class)).isInstanceOf(CustomHandler.class);
                });
    }

    @Test
    void usesACustomFactory() {
        contextRunner.withBean(ProblemDetailsFactory.class, () -> new ProblemDetailsFactory("urn:custom:"))
                .run(ctx -> assertThat(ctx.getBean(ProblemDetailsFactory.class).getBaseType())
                        .isEqualTo("urn:custom:"));
    }

    @Test
    void translatesWithTheApplicationMessageSource() {
        contextRunner.withBean("messageSource", MessageSource.class, () -> {
                    StaticMessageSource messages = new StaticMessageSource();
                    messages.addMessage("problemDetails.title.RESOURCE_NOT_FOUND", Locale.ENGLISH, "Nothing here");
                    return messages;
                })
                .run(ctx -> {
                    LocaleContextHolder.setLocale(Locale.ENGLISH);
                    try {
                        assertThat(ctx.getBean(ProblemDetailsFactory.class).title(ErrorCode.RESOURCE_NOT_FOUND))
                                .isEqualTo("Nothing here");
                    } finally {
                        LocaleContextHolder.resetLocaleContext();
                    }
                });
    }

    @Test
    void theLanguagePropertySelectsTheBundledTexts() {
        contextRunner.run(ctx -> assertThat(ctx.getBean(ProblemDetailsFactory.class)
                .title(ErrorCode.RESOURCE_NOT_FOUND)).isEqualTo("Resource not found"));
        contextRunner.withPropertyValues("problem-details.language=es")
                .run(ctx -> assertThat(ctx.getBean(ProblemDetailsFactory.class)
                        .title(ErrorCode.RESOURCE_NOT_FOUND)).isEqualTo("Recurso no encontrado"));
    }

    @Test
    void failsOnStartupWithAnUnsupportedLanguage() {
        contextRunner.withPropertyValues("problem-details.language=fr")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void includesTheTraceIdFromMicrometerTracing() {
        Tracer tracer = mock(Tracer.class, RETURNS_DEEP_STUBS);
        when(tracer.currentSpan().context().traceId()).thenReturn(TRACE_ID);

        contextRunner.withBean(Tracer.class, () -> tracer)
                .run(ctx -> assertThat(propertiesOf(ctx)).containsEntry("traceId", TRACE_ID));
    }

    @Test
    void omitsTheTraceIdWithoutAnActiveTrace() {
        contextRunner.withBean(Tracer.class, () -> Tracer.NOOP)
                .run(ctx -> assertThat(propertiesOf(ctx)).doesNotContainKey("traceId"));
    }

    @Test
    void omitsTheTraceIdAndStartsWithoutATracerBean() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(TraceIdProvider.class);
            assertThat(propertiesOf(ctx)).doesNotContainKey("traceId");
        });
    }

    @Test
    void startsWithoutMicrometerTracingOnTheClasspath() {
        contextRunner.withClassLoader(new FilteredClassLoader("io.micrometer.tracing"))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed().doesNotHaveBean(TraceIdProvider.class);
                    assertThat(propertiesOf(ctx)).doesNotContainKey("traceId");
                });
    }

    @Test
    void usesACustomTraceIdProvider() {
        contextRunner.withBean(TraceIdProvider.class, () -> () -> "custom")
                .run(ctx -> assertThat(propertiesOf(ctx)).containsEntry("traceId", "custom"));
    }

    @Test
    void thePropertyDisablesTheTraceIdEvenWithACustomProvider() {
        Tracer tracer = mock(Tracer.class, RETURNS_DEEP_STUBS);
        when(tracer.currentSpan().context().traceId()).thenReturn(TRACE_ID);

        contextRunner.withBean(Tracer.class, () -> tracer)
                .withPropertyValues("problem-details.trace-id.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean("problemDetailsTraceIdProvider");
                    assertThat(propertiesOf(ctx)).doesNotContainKey("traceId");
                });
        contextRunner.withBean(TraceIdProvider.class, () -> () -> "custom")
                .withPropertyValues("problem-details.trace-id.enabled=false")
                .run(ctx -> assertThat(propertiesOf(ctx)).doesNotContainKey("traceId"));
    }

    private static Map<String, Object> propertiesOf(ApplicationContext ctx) {
        return ctx.getBean(ProblemDetailsFactory.class)
                .create(ErrorCode.INTERNAL_ERROR, "x", "/a")
                .getProperties();
    }

    @Test
    void usesTheConfiguredBaseForTheType() {
        contextRunner.withPropertyValues("problem-details.base-type-url=https://api.example.com/problems")
                .run(ctx -> assertThat(ctx.getBean(ProblemDetailsFactory.class).getBaseType())
                        .isEqualTo("https://api.example.com/problems/"));
    }

    @Test
    void failsOnStartupWhenTheBaseIsNotAValidUri() {
        contextRunner.withPropertyValues("problem-details.base-type-url=https://api example.com/")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void doesNotActivateOutsideAServletApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class, ProblemDetailsAutoConfiguration.class))
                .run(ctx -> assertThat(ctx)
                        .doesNotHaveBean(ProblemDetailsFactory.class)
                        .doesNotHaveBean(GlobalExceptionHandler.class));
    }

    @Test
    void theWriterPutsExtensionsAtTheRootOfTheJson() {
        contextRunner.run(ctx -> {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private/data");
            MockHttpServletResponse response = new MockHttpServletResponse();

            ctx.getBean(ProblemJsonWriter.class).write(response, request, ErrorCode.TOO_MANY_REQUESTS,
                    "Request limit exceeded", "Retry-After", "30");

            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getContentType()).startsWith("application/problem+json");
            assertThat(response.getHeader("Retry-After")).isEqualTo("30");

            // Extensions at the root, not inside "properties"
            @SuppressWarnings("unchecked")
            Map<String, Object> body = ctx.getBean(JsonMapper.class)
                    .readValue(response.getContentAsString(), Map.class);
            assertThat(body)
                    .containsEntry("code", "TOO_MANY_REQUESTS")
                    .containsEntry("status", 429)
                    .containsEntry("instance", "/private/data")
                    .containsEntry("type", "/problems/TOO_MANY_REQUESTS")
                    .containsKey("timestamp")
                    .doesNotContainKey("properties");
        });
    }

    @Test
    void theWriterLeavesACommittedResponseUntouched() {
        contextRunner.run(ctx -> {
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setCommitted(true);

            ctx.getBean(ProblemJsonWriter.class).write(response, new MockHttpServletRequest("GET", "/a"),
                    ErrorCode.TOO_MANY_REQUESTS, "x");

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEmpty();
        });
    }

    @Test
    void theWriterAcceptsAnAlreadyBuiltProblem() {
        contextRunner.run(ctx -> {
            ProblemDetailsFactory factory = ctx.getBean(ProblemDetailsFactory.class);
            ProblemDetail problem = factory.create(ErrorCode.RESOURCE_CONFLICT, "Conflict", "/a",
                    Map.of("version", 3));
            MockHttpServletResponse response = new MockHttpServletResponse();

            ctx.getBean(ProblemJsonWriter.class).write(response, problem);

            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(response.getContentType()).startsWith("application/problem+json");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = ctx.getBean(JsonMapper.class)
                    .readValue(response.getContentAsString(), Map.class);
            assertThat(body).containsEntry("code", "RESOURCE_CONFLICT").containsEntry("version", 3);
        });
    }

    @Test
    void theEntryPointSendsTheConfiguredWwwAuthenticateHeader() {
        contextRunner.withPropertyValues("problem-details.security.www-authenticate=Basic realm=\"api\"")
                .run(ctx -> {
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    ctx.getBean(SecurityAuthenticationEntryPoint.class).commence(
                            new MockHttpServletRequest("GET", "/private"), response,
                            new InsufficientAuthenticationException("no credentials"));

                    assertThat(response.getStatus()).isEqualTo(401);
                    assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Basic realm=\"api\"");
                    assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
                });
    }

    @Test
    void theEntryPointOmitsTheHeaderWhenConfiguredEmpty() {
        contextRunner.withPropertyValues("problem-details.security.www-authenticate=")
                .run(ctx -> {
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    ctx.getBean(SecurityAuthenticationEntryPoint.class).commence(
                            new MockHttpServletRequest("GET", "/private"), response,
                            new InsufficientAuthenticationException("no credentials"));

                    assertThat(response.getStatus()).isEqualTo(401);
                    assertThat(response.getHeader("WWW-Authenticate")).isNull();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomHandlerConfiguration {

        @Bean
        CustomHandler customHandler(ProblemDetailsFactory factory) {
            return new CustomHandler(factory);
        }
    }

    static class CustomHandler extends GlobalExceptionHandler {

        CustomHandler(ProblemDetailsFactory factory) {
            super(factory, true);
        }
    }
}
