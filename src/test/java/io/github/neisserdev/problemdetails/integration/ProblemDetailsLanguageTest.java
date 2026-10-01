package io.github.neisserdev.problemdetails.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import tools.jackson.databind.json.JsonMapper;

// Bundled Spanish texts. The body is read as UTF-8 to compare accents.
@SpringBootTest(properties = "problem-details.language=es")
@AutoConfigureMockMvc
class ProblemDetailsLanguageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void businessErrors() throws Exception {
        Map<String, Object> body = perform(get("/public/orders/7"), 404);

        assertThat(body)
                .containsEntry("title", "Recurso no encontrado")
                .containsEntry("detail", "Order con id 7 no encontrado")
                .containsEntry("code", "RESOURCE_NOT_FOUND");
    }

    @Test
    void springMvcDetailsAreTranslated() throws Exception {
        assertThat(perform(delete("/public/orders/7"), 405))
                .containsEntry("title", "Método HTTP no permitido")
                .containsEntry("detail", "El método 'DELETE' no está soportado.");

        assertThat(perform(post("/public/users").contentType(MediaType.TEXT_PLAIN).content("name"), 415)
                .get("detail").toString())
                .startsWith("El Content-Type 'text/plain");

        assertThat(perform(get("/public/no-such-route"), 404).get("detail").toString())
                .startsWith("No existe el recurso")
                .contains("no-such-route");
    }

    @Test
    void statusesWithoutAnErrorCodeKeepTheDeveloperDetail() throws Exception {
        assertThat(perform(get("/public/gone"), 410))
                .containsEntry("title", "Recurso ya no disponible")
                .containsEntry("detail", "The resource was removed")
                .containsEntry("code", "GONE");
    }

    @Test
    void fixedDetails() throws Exception {
        assertThat(perform(get("/public/failure"), 500))
                .containsEntry("detail", "Ha ocurrido un error interno");

        assertThat(perform(get("/public/duplicate"), 409))
                .containsEntry("detail", "La operación entra en conflicto con datos existentes");

        assertThat(perform(post("/public/users").contentType(MediaType.APPLICATION_JSON).content("{}"), 400))
                .containsEntry("title", "Error de validación");
    }

    @Test
    void securityFilters() throws Exception {
        assertThat(perform(get("/private/data"), 401))
                .containsEntry("title", "No autenticado")
                .containsEntry("detail", "Se requiere autenticación para acceder a este recurso");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> perform(RequestBuilder request, int status) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(status);
        return jsonMapper.readValue(response.getContentAsString(StandardCharsets.UTF_8), Map.class);
    }
}
