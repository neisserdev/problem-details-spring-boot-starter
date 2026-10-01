package io.github.neisserdev.problemdetails.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.http.HttpStatus;

class ProblemDetailsRuntimeHintsTest {

    private final RuntimeHints hints = new RuntimeHints();

    ProblemDetailsRuntimeHintsTest() {
        new ProblemDetailsRuntimeHints().registerHints(hints, getClass().getClassLoader());
    }

    @Test
    void registersTheBundledTexts() {
        assertThat(RuntimeHintsPredicates.resource()
                .forResource("io/github/neisserdev/problemdetails/messages.properties")).accepts(hints);
        assertThat(RuntimeHintsPredicates.resource()
                .forResource("io/github/neisserdev/problemdetails/messages_es.properties")).accepts(hints);
    }

    @Test
    void registersTheHttpStatusConstants() {
        assertThat(RuntimeHintsPredicates.reflection().onFieldAccess(HttpStatus.class, "PAYLOAD_TOO_LARGE"))
                .accepts(hints);
    }

    @Test
    void theAutoConfigurationImportsTheHints() {
        assertThat(ProblemDetailsAutoConfiguration.class.getAnnotation(ImportRuntimeHints.class).value())
                .containsExactly(ProblemDetailsRuntimeHints.class);
    }
}
