package io.github.neisserdev.problemdetails.integracion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// Traducciones en src/test/resources/i18n
@SpringBootTest(properties = {
        "spring.messages.basename=i18n.mensajes",
        "spring.messages.fallback-to-system-locale=false"
})
@AutoConfigureMockMvc
class ProblemDetailsMensajesTest {

    private static final Locale ESPANOL = Locale.forLanguageTag("es");

    @Autowired
    private MockMvc mvc;

    @Test
    void traduceElTituloSegunElIdioma() throws Exception {
        mvc.perform(get("/publico/pedidos/7").locale(Locale.ENGLISH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void sinTraduccionUsaElTextoPorDefecto() throws Exception {
        mvc.perform(get("/publico/pedidos/7").locale(ESPANOL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"));
    }

    @Test
    void traduceLosTiposPropios() throws Exception {
        mvc.perform(post("/publico/pedidos").locale(Locale.ENGLISH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Insufficient stock"));
    }

    @Test
    void traduceDetallesConArgumentos() throws Exception {
        mvc.perform(get("/publico/pagina").param("n", "abc").locale(Locale.ENGLISH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Parameter 'n' must be of type int"));
    }

    @Test
    void traduceElDetalleDeLosFiltrosDeSeguridad() throws Exception {
        mvc.perform(get("/privado/datos").locale(Locale.ENGLISH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource"));
    }

    @Test
    void traduceElDetalleDeLosConflictosDeDatos() throws Exception {
        mvc.perform(get("/publico/duplicado").locale(Locale.ENGLISH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The operation conflicts with existing data"));
    }

    @Test
    void resuelveElReasonDeResponseStatusComoClave() throws Exception {
        mvc.perform(get("/publico/licencia").locale(Locale.ENGLISH))
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.detail").value("License expired"));
    }
}
