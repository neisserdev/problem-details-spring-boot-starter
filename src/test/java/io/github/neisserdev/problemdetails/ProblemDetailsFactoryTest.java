package io.github.neisserdev.problemdetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

class ProblemDetailsFactoryTest {

    enum ErroresDePrueba implements ProblemType {
        STOCK_INSUFICIENTE;

        @Override
        public String getCode() {
            return name();
        }

        @Override
        public String getTitle() {
            return "Stock insuficiente";
        }

        @Override
        public HttpStatusCode getHttpStatus() {
            return HttpStatus.CONFLICT;
        }
    }

    @AfterEach
    void restaurarIdioma() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void usaLaBasePorDefecto() {
        assertThat(new ProblemDetailsFactory().typeOf(ErrorCode.RESOURCE_NOT_FOUND))
                .isEqualTo(URI.create("/problems/RESOURCE_NOT_FOUND"));
    }

    @Test
    void anadeLaBarraFinalSiFalta() {
        assertThat(new ProblemDetailsFactory(" https://api.ejemplo.com/problemas ").getBaseType())
                .isEqualTo("https://api.ejemplo.com/problemas/");
    }

    @Test
    void respetaLasBasesQueYaTerminanEnSeparador() {
        assertThat(new ProblemDetailsFactory("urn:problema:").getBaseType()).isEqualTo("urn:problema:");
        assertThat(new ProblemDetailsFactory("https://docs.ejemplo.com/errores#").getBaseType())
                .isEqualTo("https://docs.ejemplo.com/errores#");
    }

