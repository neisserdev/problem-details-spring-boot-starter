package io.github.neisserdev.problemdetails.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.neisserdev.problemdetails.ErrorCode;
import io.github.neisserdev.problemdetails.GlobalExceptionHandler;
import io.github.neisserdev.problemdetails.ProblemDetailsFactory;
import io.github.neisserdev.problemdetails.ProblemJsonWriter;
import io.github.neisserdev.problemdetails.security.SecurityAccessDeniedHandler;
import io.github.neisserdev.problemdetails.security.SecurityAuthenticationEntryPoint;

import tools.jackson.databind.json.JsonMapper;

class ProblemDetailsAutoConfigurationTest {

    private static final String CUSTOMIZER = "problemDetailsExceptionHandlingCustomizer";
    private static final String MANEJADOR = "problemDetailsExceptionHandler";

    private final WebApplicationContextRunner contexto = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, ProblemDetailsAutoConfiguration.class));

    @Test
    void registraTodoPorDefecto() {
        contexto.run(ctx -> assertThat(ctx)
                .hasSingleBean(ProblemDetailsFactory.class)
                .hasSingleBean(GlobalExceptionHandler.class)
                .hasSingleBean(ProblemJsonWriter.class)
                .hasSingleBean(SecurityAuthenticationEntryPoint.class)
                .hasSingleBean(SecurityAccessDeniedHandler.class)
                .hasBean(CUSTOMIZER));
    }

    @Test
    void laBanderaDeSeguridadSoloApagaLaIntegracionConSpringSecurity() {
        contexto.withPropertyValues("problem-details.security.enabled=false")
                .run(ctx -> assertThat(ctx)
                        .hasSingleBean(GlobalExceptionHandler.class)
                        .hasSingleBean(ProblemJsonWriter.class)
                        .doesNotHaveBean(SecurityAuthenticationEntryPoint.class)
                        .doesNotHaveBean(SecurityAccessDeniedHandler.class)
                        .doesNotHaveBean(CUSTOMIZER));
    }

    @Test
    void arrancaSinSpringSecurityEnElClasspath() {
        contexto.withClassLoader(new FilteredClassLoader("org.springframework.security"))
                .run(ctx -> assertThat(ctx)
                        .hasNotFailed()
                        .hasSingleBean(GlobalExceptionHandler.class)
                        .hasSingleBean(ProblemJsonWriter.class)
                        .doesNotHaveBean(SecurityAuthenticationEntryPoint.class)
                        .doesNotHaveBean(CUSTOMIZER));
    }

    @Test
    void sinJsonMapperNoHayEscritorNiSeguridadPeroSiManejador() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ProblemDetailsAutoConfiguration.class))
                .run(ctx -> assertThat(ctx)
                        .hasNotFailed()
                        .hasSingleBean(GlobalExceptionHandler.class)
                        .doesNotHaveBean(ProblemJsonWriter.class)
                        .doesNotHaveBean(SecurityAuthenticationEntryPoint.class));
    }

    @Test
    void cedeElSitioAUnManejadorPropioDeLaAplicacion() {
        contexto.withUserConfiguration(ConManejadorPropio.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ResponseEntityExceptionHandler.class).doesNotHaveBean(MANEJADOR);
                    assertThat(ctx.getBean(ResponseEntityExceptionHandler.class)).isInstanceOf(ManejadorPropio.class);
                });
    }

    @Test
    void respetaUnaFactoryPropia() {
        contexto.withBean(ProblemDetailsFactory.class, () -> new ProblemDetailsFactory("urn:propio:"))
                .run(ctx -> assertThat(ctx.getBean(ProblemDetailsFactory.class).getBaseType())
                        .isEqualTo("urn:propio:"));
    }

    @Test
    void usaLaBaseConfiguradaParaElType() {
        contexto.withPropertyValues("problem-details.base-type-url=https://api.ejemplo.com/problemas")
                .run(ctx -> assertThat(ctx.getBean(ProblemDetailsFactory.class).getBaseType())
                        .isEqualTo("https://api.ejemplo.com/problemas/"));
    }

    @Test
    void fallaAlArrancarSiLaBaseNoFormaUnUri() {
        contexto.withPropertyValues("problem-details.base-type-url=https://api ejemplo.com/")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void noSeActivaFueraDeUnaAplicacionServlet() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class, ProblemDetailsAutoConfiguration.class))
                .run(ctx -> assertThat(ctx)
                        .doesNotHaveBean(ProblemDetailsFactory.class)
                        .doesNotHaveBean(GlobalExceptionHandler.class));
    }

    @Test
    void elEscritorDejaLasExtensionesEnLaRaizDelJson() {
        contexto.run(ctx -> {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/privado/datos");
            MockHttpServletResponse response = new MockHttpServletResponse();

            ctx.getBean(ProblemJsonWriter.class).escribir(response, request, ErrorCode.TOO_MANY_REQUESTS,
                    "Has superado el limite de peticiones", "Retry-After", "30");

            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getContentType()).startsWith("application/problem+json");
            assertThat(response.getHeader("Retry-After")).isEqualTo("30");

            // Con el JsonMapper de Spring Boot (que registra el mixin de ProblemDetail),
            // code y timestamp van en la raíz y no anidados bajo "properties".
            @SuppressWarnings("unchecked")
            Map<String, Object> cuerpo = ctx.getBean(JsonMapper.class)
                    .readValue(response.getContentAsString(), Map.class);
            assertThat(cuerpo)
                    .containsEntry("code", "TOO_MANY_REQUESTS")
                    .containsEntry("status", 429)
                    .containsEntry("instance", "/privado/datos")
                    .containsEntry("type", "/problems/TOO_MANY_REQUESTS")
                    .containsKey("timestamp")
                    .doesNotContainKey("properties");
        });
    }

    @Test
    void elEntryPointEnviaLaCabeceraWwwAuthenticateConfigurada() {
        contexto.withPropertyValues("problem-details.security.www-authenticate=Basic realm=\"api\"")
                .run(ctx -> {
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    ctx.getBean(SecurityAuthenticationEntryPoint.class).commence(
                            new MockHttpServletRequest("GET", "/privado"), response,
                            new InsufficientAuthenticationException("sin credenciales"));

                    assertThat(response.getStatus()).isEqualTo(401);
                    assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Basic realm=\"api\"");
                    assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
                });
    }

    @Test
    void elEntryPointOmiteLaCabeceraSiSeConfiguraVacia() {
        contexto.withPropertyValues("problem-details.security.www-authenticate=")
                .run(ctx -> {
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    ctx.getBean(SecurityAuthenticationEntryPoint.class).commence(
                            new MockHttpServletRequest("GET", "/privado"), response,
                            new InsufficientAuthenticationException("sin credenciales"));

                    assertThat(response.getStatus()).isEqualTo(401);
                    assertThat(response.getHeader("WWW-Authenticate")).isNull();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ConManejadorPropio {

        @Bean
        ManejadorPropio manejadorPropio(ProblemDetailsFactory fabrica) {
            return new ManejadorPropio(fabrica);
        }
    }

    static class ManejadorPropio extends GlobalExceptionHandler {

        ManejadorPropio(ProblemDetailsFactory fabrica) {
            super(fabrica, true);
        }
    }
}
