package io.github.neisserdev.problemdetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

class ProblemDetailsFactoryTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static final Locale SPANISH = Locale.forLanguageTag("es");

    enum TestErrors implements ProblemType {
        INSUFFICIENT_STOCK;

        @Override
        public String getCode() {
            return name();
        }

        @Override
        public String getTitle() {
            return "Insufficient stock";
        }

        @Override
        public HttpStatusCode getHttpStatus() {
            return HttpStatus.CONFLICT;
        }
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void usesTheDefaultBase() {
        assertThat(new ProblemDetailsFactory().typeOf(ErrorCode.RESOURCE_NOT_FOUND))
                .isEqualTo(URI.create("/problems/RESOURCE_NOT_FOUND"));
    }

    @Test
    void appendsTheTrailingSlashWhenMissing() {
        assertThat(new ProblemDetailsFactory(" https://api.example.com/problems ").getBaseType())
                .isEqualTo("https://api.example.com/problems/");
    }

    @Test
    void keepsBasesThatAlreadyEndWithASeparator() {
        assertThat(new ProblemDetailsFactory("urn:problem:").getBaseType()).isEqualTo("urn:problem:");
        assertThat(new ProblemDetailsFactory("https://docs.example.com/errors#").getBaseType())
                .isEqualTo("https://docs.example.com/errors#");
    }

    @Test
    void rejectsEmptyBasesAndInvalidUris() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProblemDetailsFactory((String) null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProblemDetailsFactory("  "));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProblemDetailsFactory("https://api example.com/"));
    }

    @Test
    void createsTheProblemWithTheExpectedShape() {
        ProblemDetailsFactory factory = new ProblemDetailsFactory("https://api.example.com/problems/");

        ProblemDetail pd = factory.create(ErrorCode.RESOURCE_NOT_FOUND, "Order 7 was not found",
                "/orders/7", Map.of("resource", "Order", "code", "OVERRIDDEN", "timestamp", "yesterday"));

        assertThat(pd.getStatus()).isEqualTo(404);
        assertThat(pd.getType()).isEqualTo(URI.create("https://api.example.com/problems/RESOURCE_NOT_FOUND"));
        assertThat(pd.getTitle()).isEqualTo("Resource not found");
        assertThat(pd.getDetail()).isEqualTo("Order 7 was not found");
        assertThat(pd.getInstance()).isEqualTo(URI.create("/orders/7"));
        assertThat(pd.getProperties())
                .containsEntry("code", "RESOURCE_NOT_FOUND")
                .containsEntry("resource", "Order")
                .containsKey("timestamp");
        assertThat(pd.getProperties().get("timestamp")).isNotEqualTo("yesterday");
    }

    @Test
    void createsTheProblemFromABusinessException() {
        ProblemDetail pd = new ProblemDetailsFactory().create(new ResourceNotFoundException("Order", 7), "/orders/7");

        assertThat(pd.getDetail()).isEqualTo("Order with id 7 not found");
        assertThat(pd.getProperties())
                .containsEntry("resource", "Order")
                .containsEntry("resourceId", "7");
    }

    @Test
    void acceptsCustomProblemTypes() {
        ProblemDetail pd = new ProblemDetailsFactory()
                .create(TestErrors.INSUFFICIENT_STOCK, "Only 3 units left", "/orders");

        assertThat(pd.getStatus()).isEqualTo(409);
        assertThat(pd.getType()).hasToString("/problems/INSUFFICIENT_STOCK");
        assertThat(pd.getTitle()).isEqualTo("Insufficient stock");
        assertThat(pd.getProperties()).containsEntry("code", "INSUFFICIENT_STOCK");
    }

    @Test
    void omitsTheInstanceWhenThePathIsNotAValidUri() {
        ProblemDetail pd = new ProblemDetailsFactory().create(ErrorCode.MALFORMED_REQUEST, "x", "/path with spaces");

        assertThat(pd.getInstance()).isNull();
    }

    @Test
    void rejectsCodesThatDoNotFitInAUri() {
        assertThatIllegalStateException().isThrownBy(() -> new ProblemDetailsFactory().typeOf("CODE WITH SPACES"));
    }

    @Test
    void completesFrameworkProblemsWithTheirCanonicalCode() {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);

        new ProblemDetailsFactory().complete(pd, 405, "/orders/1");

        assertThat(pd.getProperties()).containsEntry("code", "METHOD_NOT_ALLOWED").containsKey("timestamp");
        assertThat(pd.getType()).hasToString("/problems/METHOD_NOT_ALLOWED");
        assertThat(pd.getTitle()).isEqualTo("Method not allowed");
        assertThat(pd.getInstance()).hasToString("/orders/1");
    }

    @Test
    void derivesTheCodeOfStatusesWithoutACanonicalCode() {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.GONE);

        new ProblemDetailsFactory().complete(pd, 410, "/resource");

        assertThat(pd.getProperties()).containsEntry("code", "GONE");
        assertThat(pd.getType()).hasToString("/problems/GONE");
        assertThat(ProblemDetailsFactory.genericCodeOf(599)).isEqualTo("HTTP_599");
    }

    @Test
    void neverDerivesCodesFromDeprecatedSpringConstants() {
        // Deprecated in Spring 7
        assertThat(ProblemDetailsFactory.genericCodeOf(413)).isEqualTo("CONTENT_TOO_LARGE");
        assertThat(ProblemDetailsFactory.genericCodeOf(422)).isEqualTo("UNPROCESSABLE_CONTENT");
        assertThat(ProblemDetailsFactory.genericCodeOf(418)).isEqualTo("HTTP_418");
        assertThat(ProblemDetailsFactory.genericCodeOf(402)).isEqualTo("PAYMENT_REQUIRED");
    }

    @Test
    void doesNotOverrideAProblemThatAlreadyHasACode() {
        ProblemDetailsFactory factory = new ProblemDetailsFactory();
        ProblemDetail pd = factory.create(ErrorCode.VALIDATION_ERROR, "x", "/a");

        factory.complete(pd, 400, "/b");

        assertThat(pd.getProperties()).containsEntry("code", "VALIDATION_ERROR");
        assertThat(pd.getTitle()).isEqualTo("Validation error");
        assertThat(pd.getInstance()).hasToString("/a");
    }

    @Test
    void translatesTitlesAndDetailsWithTheMessageSource() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("problemDetails.title.RESOURCE_NOT_FOUND", Locale.ENGLISH, "Nothing here");
        messages.addMessage("problemDetails.detail.INTERNAL_ERROR", Locale.ENGLISH, "Something broke");
        messages.addMessage("problemDetails.detail.TYPE_MISMATCH.parameter", Locale.ENGLISH,
                "Param ''{0}'' must be {1}");
        ProblemDetailsFactory factory = new ProblemDetailsFactory("/problems/", messages);
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(factory.create(ErrorCode.RESOURCE_NOT_FOUND, "x", "/a").getTitle()).isEqualTo("Nothing here");
        assertThat(factory.detail(ErrorCode.INTERNAL_ERROR, "Default detail")).isEqualTo("Something broke");
        assertThat(factory.message("problemDetails.detail.TYPE_MISMATCH.parameter",
                "Default text {0} {1}", "n", "int"))
                .isEqualTo("Param 'n' must be int");
    }

    @Test
    void translatesTheTitleOfFrameworkProblems() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("problemDetails.title.METHOD_NOT_ALLOWED", Locale.ENGLISH, "Wrong method");
        messages.addMessage("problemDetails.title.GONE", Locale.ENGLISH, "No longer available");
        ProblemDetailsFactory factory = new ProblemDetailsFactory("/problems/", messages);
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        ProblemDetail methodNotAllowed = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        factory.complete(methodNotAllowed, 405, "/a");
        ProblemDetail gone = ProblemDetail.forStatus(HttpStatus.GONE);
        factory.complete(gone, 410, "/b");

        assertThat(methodNotAllowed.getTitle()).isEqualTo("Wrong method");
        assertThat(gone.getTitle()).isEqualTo("No longer available");
    }

    @Test
    void usesTheBundledTextsWithoutAnApplicationTranslation() {
        ProblemDetailsFactory factory = new ProblemDetailsFactory("/problems/", new StaticMessageSource());
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(factory.title(ErrorCode.RESOURCE_NOT_FOUND)).isEqualTo("Resource not found");
        assertThat(factory.detail(ErrorCode.INTERNAL_ERROR, "Fallback")).isEqualTo("An internal error occurred");
        assertThat(factory.message("missing.key", "Parameter ''{0}'' must be of type {1}", "n", "int"))
                .isEqualTo("Parameter 'n' must be of type int");
    }

    @Test
    void usesTheConfiguredLanguageForTheBundledTexts() {
        ProblemDetailsFactory factory = new ProblemDetailsFactory("/problems/", null, null, SPANISH);

        assertThat(factory.getLanguage()).isEqualTo(SPANISH);
        assertThat(factory.title(ErrorCode.RESOURCE_NOT_FOUND)).isEqualTo("Recurso no encontrado");
        assertThat(factory.detail(ErrorCode.INTERNAL_ERROR, "Fallback")).isEqualTo("Ha ocurrido un error interno");
        assertThat(factory.message("problemDetails.detail.TYPE_MISMATCH.parameter", "Fallback", "n", "int"))
                .isEqualTo("El parámetro 'n' debe ser de tipo int");
        assertThat(factory.create(new ResourceNotFoundException("Pedido", 7), "/a").getDetail())
                .isEqualTo("Pedido con id 7 no encontrado");

        ProblemDetail gone = ProblemDetail.forStatus(HttpStatus.GONE);
        factory.complete(gone, 410, "/a");
        assertThat(gone.getTitle()).isEqualTo("Recurso ya no disponible");
    }

    @Test
    void applicationTranslationsTakePrecedenceOverTheLanguage() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("problemDetails.title.RESOURCE_NOT_FOUND", Locale.ENGLISH, "Nothing here");
        ProblemDetailsFactory factory = new ProblemDetailsFactory("/problems/", messages, null, SPANISH);

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(factory.title(ErrorCode.RESOURCE_NOT_FOUND)).isEqualTo("Nothing here");

        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertThat(factory.title(ErrorCode.RESOURCE_NOT_FOUND)).isEqualTo("Recurso no encontrado");
    }

    @Test
    void languagesWithoutBundledTextsFallBackToEnglish() {
        ProblemDetailsFactory factory = new ProblemDetailsFactory("/problems/", null, null, Locale.GERMAN);

        assertThat(factory.title(ErrorCode.RESOURCE_NOT_FOUND)).isEqualTo("Resource not found");
        assertThat(new ProblemDetailsFactory("/problems/", null, null, null).getLanguage()).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void doesNotGroupTheDigitsOfNumericIds() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Order", 1234567L);

        assertThat(new ProblemDetailsFactory().create(ex, "/a").getDetail())
                .isEqualTo("Order with id 1234567 not found");
        assertThat(new ProblemDetailsFactory("/problems/", null, null, SPANISH).create(ex, "/a").getDetail())
                .isEqualTo("Order con id 1234567 no encontrado");
    }

    @Test
    void businessExceptionsCanTranslateTheirDetail() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("store.insufficientStock", Locale.ENGLISH, "Only {0} left");
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        ProblemDetail translated = new ProblemDetailsFactory("/problems/", messages)
                .create(new InsufficientStockException(3), "/orders");
        ProblemDetail untranslated = new ProblemDetailsFactory()
                .create(new InsufficientStockException(3), "/orders");

        assertThat(translated.getDetail()).isEqualTo("Only 3 left");
        assertThat(untranslated.getDetail()).isEqualTo("Can't sell, only 3 left");
    }

    static class InsufficientStockException extends BusinessException {

        private static final long serialVersionUID = 1L;

        private final int available;

        InsufficientStockException(int available) {
            super(TestErrors.INSUFFICIENT_STOCK, "Can't sell, only " + available + " left");
            this.available = available;
        }

        @Override
        public String getDetailMessageCode() {
            return "store.insufficientStock";
        }

        @Override
        public Object[] getDetailMessageArguments() {
            return new Object[] {available};
        }
    }

    @Test
    void formatsArgumentsWithoutAMessageSource() {
        ProblemDetailsFactory factory = new ProblemDetailsFactory();

        assertThat(factory.message("key", "Parameter ''{0}'' must be of type {1}", "n", "int"))
                .isEqualTo("Parameter 'n' must be of type int");
        assertThat(factory.message("key", "Without arguments the 'quotes' stay as they are"))
                .isEqualTo("Without arguments the 'quotes' stay as they are");
    }

    @Test
    void includesTheTraceIdWhenThereIsATrace() {
        ProblemDetailsFactory factory = new ProblemDetailsFactory("/problems/", null, () -> TRACE_ID);

        ProblemDetail created = factory.create(ErrorCode.RESOURCE_NOT_FOUND, "x", "/a");
        ProblemDetail completed = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        factory.complete(completed, 405, "/b");

        assertThat(created.getProperties()).containsEntry("traceId", TRACE_ID);
        assertThat(completed.getProperties()).containsEntry("traceId", TRACE_ID);
    }

    @Test
    void omitsTheTraceIdWhenThereIsNoTraceOrTheProviderFails() {
        assertThat(hasNoTraceId(new ProblemDetailsFactory())).isTrue();
        assertThat(hasNoTraceId(new ProblemDetailsFactory("/problems/", null, () -> null))).isTrue();
        assertThat(hasNoTraceId(new ProblemDetailsFactory("/problems/", null, () -> ""))).isTrue();
        assertThat(hasNoTraceId(new ProblemDetailsFactory("/problems/", null, () -> {
            throw new IllegalStateException("no trace");
        }))).isTrue();
    }

    private static boolean hasNoTraceId(ProblemDetailsFactory factory) {
        return !factory.create(ErrorCode.INTERNAL_ERROR, "x", "/a").getProperties().containsKey("traceId");
    }
}
