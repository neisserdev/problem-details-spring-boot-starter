package io.github.neisserdev.problemdetails;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Builds the {@link ProblemDetail} responses of the API with the {@code type},
 * {@code title}, {@code status}, {@code detail}, {@code instance},
 * {@code timestamp} and {@code code} fields.
 *
 * <p>The {@code type} is made of the configured base and the error code. A
 * slash is appended when the base does not end with {@code /}, {@code :},
 * {@code #} or {@code =}. The default base is {@code /problems/}.
 *
 * <p>Titles and details are resolved in this order: the application
 * {@link MessageSource} in the language of the request, then the texts bundled
 * with the starter in the configured language (English by default).
 *
 * <p>With a {@link TraceIdProvider}, every problem includes the {@code traceId}
 * of the request when there is one.
 */
public class ProblemDetailsFactory {

    /** Base of the {@code type} when no other is configured. */
    public static final String DEFAULT_BASE_TYPE = "/problems/";

    /** Instant when the error happened. */
    public static final String PROP_TIMESTAMP = "timestamp";

    /** Stable error code. */
    public static final String PROP_CODE = "code";

    /** Trace identifier of the request, when tracing is available. */
    public static final String PROP_TRACE_ID = "traceId";

    /** Prefix of the title keys: {@code problemDetails.title.CODE}. */
    public static final String TITLE_KEY_PREFIX = "problemDetails.title.";

    /** Prefix of the detail keys: {@code problemDetails.detail.CODE}. */
    public static final String DETAIL_KEY_PREFIX = "problemDetails.detail.";

    private static final Set<String> RESERVED_PROPERTIES = Set.of(PROP_TIMESTAMP, PROP_CODE);

    // Not a bean, so it never replaces the application MessageSource
    private static final ResourceBundleMessageSource DEFAULT_MESSAGES = defaultMessages();

    private final String baseType;
    private final MessageSource messageSource;
    private final TraceIdProvider traceIdProvider;
    private final Locale language;

    /** Creates the factory with the default base ({@value #DEFAULT_BASE_TYPE}). */
    public ProblemDetailsFactory() {
        this(DEFAULT_BASE_TYPE);
    }

    /**
     * @param baseTypeUrl base of the {@code type}, relative or absolute
     * @throws IllegalArgumentException if it is empty or does not form a valid URI
     */
    public ProblemDetailsFactory(String baseTypeUrl) {
        this(baseTypeUrl, null);
    }

    /**
     * @param baseTypeUrl   base of the {@code type}, relative or absolute
     * @param messageSource source of the translations, may be {@code null}
     */
    public ProblemDetailsFactory(String baseTypeUrl, MessageSource messageSource) {
        this(baseTypeUrl, messageSource, null);
    }

    /**
     * @param baseTypeUrl     base of the {@code type}, relative or absolute
     * @param messageSource   source of the translations, may be {@code null}
     * @param traceIdProvider source of the {@code traceId}, may be {@code null}
     */
    public ProblemDetailsFactory(String baseTypeUrl, MessageSource messageSource, TraceIdProvider traceIdProvider) {
        this(baseTypeUrl, messageSource, traceIdProvider, Locale.ENGLISH);
    }

    /**
     * @param baseTypeUrl     base of the {@code type}, relative or absolute
     * @param messageSource   source of the translations, may be {@code null}
     * @param traceIdProvider source of the {@code traceId}, may be {@code null}
     * @param language        language of the default texts, English when it is not bundled
     * @throws IllegalArgumentException if the base is empty or does not form a valid URI
     */
    public ProblemDetailsFactory(String baseTypeUrl, MessageSource messageSource,
                                 TraceIdProvider traceIdProvider, Locale language) {
        if (baseTypeUrl == null || baseTypeUrl.isBlank()) {
            throw new IllegalArgumentException("problem-details.base-type-url must not be empty");
        }
        String base = baseTypeUrl.strip();
        if (!endsWithSeparator(base)) {
            base = base + "/";
        }
        try {
            URI.create(base + "EXAMPLE");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "problem-details.base-type-url is not a valid URI: '" + baseTypeUrl + "'", e);
        }
        this.baseType = base;
        this.messageSource = messageSource;
        this.traceIdProvider = traceIdProvider;
        this.language = language != null ? language : Locale.ENGLISH;
    }

    /**
     * @return the base of the {@code type}, always ending with a separator
     */
    public String getBaseType() {
        return baseType;
    }

    /**
     * @return the language of the default texts
     */
    public Locale getLanguage() {
        return language;
    }

    /**
     * @param type problem type
     * @return the {@code type} URI of the problem
     */
    public URI typeOf(ProblemType type) {
        return typeOf(type.getCode());
    }

    /**
     * @param code error code
     * @return the {@code type} URI for the code
     * @throws IllegalStateException if the code is not valid inside a URI
     */
    public URI typeOf(String code) {
        try {
            return URI.create(baseType + code);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The error code '" + code
                    + "' is not valid inside a URI; use UPPER_CASE_WITH_UNDERSCORES", e);
        }
    }

    /**
     * Problem of a business exception, with its properties as extension members.
     *
     * @param ex         business exception
     * @param requestUri path of the request, for the {@code instance}
     * @return the problem ready to be returned
     */
    public ProblemDetail create(BusinessException ex, String requestUri) {
        String detail = ex.getMessage();
        String code = ex.getDetailMessageCode();
        if (code != null) {
            String translated = resolve(code, ex.getDetailMessageArguments());
            if (translated != null) {
                detail = translated;
            }
        }
        return create(ex.getProblemType(), detail, requestUri, ex.getProperties());
    }

    /**
     * @param type       problem type
     * @param detail     specific explanation of this occurrence
     * @param requestUri path of the request, for the {@code instance}
     * @return the problem ready to be returned
     */
    public ProblemDetail create(ProblemType type, String detail, String requestUri) {
        return create(type, detail, requestUri, Map.of());
    }

    /**
     * @param type       problem type
     * @param detail     specific explanation of this occurrence
     * @param requestUri path of the request, for the {@code instance}
     * @param extra      extension members; {@code code} and {@code timestamp} are ignored
     * @return the problem ready to be returned
     */
    public ProblemDetail create(ProblemType type, String detail, String requestUri, Map<String, ?> extra) {
        Objects.requireNonNull(type, "type");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(type.getHttpStatus(), detail);
        pd.setType(typeOf(type));
        pd.setTitle(title(type));
        pd.setInstance(toInstance(requestUri));
        pd.setProperty(PROP_TIMESTAMP, Instant.now().toString());
        pd.setProperty(PROP_CODE, type.getCode());
        if (extra != null) {
            extra.forEach((key, value) -> {
                if (!RESERVED_PROPERTIES.contains(key)) {
                    pd.setProperty(key, value);
                }
            });
        }
        addTraceId(pd);
        return pd;
    }

    /**
     * Completes a problem built by Spring MVC with {@code code}, {@code type},
     * {@code timestamp}, {@code instance} and {@code traceId}. A problem that
     * already has a {@code code} keeps its values.
     *
     * <p>The code is the one of {@link ErrorCode#forStatus(int)}, or the name
     * of the HTTP status when the catalog has none.
     *
     * @param pd         the problem to complete
     * @param status     HTTP status of the response
     * @param requestUri path of the request, for the {@code instance}
     */
    public void complete(ProblemDetail pd, int status, String requestUri) {
        Map<String, Object> props = pd.getProperties();

        if (props == null || !props.containsKey(PROP_CODE)) {
            Optional<ErrorCode> canonical = ErrorCode.forStatus(status);
            if (canonical.isPresent()) {
                ErrorCode error = canonical.get();
                pd.setProperty(PROP_CODE, error.getCode());
                pd.setType(typeOf(error));
                pd.setTitle(title(error));
            } else {
                String code = genericCodeOf(status);
                pd.setProperty(PROP_CODE, code);
                pd.setType(typeOf(code));
                if (pd.getTitle() != null) {
                    pd.setTitle(message(TITLE_KEY_PREFIX + code, pd.getTitle()));
                }
            }
        }
        if (props == null || !props.containsKey(PROP_TIMESTAMP)) {
            pd.setProperty(PROP_TIMESTAMP, Instant.now().toString());
        }
        if (pd.getInstance() == null) {
            pd.setInstance(toInstance(requestUri));
        }
        Map<String, Object> existing = pd.getProperties();
        if (existing == null || !existing.containsKey(PROP_TRACE_ID)) {
            addTraceId(pd);
        }
    }

    // A failure while reading the trace must not prevent the error response
    private void addTraceId(ProblemDetail pd) {
        if (traceIdProvider == null) {
            return;
        }
        String traceId;
        try {
            traceId = traceIdProvider.currentTraceId();
        } catch (RuntimeException e) {
            return;
        }
        if (StringUtils.hasText(traceId)) {
            pd.setProperty(PROP_TRACE_ID, traceId);
        }
    }

    /**
     * Title of the problem type, translated with {@code problemDetails.title.CODE}.
     *
     * @param type problem type
     * @return the translated title, or the one of {@link ProblemType#getTitle()}
     */
    public String title(ProblemType type) {
        return message(TITLE_KEY_PREFIX + type.getCode(), type.getTitle());
    }

    /**
     * Fixed detail of a problem type, translated with {@code problemDetails.detail.CODE}.
     *
     * @param type        problem type
     * @param defaultText text when there is no translation
     * @param args        arguments for the {@code {0}}, {@code {1}}... placeholders
     * @return the translated detail or the default text
     */
    public String detail(ProblemType type, String defaultText, Object... args) {
        return message(DETAIL_KEY_PREFIX + type.getCode(), defaultText, args);
    }

    /**
     * Resolves a key in the language of the request, then in the configured
     * language with the bundled texts.
     *
     * <p>With arguments, the text follows the {@link MessageFormat} syntax:
     * literal single quotes are written twice ({@code ''}).
     *
     * @param key         message key
     * @param defaultText text when the key does not exist
     * @param args        arguments for the {@code {0}}, {@code {1}}... placeholders
     * @return the resolved text
     */
    public String message(String key, String defaultText, Object... args) {
        String resolved = resolve(key, args);
        if (resolved != null) {
            return resolved;
        }
        if (defaultText == null || ObjectUtils.isEmpty(args)) {
            return defaultText;
        }
        try {
            return new MessageFormat(defaultText, LocaleContextHolder.getLocale()).format(args);
        } catch (IllegalArgumentException e) {
            return defaultText;
        }
    }

    // Application translation first, then the bundled one; null if neither exists
    String resolve(String key, Object... args) {
        if (key == null) {
            return null;
        }
        if (messageSource != null) {
            String translated = messageSource.getMessage(key, args, null, LocaleContextHolder.getLocale());
            // With use-code-as-default-message the key itself comes back
            if (translated != null && !translated.equals(key)) {
                return translated;
            }
        }
        return DEFAULT_MESSAGES.getMessage(key, args, null, language);
    }

    // Name of the HttpStatus constant, or HTTP_<n> when there is none
    static String genericCodeOf(int status) {
        String code = HTTP_CODES.get(status);
        return code != null ? code : "HTTP_" + status;
    }

    // Current constants only, Spring 7 deprecated PAYLOAD_TOO_LARGE, UNPROCESSABLE_ENTITY, etc.
    private static final Map<Integer, String> HTTP_CODES = currentHttpCodes();

    private static Map<Integer, String> currentHttpCodes() {
        Map<Integer, String> codes = new HashMap<>();
        for (HttpStatus status : HttpStatus.values()) {
            if (!isDeprecated(status)) {
                codes.putIfAbsent(status.value(), status.name());
            }
        }
        return Map.copyOf(codes);
    }

    private static boolean isDeprecated(HttpStatus status) {
        try {
            return HttpStatus.class.getField(status.name()).isAnnotationPresent(Deprecated.class);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private static ResourceBundleMessageSource defaultMessages() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("io.github.neisserdev.problemdetails.messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messages.setFallbackToSystemLocale(false);
        messages.setBundleClassLoader(ProblemDetailsFactory.class.getClassLoader());
        return messages;
    }

    // instance is optional, it is omitted when the path is not a valid URI
    private static URI toInstance(String requestUri) {
        if (requestUri == null || requestUri.isEmpty()) {
            return null;
        }
        try {
            return URI.create(requestUri);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean endsWithSeparator(String base) {
        return base.endsWith("/") || base.endsWith(":") || base.endsWith("#") || base.endsWith("=");
    }
}
