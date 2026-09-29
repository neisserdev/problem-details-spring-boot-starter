package io.github.neisserdev.problemdetails.integracion;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Tracer simulado, el traceId debe salir en errores de MVC, del framework y de los filtros
@SpringBootTest
@AutoConfigureMockMvc
class ProblemDetailsTrazaTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Autowired
    private MockMvc mvc;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private Tracer tracer;

    @BeforeEach
    void configurarTraza() {
        when(tracer.currentSpan().context().traceId()).thenReturn(TRACE_ID);
    }

    @Test
    void errorDeNegocio() throws Exception {
        mvc.perform(get("/publico/pedidos/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void errorDelFramework() throws Exception {
        mvc.perform(delete("/publico/pedidos/7"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }

    @Test
    void errorEnLosFiltrosDeSeguridad() throws Exception {
        mvc.perform(get("/privado/datos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.traceId").value(TRACE_ID));
    }
}
