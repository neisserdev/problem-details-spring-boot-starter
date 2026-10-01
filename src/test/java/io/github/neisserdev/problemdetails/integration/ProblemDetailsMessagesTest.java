package io.github.neisserdev.problemdetails.integration;

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

// Translations in src/test/resources/i18n
@SpringBootTest(properties = {
        "spring.messages.basename=i18n.messages",
        "spring.messages.fallback-to-system-locale=false"
})
@AutoConfigureMockMvc
class ProblemDetailsMessagesTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es");

    @Autowired
    private MockMvc mvc;

    @Test
    void translatesTheTitleByLanguage() throws Exception {
        mvc.perform(get("/public/orders/7").locale(Locale.ENGLISH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Nothing here"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void requestsWithoutATranslationUseTheConfiguredLanguage() throws Exception {
        mvc.perform(get("/public/orders/7").locale(SPANISH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void translatesCustomProblemTypes() throws Exception {
        mvc.perform(post("/public/orders").locale(Locale.ENGLISH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Not enough stock"));
    }

    @Test
    void translatesDetailsWithArguments() throws Exception {
        mvc.perform(get("/public/page").param("n", "abc").locale(Locale.ENGLISH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Param 'n' must be int"));
    }

    @Test
    void translatesTheDetailOfSecurityFilters() throws Exception {
        mvc.perform(get("/private/data").locale(Locale.ENGLISH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Please sign in first"));
    }

    @Test
    void translatesTheDetailOfDataConflicts() throws Exception {
        mvc.perform(get("/public/duplicate").locale(Locale.ENGLISH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("That data already exists"));
    }

    @Test
    void resolvesTheResponseStatusReasonAsAKey() throws Exception {
        mvc.perform(get("/public/license").locale(Locale.ENGLISH))
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.detail").value("License expired"));
    }
}
