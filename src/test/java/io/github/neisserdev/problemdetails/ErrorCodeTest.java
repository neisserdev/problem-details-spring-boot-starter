package io.github.neisserdev.problemdetails;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void everyCanonicalStatusHasOneCode() {
        // toUnmodifiableMap fails if two codes share a status
        assertThat(ErrorCode.forStatus(400)).contains(ErrorCode.MALFORMED_REQUEST);
        assertThat(ErrorCode.forStatus(404)).contains(ErrorCode.ENDPOINT_NOT_FOUND);
        assertThat(ErrorCode.forStatus(413)).contains(ErrorCode.CONTENT_TOO_LARGE);
        assertThat(ErrorCode.forStatus(422)).contains(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void statusesWithoutACodeDoNotFallBackToInternalError() {
        assertThat(ErrorCode.forStatus(410)).isEmpty();
        assertThat(ErrorCode.forStatus(402)).isEmpty();
    }

    @Test
    void everyCodeIsValidInsideAUri() {
        ProblemDetailsFactory factory = new ProblemDetailsFactory();
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(factory.typeOf(code)).hasToString("/problems/" + code.name());
        }
    }

    @Test
    void standardRfc9110HttpStatusCodes() {
        assertThat(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(ErrorCode.MALFORMED_REQUEST.getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.CONTENT_TOO_LARGE.getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.CONTENT_TOO_LARGE);
        assertThat(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
