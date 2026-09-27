package io.github.neisserdev.problemdetails.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.neisserdev.problemdetails.GlobalExceptionHandler;

/**
 * Aplicación Spring Boot real (MVC + Security + validación) con el starter
 * entrando por autoconfiguración. Los textos con tildes no se comprueban
 * literalmente para no depender del charset por defecto de MockMvc.
 */
@SpringBootTest(properties = {
        "problem-details.base-type-url=https://api.ejemplo.com/problemas",
        "spring.mvc.problemdetails.enabled=true"
})
@AutoConfigureMockMvc
class ProblemDetailsIntegracionTest {

    private static final String BASE = "https://api.ejemplo.com/problemas/";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext contexto;

    @Test
    void desplazaAlManejadorDeProblemDetailsDeSpringBoot() {
        assertThat(contexto.getBeansOfType(ResponseEntityExceptionHandler.class).values())
                .singleElement()
                .isInstanceOf(GlobalExceptionHandler.class);
    }

    /* ---------- Errores de negocio ---------- */

    @Test
    void recursoNoEncontrado() throws Exception {
        mvc.perform(get("/publico/pedidos/7"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(BASE + "RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Pedido con id 7 no encontrado"))
                .andExpect(jsonPath("$.instance").value("/publico/pedidos/7"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.resource").value("Pedido"))
                .andExpect(jsonPath("$.resourceId").value("7"))
                .andExpect(jsonPath("$.properties").doesNotExist());
    }

    @Test
    void tipoDeProblemaPropioDelConsumidor() throws Exception {
        mvc.perform(post("/publico/pedidos"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(BASE + "STOCK_INSUFICIENTE"))
                .andExpect(jsonPath("$.title").value("Stock insuficiente"))
                .andExpect(jsonPath("$.code").value("STOCK_INSUFICIENTE"))
                .andExpect(jsonPath("$.disponible").value(3));
    }

    /* ---------- Validación y formato ---------- */

    @Test
    void cuerpoConCamposInvalidos() throws Exception {
        mvc.perform(post("/publico/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"email\":\"no-es-un-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.errores[*].campo", containsInAnyOrder("nombre", "email")))
                .andExpect(jsonPath("$.errores[0].mensaje").isNotEmpty());
    }

    @Test
    void parametroQueIncumpleUnaRestriccion() throws Exception {
        mvc.perform(get("/publico/pagina").param("n", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("n"));
    }

    @Test
    void parametroConTipoIncorrecto() throws Exception {
        mvc.perform(get("/publico/pagina").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.detail", containsString("'n'")));
    }

    @Test
    void jsonMalFormado() throws Exception {
        mvc.perform(post("/publico/usuarios").contentType(MediaType.APPLICATION_JSON).content("{roto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    /* ---------- Errores del framework ---------- */

    @Test
    void metodoNoPermitido() throws Exception {
        mvc.perform(delete("/publico/pedidos/7"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value(BASE + "METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.instance").value("/publico/pedidos/7"));
    }

    @Test
    void rutaInexistente() throws Exception {
        mvc.perform(get("/publico/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
    }

    @Test
    void statusSinRepresentanteCanonico() throws Exception {
        mvc.perform(get("/publico/retirado"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value(BASE + "GONE"))
                .andExpect(jsonPath("$.code").value("GONE"));
    }

    @Test
    void respetaResponseStatusEnExcepcionesPropias() throws Exception {
        mvc.perform(get("/publico/suscripcion"))
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.code").value("PAYMENT_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("Suscripcion caducada"));
    }

    @Test
    void errorInesperado() throws Exception {
        mvc.perform(get("/publico/fallo"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Ha ocurrido un error interno"));
    }

    @Test
    void elAdviceDeLaAplicacionTienePrioridad() throws Exception {
        mvc.perform(get("/publico/consumidor"))
                .andExpect(status().is(418))
                .andExpect(content().string("atendida por la aplicacion"));
    }

    /* ---------- Spring Security ---------- */

    @Test
    void credencialesInvalidasEnElLogin() throws Exception {
        mvc.perform(post("/publico/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void recursoProtegidoSinAutenticar() throws Exception {
        mvc.perform(get("/privado/datos"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.type").value(BASE + "UNAUTHORIZED"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.instance").value("/privado/datos"))
                .andExpect(jsonPath("$.properties").doesNotExist());
    }

    @Test
    void recursoProtegidoSinElRolNecesario() throws Exception {
        mvc.perform(get("/admin/panel").with(user("ana").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void accesoDenegadoEnElControladorSiendoAnonimoEs401() throws Exception {
        mvc.perform(get("/publico/solo-admin"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void accesoDenegadoEnElControladorAutenticadoEs403() throws Exception {
        mvc.perform(get("/publico/solo-admin").with(user("ana")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.instance").value("/publico/solo-admin"));
    }
}
