package io.github.neisserdev.problemdetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.Test;

// Consistency of the bundled texts
class DefaultMessagesTest {

    private static final String BUNDLE = "io/github/neisserdev/problemdetails/messages";
    private static final String SPRING_PREFIX = "problemDetail.";

    @Test
    void errorCodeTitlesMatchTheEnglishTexts() throws IOException {
        Properties english = load(BUNDLE + ".properties");

        for (ErrorCode code : ErrorCode.values()) {
            assertThat(english.getProperty(ProblemDetailsFactory.TITLE_KEY_PREFIX + code.name()))
                    .as(code.name())
                    .isEqualTo(code.getTitle());
        }
    }

    @Test
    void theSpanishTextsCoverEveryEnglishKey() throws IOException {
        Properties english = load(BUNDLE + ".properties");
        Properties spanish = load(BUNDLE + "_es.properties");

        assertThat(spanish.stringPropertyNames()).containsAll(english.stringPropertyNames());
    }

    @Test
    void theSpanishTextsHaveATitleForEveryStatusWithoutAnErrorCode() throws IOException {
        Properties spanish = load(BUNDLE + "_es.properties");

        for (int status = 400; status < 600; status++) {
            String code = ProblemDetailsFactory.genericCodeOf(status);
            if (ErrorCode.forStatus(status).isEmpty() && !code.startsWith("HTTP_")) {
                assertThat(spanish).as(code).containsKey(ProblemDetailsFactory.TITLE_KEY_PREFIX + code);
            }
        }
    }

    @Test
    void springDetailKeysPointToExistingExceptions() throws IOException {
        Properties spanish = load(BUNDLE + "_es.properties");

        for (String key : spanish.stringPropertyNames()) {
            if (key.startsWith(SPRING_PREFIX)) {
                String className = key.substring(SPRING_PREFIX.length()).replace(".parseError", "");
                assertThatCode(() -> Class.forName(className, false, getClass().getClassLoader()))
                        .as(key)
                        .doesNotThrowAnyException();
            }
        }
    }

    private Properties load(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
