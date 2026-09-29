package io.github.neisserdev.problemdetails.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Subclase del manejador como se describe en el README
@SpringBootTest
@AutoConfigureMockMvc
class ProblemDetailsExtensionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext contexto;

    @Test
    void laSubclaseSustituyeAlManejadorDelStarter() {
        assertThat(contexto.getBeansOfType(ResponseEntityExceptionHandler.class).values())
                .singleElement()
                .isInstanceOf(ManejadorExtendido.class);
    }

    @Test
    void atiendeSusPropiasExcepciones() throws Exception {
        mvc.perform(get("/extension/retirado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("El articulo se retiro"))
                .andExpect(jsonPath("$.instance").value("/extension/retirado"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void conservaElComportamientoHeredado() throws Exception {
        mvc.perform(get("/extension/articulos/3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.resourceId").value("3"));

        mvc.perform(get("/extension/fallo"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        mvc.perform(get("/extension/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
    }
}
