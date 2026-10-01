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

// Handler subclass as described in the README
@SpringBootTest
@AutoConfigureMockMvc
class ProblemDetailsExtensionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void theSubclassReplacesTheStarterHandler() {
        assertThat(context.getBeansOfType(ResponseEntityExceptionHandler.class).values())
                .singleElement()
                .isInstanceOf(ExtendedExceptionHandler.class);
    }

    @Test
    void handlesItsOwnExceptions() throws Exception {
        mvc.perform(get("/extension/retired"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("The item was retired"))
                .andExpect(jsonPath("$.instance").value("/extension/retired"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void keepsTheInheritedBehavior() throws Exception {
        mvc.perform(get("/extension/items/3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.resourceId").value("3"));

        mvc.perform(get("/extension/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        mvc.perform(get("/extension/no-such-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
    }
}