    @Test
    void rechazaBasesVaciasOQueNoFormanUnUri() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProblemDetailsFactory((String) null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProblemDetailsFactory("  "));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProblemDetailsFactory("https://api ejemplo.com/"));
    }

    @Test
    void creaElProblemaConLaFormaDeLaCasa() {
        ProblemDetailsFactory fabrica = new ProblemDetailsFactory("https://api.ejemplo.com/problemas/");

        ProblemDetail pd = fabrica.create(ErrorCode.RESOURCE_NOT_FOUND, "Pedido con id 7 no encontrado",
                "/pedidos/7", Map.of("resource", "Pedido", "code", "PISADO", "timestamp", "ayer"));

        assertThat(pd.getStatus()).isEqualTo(404);
        assertThat(pd.getType()).isEqualTo(URI.create("https://api.ejemplo.com/problemas/RESOURCE_NOT_FOUND"));
        assertThat(pd.getTitle()).isEqualTo("Recurso no encontrado");
        assertThat(pd.getDetail()).isEqualTo("Pedido con id 7 no encontrado");
        assertThat(pd.getInstance()).isEqualTo(URI.create("/pedidos/7"));
        assertThat(pd.getProperties())
                .containsEntry("code", "RESOURCE_NOT_FOUND")
                .containsEntry("resource", "Pedido")
                .containsKey("timestamp");
        assertThat(pd.getProperties().get("timestamp")).isNotEqualTo("ayer");
    }

    @Test
    void creaElProblemaDesdeUnaExcepcionDeNegocio() {
        ProblemDetail pd = new ProblemDetailsFactory().create(new ResourceNotFoundException("Pedido", 7), "/pedidos/7");

        assertThat(pd.getDetail()).isEqualTo("Pedido con id 7 no encontrado");
        assertThat(pd.getProperties())
                .containsEntry("resource", "Pedido")
                .containsEntry("resourceId", "7");
    }

    @Test
    void aceptaTiposDeProblemaPropios() {
        ProblemDetail pd = new ProblemDetailsFactory()
                .create(ErroresDePrueba.STOCK_INSUFICIENTE, "Quedan 3 unidades", "/pedidos");

        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getType()).hasToString("/problems/STOCK_INSUFICIENTE");
        assertThat(pd.getTitle()).isEqualTo("Stock insuficiente");
        assertThat(pd.getProperties()).containsEntry("code", "STOCK_INSUFICIENTE");
    }

    @Test
    void omiteElInstanceSiLaRutaNoEsUnUriValido() {
        ProblemDetail pd = new ProblemDetailsFactory().create(ErrorCode.MALFORMED_REQUEST, "x", "/ruta con espacios");

        assertThat(pd.getInstance()).isNull();
    }

    @Test
    void rechazaCodigosQueNoCabenEnUnUri() {
        assertThatIllegalStateException().isThrownBy(() -> new ProblemDetailsFactory().typeOf("CODIGO CON ESPACIOS"));
    }

    @Test
    void completaLosProblemasDelFrameworkConSuCodigoCanonico() {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);

        new ProblemDetailsFactory().complete(pd, 405, "/pedidos/1");

        assertThat(pd.getProperties()).containsEntry("code", "METHOD_NOT_ALLOWED").containsKey("timestamp");
        assertThat(pd.getType()).hasToString("/problems/METHOD_NOT_ALLOWED");
        assertThat(pd.getTitle()).isEqualTo("Método HTTP no permitido");
        assertThat(pd.getInstance()).hasToString("/pedidos/1");
    }

    @Test
    void derivaElCodigoDeLosStatusSinRepresentante() {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.GONE);

        new ProblemDetailsFactory().complete(pd, 410, "/recurso");

        assertThat(pd.getProperties()).containsEntry("code", "GONE");
        assertThat(pd.getType()).hasToString("/problems/GONE");
        assertThat(ProblemDetailsFactory.genericCodeOf(599)).isEqualTo("HTTP_599");
    }

    @Test
    void nuncaDerivaCodigosDeConstantesDeprecadasDeSpring() {
        // Constantes deprecadas en Spring 7
        assertThat(ProblemDetailsFactory.genericCodeOf(413)).isEqualTo("CONTENT_TOO_LARGE");
        assertThat(ProblemDetailsFactory.genericCodeOf(422)).isEqualTo("UNPROCESSABLE_CONTENT");
        assertThat(ProblemDetailsFactory.genericCodeOf(418)).isEqualTo("HTTP_418");
        assertThat(ProblemDetailsFactory.genericCodeOf(402)).isEqualTo("PAYMENT_REQUIRED");
    }

    @Test
    void noPisaUnProblemaQueYaTieneCodigo() {
        ProblemDetailsFactory fabrica = new ProblemDetailsFactory();
        ProblemDetail pd = fabrica.create(ErrorCode.VALIDATION_ERROR, "x", "/a");

        fabrica.complete(pd, 400, "/b");

        assertThat(pd.getProperties()).containsEntry("code", "VALIDATION_ERROR");
        assertThat(pd.getTitle()).isEqualTo("Error de validación");
        assertThat(pd.getInstance()).hasToString("/a");
    }

    @Test
    void traduceTitulosYDetallesConElMessageSource() {
        StaticMessageSource mensajes = new StaticMessageSource();
        mensajes.addMessage("problemDetails.title.RESOURCE_NOT_FOUND", Locale.ENGLISH, "Resource not found");
        mensajes.addMessage("problemDetails.detail.INTERNAL_ERROR", Locale.ENGLISH, "Internal error");
        mensajes.addMessage("problemDetails.detail.TYPE_MISMATCH.parameter", Locale.ENGLISH,
                "Parameter ''{0}'' must be of type {1}");
        ProblemDetailsFactory fabrica = new ProblemDetailsFactory("/problems/", mensajes);
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(fabrica.create(ErrorCode.RESOURCE_NOT_FOUND, "x", "/a").getTitle()).isEqualTo("Resource not found");
        assertThat(fabrica.detail(ErrorCode.INTERNAL_ERROR, "Ha ocurrido un error interno")).isEqualTo("Internal error");
        assertThat(fabrica.message("problemDetails.detail.TYPE_MISMATCH.parameter",
                "El parámetro ''{0}'' debe ser de tipo {1}", "n", "int"))
                .isEqualTo("Parameter 'n' must be of type int");
    }

    @Test
    void traduceElTituloDeLosProblemasDelFramework() {
        StaticMessageSource mensajes = new StaticMessageSource();
        mensajes.addMessage("problemDetails.title.METHOD_NOT_ALLOWED", Locale.ENGLISH, "Method not allowed");
        mensajes.addMessage("problemDetails.title.GONE", Locale.ENGLISH, "No longer available");
        ProblemDetailsFactory fabrica = new ProblemDetailsFactory("/problems/", mensajes);
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        ProblemDetail metodo = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        fabrica.complete(metodo, 405, "/a");
        ProblemDetail retirado = ProblemDetail.forStatus(HttpStatus.GONE);
        fabrica.complete(retirado, 410, "/b");

        assertThat(metodo.getTitle()).isEqualTo("Method not allowed");
        assertThat(retirado.getTitle()).isEqualTo("No longer available");
    }

    @Test
    void usaElTextoPorDefectoSiNoHayTraduccion() {
        ProblemDetailsFactory fabrica = new ProblemDetailsFactory("/problems/", new StaticMessageSource());
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(fabrica.title(ErrorCode.RESOURCE_NOT_FOUND)).isEqualTo("Recurso no encontrado");
        assertThat(fabrica.detail(ErrorCode.INTERNAL_ERROR, "Ha ocurrido un error interno"))
                .isEqualTo("Ha ocurrido un error interno");
        assertThat(fabrica.message("clave.inexistente", "El parámetro ''{0}'' debe ser de tipo {1}", "n", "int"))
                .isEqualTo("El parámetro 'n' debe ser de tipo int");
    }

    @Test
    void formateaLosArgumentosSinMessageSource() {
        ProblemDetailsFactory fabrica = new ProblemDetailsFactory();

        assertThat(fabrica.message("clave", "El parámetro ''{0}'' debe ser de tipo {1}", "n", "int"))
                .isEqualTo("El parámetro 'n' debe ser de tipo int");
        assertThat(fabrica.message("clave", "Sin argumentos, las comillas 'quedan' igual"))
                .isEqualTo("Sin argumentos, las comillas 'quedan' igual");
    }

    @Test
    void incluyeElTraceIdSiHayTraza() {
        ProblemDetailsFactory fabrica = new ProblemDetailsFactory("/problems/", null,
                () -> "4bf92f3577b34da6a3ce929d0e0e4736");

        ProblemDetail creado = fabrica.create(ErrorCode.RESOURCE_NOT_FOUND, "x", "/a");
        ProblemDetail completado = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        fabrica.complete(completado, 405, "/b");

        assertThat(creado.getProperties()).containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(completado.getProperties()).containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void omiteElTraceIdSiNoHayTrazaOElProveedorFalla() {
        assertThat(sinTraza(new ProblemDetailsFactory())).isTrue();
        assertThat(sinTraza(new ProblemDetailsFactory("/problems/", null, () -> null))).isTrue();
        assertThat(sinTraza(new ProblemDetailsFactory("/problems/", null, () -> ""))).isTrue();
        assertThat(sinTraza(new ProblemDetailsFactory("/problems/", null, () -> {
            throw new IllegalStateException("sin traza");
        }))).isTrue();
    }

    private static boolean sinTraza(ProblemDetailsFactory fabrica) {
        return !fabrica.create(ErrorCode.INTERNAL_ERROR, "x", "/a").getProperties().containsKey("traceId");
    }
}
