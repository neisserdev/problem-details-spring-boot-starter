package io.github.neisserdev.problemdetails;

import java.net.URI;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Construye los {@link ProblemDetail} de la API con los campos {@code type},
 * {@code title}, {@code status}, {@code detail}, {@code instance},
 * {@code timestamp} y {@code code}.
 *
 * <p>El {@code type} se forma con la base configurada y el código del error.
 * Si la base no termina en {@code /}, {@code :}, {@code #} o {@code =} se añade
 * una barra. Por defecto es {@code /problems/}.
 *
 * <p>Con un {@link MessageSource}, títulos y detalles se resuelven en el idioma
 * de la petición. Sin traducción se usa el texto por defecto.
 *
 * <p>Con un {@link TraceIdProvider}, cada problema incluye el {@code traceId}
 * de la petición si existe.
 */
public class ProblemDetailsFactory {

    /** Base del {@code type} cuando no se configura otra. */
    public static final String DEFAULT_BASE_TYPE = "/problems/";

    /** Instante en que se produjo el error. */
    public static final String PROP_TIMESTAMP = "timestamp";

    /** Código estable del error. */
    public static final String PROP_CODE = "code";

    /** Identificador de la traza de la petición, si hay trazas. */
    public static final String PROP_TRACE_ID = "traceId";

    /** Prefijo de las claves de título: {@code problemDetails.title.CODIGO}. */
    public static final String TITLE_KEY_PREFIX = "problemDetails.title.";

    /** Prefijo de las claves de detalle: {@code problemDetails.detail.CODIGO}. */
    public static final String DETAIL_KEY_PREFIX = "problemDetails.detail.";

    private static final Set<String> RESERVED_PROPERTIES = Set.of(PROP_TIMESTAMP, PROP_CODE);

    private final String baseType;
    private final MessageSource messageSource;
    private final TraceIdProvider traceIdProvider;

    /** Crea la factory con la base por defecto ({@value #DEFAULT_BASE_TYPE}). */
    public ProblemDetailsFactory() {
        this(DEFAULT_BASE_TYPE);
    }

    /**
     * @param baseTypeUrl base a la que se concatena el código para formar el {@code type}
     * @throws IllegalArgumentException si la base está vacía o no forma un URI válido
     */
    public ProblemDetailsFactory(String baseTypeUrl) {
        this(baseTypeUrl, null);
    }

    /**
     * @param baseTypeUrl   base a la que se concatena el código para formar el {@code type}
     * @param messageSource fuente de mensajes para traducir títulos y detalles, o {@code null}
     * @throws IllegalArgumentException si la base está vacía o no forma un URI válido
     */
    public ProblemDetailsFactory(String baseTypeUrl, MessageSource messageSource) {
        this(baseTypeUrl, messageSource, null);
    }

    /**
     * @param baseTypeUrl     base a la que se concatena el código para formar el {@code type}
     * @param messageSource   fuente de mensajes para traducir títulos y detalles, o {@code null}
     * @param traceIdProvider proveedor del {@code traceId}, o {@code null}
     * @throws IllegalArgumentException si la base está vacía o no forma un URI válido
     */
    public ProblemDetailsFactory(String baseTypeUrl, MessageSource messageSource, TraceIdProvider traceIdProvider) {
        if (baseTypeUrl == null || baseTypeUrl.isBlank()) {
            throw new IllegalArgumentException("problem-details.base-type-url no puede estar vacío");
        }
        String base = baseTypeUrl.strip();
        if (!endsWithSeparator(base)) {
            base = base + "/";
        }
        try {
            URI.create(base + "EJEMPLO");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "problem-details.base-type-url no forma un URI válido: '" + baseTypeUrl + "'", e);
        }
        this.baseType = base;
        this.messageSource = messageSource;
        this.traceIdProvider = traceIdProvider;
    }

    /**
     * @return la base normalizada que se antepone a cada código
     */
    public String getBaseType() {
        return baseType;
    }

    /**
     * URI del {@code type} para un tipo de problema dado.
     *
     * @param type tipo de problema
     * @return la base más el código
     */
    public URI typeOf(ProblemType type) {
        return typeOf(type.getCode());
    }

    /**
     * URI del {@code type} para un código dado.
     *
     * @param code código del problema
     * @return la base más el código
     * @throws IllegalStateException si el código contiene caracteres no válidos en un URI
     */
    public URI typeOf(String code) {
        try {
            return URI.create(baseType + code);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("El código de error '" + code
                    + "' no es válido dentro de un URI; usa MAYUSCULAS_CON_GUIONES_BAJOS", e);
        }
    }

    /**
     * Construye el problema desde una excepción de negocio, con sus propiedades extra.
     *
     * @param ex         la excepción
     * @param requestUri ruta de la petición, para el {@code instance}
     * @return el problema listo para devolver
     */
    public ProblemDetail create(BusinessException ex, String requestUri) {
        return create(ex.getProblemType(), ex.getMessage(), requestUri, ex.getProperties());
    }

    /**
     * @param type       tipo de problema
     * @param detail     explicación específica de esta ocurrencia
     * @param requestUri ruta de la petición, para el {@code instance}
     * @return el problema listo para devolver
     */
    public ProblemDetail create(ProblemType type, String detail, String requestUri) {
        return create(type, detail, requestUri, Map.of());
    }

    /**
     * @param type       tipo de problema
     * @param detail     explicación específica de esta ocurrencia
     * @param requestUri ruta de la petición, para el {@code instance}
     * @param extra      miembros de extensión, se ignoran {@code code} y {@code timestamp}
     * @return el problema listo para devolver
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
     * Completa un {@link ProblemDetail} creado fuera de esta clase, por ejemplo
     * los que genera Spring MVC. Si ya tiene {@code code} no se modifica su
     * {@code type} ni su {@code title}.
     *
     * <p>Si el status no está en {@link ErrorCode}, el código se toma del propio
     * status ({@code GONE}, {@code PAYMENT_REQUIRED}, etc.).
     *
     * @param pd         el problema a completar
     * @param status     status HTTP de la respuesta, para derivar el código
     * @param requestUri ruta de la petición, para el {@code instance}
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

    // Un fallo al obtener la traza no debe impedir responder el error
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
     * Título del tipo de problema, traducido con {@code problemDetails.title.CODIGO}.
     *
     * @param type tipo de problema
     * @return el título traducido o el de {@link ProblemType#getTitle()}
     */
    public String title(ProblemType type) {
        return message(TITLE_KEY_PREFIX + type.getCode(), type.getTitle());
    }

    /**
     * Detalle fijo de un tipo de problema, traducido con {@code problemDetails.detail.CODIGO}.
     *
     * @param type        tipo de problema
     * @param defaultText texto si no hay traducción
     * @param args        argumentos para los marcadores {@code {0}}, {@code {1}}...
     * @return el detalle traducido o el texto por defecto
     */
    public String detail(ProblemType type, String defaultText, Object... args) {
        return message(DETAIL_KEY_PREFIX + type.getCode(), defaultText, args);
    }

    /**
     * Resuelve una clave en el idioma de la petición.
     *
     * <p>Con argumentos, el texto sigue el formato de {@link MessageFormat}:
     * las comillas simples literales se escriben dobles ({@code ''}).
     *
     * @param key         clave del mensaje
     * @param defaultText texto si la clave no existe
     * @param args        argumentos para los marcadores {@code {0}}, {@code {1}}...
     * @return el mensaje resuelto
     */
    public String message(String key, String defaultText, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        if (messageSource != null) {
            return messageSource.getMessage(key, args, defaultText, locale);
        }
        if (defaultText == null || ObjectUtils.isEmpty(args)) {
            return defaultText;
        }
        try {
            return new MessageFormat(defaultText, locale).format(args);
        } catch (IllegalArgumentException e) {
            return defaultText;
        }
    }

    // Nombre de la constante de HttpStatus o HTTP_<n> si no existe
    static String genericCodeOf(int status) {
        String code = HTTP_CODES.get(status);
        return code != null ? code : "HTTP_" + status;
    }

    // Solo constantes vigentes, Spring 7 deprecó PAYLOAD_TOO_LARGE, UNPROCESSABLE_ENTITY, etc.
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

    // instance es opcional, si la ruta no es un URI válido se omite
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
