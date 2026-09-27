package io.github.neisserdev.problemdetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;
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

    @Test
    void usaLaBasePorDefecto() {
        assertThat(new ProblemDetailsFactory().tipoDe(ErrorCode.RESOURCE_NOT_FOUND))
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

        ProblemDetail pd = fabrica.crear(ErrorCode.RESOURCE_NOT_FOUND, "Pedido con id 7 no encontrado",
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
        ProblemDetail pd = new ProblemDetailsFactory().crear(new ResourceNotFoundException("Pedido", 7), "/pedidos/7");

        assertThat(pd.getDetail()).isEqualTo("Pedido con id 7 no encontrado");
        assertThat(pd.getProperties())
                .containsEntry("resource", "Pedido")
                .containsEntry("resourceId", "7");
    }

    @Test
    void aceptaTiposDeProblemaPropios() {
        ProblemDetail pd = new ProblemDetailsFactory()
                .crear(ErroresDePrueba.STOCK_INSUFICIENTE, "Quedan 3 unidades", "/pedidos");

        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getType()).hasToString("/problems/STOCK_INSUFICIENTE");
        assertThat(pd.getTitle()).isEqualTo("Stock insuficiente");
        assertThat(pd.getProperties()).containsEntry("code", "STOCK_INSUFICIENTE");
    }

    @Test
    void omiteElInstanceSiLaRutaNoEsUnUriValido() {
        ProblemDetail pd = new ProblemDetailsFactory().crear(ErrorCode.MALFORMED_REQUEST, "x", "/ruta con espacios");

        assertThat(pd.getInstance()).isNull();
    }

    @Test
    void rechazaCodigosQueNoCabenEnUnUri() {
        assertThatIllegalStateException().isThrownBy(() -> new ProblemDetailsFactory().tipoDe("CODIGO CON ESPACIOS"));
    }

    @Test
    void completaLosProblemasDelFrameworkConSuCodigoCanonico() {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);

        new ProblemDetailsFactory().completar(pd, 405, "/pedidos/1");

        assertThat(pd.getProperties()).containsEntry("code", "METHOD_NOT_ALLOWED").containsKey("timestamp");
        assertThat(pd.getType()).hasToString("/problems/METHOD_NOT_ALLOWED");
        assertThat(pd.getTitle()).isEqualTo("Método HTTP no permitido");
        assertThat(pd.getInstance()).hasToString("/pedidos/1");
    }

    @Test
    void derivaElCodigoDeLosStatusSinRepresentante() {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.GONE);

        new ProblemDetailsFactory().completar(pd, 410, "/recurso");

        assertThat(pd.getProperties()).containsEntry("code", "GONE");
        assertThat(pd.getType()).hasToString("/problems/GONE");
        assertThat(ProblemDetailsFactory.codigoGenericoDe(599)).isEqualTo("HTTP_599");
    }

    @Test
    void nuncaDerivaCodigosDeConstantesDeprecadasDeSpring() {
        // Spring 7 deprecó PAYLOAD_TOO_LARGE, UNPROCESSABLE_ENTITY e I_AM_A_TEAPOT
        assertThat(ProblemDetailsFactory.codigoGenericoDe(413)).isEqualTo("CONTENT_TOO_LARGE");
        assertThat(ProblemDetailsFactory.codigoGenericoDe(422)).isEqualTo("UNPROCESSABLE_CONTENT");
        assertThat(ProblemDetailsFactory.codigoGenericoDe(418)).isEqualTo("HTTP_418");
        assertThat(ProblemDetailsFactory.codigoGenericoDe(402)).isEqualTo("PAYMENT_REQUIRED");
    }

    @Test
    void noPisaUnProblemaQueYaTieneCodigo() {
        ProblemDetailsFactory fabrica = new ProblemDetailsFactory();
        ProblemDetail pd = fabrica.crear(ErrorCode.VALIDATION_ERROR, "x", "/a");

        fabrica.completar(pd, 400, "/b");

        assertThat(pd.getProperties()).containsEntry("code", "VALIDATION_ERROR");
        assertThat(pd.getTitle()).isEqualTo("Error de validación");
        assertThat(pd.getInstance()).hasToString("/a");
    }
}
