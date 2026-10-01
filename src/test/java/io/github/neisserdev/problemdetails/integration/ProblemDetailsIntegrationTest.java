package io.github.neisserdev.problemdetails.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.neisserdev.problemdetails.GlobalExceptionHandler;

// Full Spring Boot application with the default language
@SpringBootTest(properties = {
        "problem-details.base-type-url=https://api.example.com/problems",
        "spring.mvc.problemdetails.enabled=true"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ProblemDetailsIntegrationTest {

    private static final String BASE = "https://api.example.com/problems/";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void replacesTheSpringBootProblemDetailsHandler() {
        assertThat(context.getBeansOfType(ResponseEntityExceptionHandler.class).values())
                .singleElement()
                .isInstanceOf(GlobalExceptionHandler.class);
    }

    // Business

    @Test
    void resourceNotFound() throws Exception {
        mvc.perform(get("/public/orders/7"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(BASE + "RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Order with id 7 not found"))
                .andExpect(jsonPath("$.instance").value("/public/orders/7"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.resource").value("Order"))
                .andExpect(jsonPath("$.resourceId").value("7"))
                .andExpect(jsonPath("$.properties").doesNotExist());
    }

    @Test
    void businessErrors4xxAreNotLoggedAboveDebug(CapturedOutput output) throws Exception {
        mvc.perform(get("/public/orders/7")).andExpect(status().isNotFound());

        assertThat(output).doesNotContain("Business exception [RESOURCE_NOT_FOUND]");
    }

    @Test
    void businessErrors5xxAreLoggedAsError(CapturedOutput output) throws Exception {
        mvc.perform(get("/public/payment-gateway"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"))
                .andExpect(header().string("Retry-After", "120"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(output).contains("ERROR").contains("Business exception [PAYMENT_GATEWAY_UNAVAILABLE]");
    }

    @Test
    void exceptionsWithoutHeadersDoNotAddAny() throws Exception {
        mvc.perform(post("/public/orders"))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    void databaseConstraintViolation() throws Exception {
        mvc.perform(get("/public/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(BASE + "RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.detail", not(containsString("users_email_key"))));
    }

    @Test
    void optimisticLockingConflict() throws Exception {
        mvc.perform(get("/public/concurrency"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.detail", not(containsString("another transaction"))));
    }

    @Test
    void customProblemTypeOfTheConsumer() throws Exception {
        mvc.perform(post("/public/orders"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(BASE + "INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.title").value("Insufficient stock"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.detail").value("Only 3 units left"))
                .andExpect(jsonPath("$.available").value(3));
    }

    // Validation and format

    @Test
    void bodyWithInvalidFields() throws Exception {
        mvc.perform(post("/public/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "email")))
                .andExpect(jsonPath("$.errors[0].detail").isNotEmpty());
    }

    @Test
    void parameterThatViolatesAConstraint() throws Exception {
        mvc.perform(get("/public/page").param("n", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("n"));
    }

    @Test
    void constraintInAValidatedService() throws Exception {
        mvc.perform(get("/public/service").param("n", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("n"))
                .andExpect(jsonPath("$.errors[0].detail").isNotEmpty());
    }

    @Test
    void parameterWithTheWrongType() throws Exception {
        mvc.perform(get("/public/page").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.detail", containsString("'n'")));
    }

    @Test
    void unsupportedMediaType() throws Exception {
        mvc.perform(post("/public/users").contentType(MediaType.TEXT_PLAIN).content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.type").value(BASE + "UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void noTraceIdWithoutTracing() throws Exception {
        mvc.perform(get("/public/orders/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void malformedJson() throws Exception {
        mvc.perform(post("/public/users").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    // Framework

    @Test
    void methodNotAllowed() throws Exception {
        mvc.perform(delete("/public/orders/7"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value(BASE + "METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.instance").value("/public/orders/7"));
    }

    @Test
    void unknownRoute() throws Exception {
        mvc.perform(get("/public/no-such-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
    }

    @Test
    void statusWithoutACanonicalCode() throws Exception {
        mvc.perform(get("/public/gone"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value(BASE + "GONE"))
                .andExpect(jsonPath("$.code").value("GONE"));
    }

    @Test
    void honorsResponseStatusOnCustomExceptions() throws Exception {
        mvc.perform(get("/public/subscription"))
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.code").value("PAYMENT_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("Subscription expired"));
    }

    @Test
    void unexpectedError() throws Exception {
        mvc.perform(get("/public/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An internal error occurred"));
    }

    @Test
    void theApplicationAdviceTakesPrecedence() throws Exception {
        mvc.perform(get("/public/consumer"))
                .andExpect(status().is(418))
                .andExpect(content().string("handled by the application"));
    }

    // Spring Security

    @Test
    void invalidCredentialsOnLogin() throws Exception {
        mvc.perform(post("/public/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedResourceWithoutAuthentication() throws Exception {
        mvc.perform(get("/private/data"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.type").value(BASE + "UNAUTHORIZED"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.instance").value("/private/data"))
                .andExpect(jsonPath("$.properties").doesNotExist());
    }

    @Test
    void protectedResourceWithoutTheRequiredRole() throws Exception {
        mvc.perform(get("/admin/panel").with(user("ana").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void accessDeniedInTheControllerForAnAnonymousUserIs401() throws Exception {
        mvc.perform(get("/public/admin-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void accessDeniedInTheControllerForAnAuthenticatedUserIs403() throws Exception {
        mvc.perform(get("/public/admin-only").with(user("ana")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.instance").value("/public/admin-only"));
    }
}
