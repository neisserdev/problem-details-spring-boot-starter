package io.github.neisserdev.problemdetails.autoconfigure;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.http.HttpStatus;

/**
 * Hints for GraalVM native images: the bundled texts and the reflective lookup
 * of the {@link HttpStatus} constants used to skip the deprecated ones.
 */
class ProblemDetailsRuntimeHints implements RuntimeHintsRegistrar {

    static final String MESSAGES = "io/github/neisserdev/problemdetails/messages";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources()
                .registerPattern(MESSAGES + ".properties")
                .registerPattern(MESSAGES + "_*.properties");
        hints.reflection().registerType(HttpStatus.class, MemberCategory.ACCESS_PUBLIC_FIELDS);
    }
}
