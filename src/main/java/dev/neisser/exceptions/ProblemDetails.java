package dev.neisser.exceptions;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.ProblemDetail;

/**
 * Factory central de {@link ProblemDetail} (RFC 9457).
 * Garantiza que TODO error de la API salga con la misma forma:
 * {@code type}, {@code title}, {@code status}, {@code detail}, {@code instance},
 * más las extensiones {@code timestamp} y {@code code}.
 *
 * <p>El prefijo del {@code type} es configurable vía la propiedad
 * {@code problem-details.base-type-url}; lo fija {@code ProblemDetailsAutoConfiguration}
 * al arrancar el contexto. Sigue siendo una utility class de métodos estáticos —como
 * el resto de la API pública— para no forzar inyección de dependencias en cada sitio
 * donde se construye un ProblemDetail a mano.
 */
public final class ProblemDetails {

    private static final String DEFAULT_BASE_TYPE = "https://neisser.dev/problems/";

    private static volatile String baseTypeUrl = DEFAULT_BASE_TYPE;

    /** Extensiones de la casa presentes en TODA respuesta de error. */
    public static final String PROP_TIMESTAMP = "timestamp";
    public static final String PROP_CODE = "code";

    private ProblemDetails() {}

    /**
     * Fija el prefijo del {@code type} para toda la JVM. Lo invoca la
     * autoconfiguración al arrancar el contexto de Spring; no debería llamarse
     * a mano salvo en tests que necesiten simular otra configuración.
     */
    public static void configurarBaseTypeUrl(String url) {
        baseTypeUrl = (url == null || url.isBlank()) ? DEFAULT_BASE_TYPE : url;
    }

    /** URI del {@code type} para un código dado. */
    public static URI typeDe(ErrorCode error) {
        return URI.create(baseTypeUrl + error.getCode());
    }

    /** Construye el problema desde una excepción de negocio, con sus propiedades extra. */
    public static ProblemDetail of(BusinessException ex, String requestURI) {
        return of(ex.getErrorCode(), ex.getMessage(), requestURI, ex.getProperties());
    }

    public static ProblemDetail of(ErrorCode error, String detail, String requestURI) {
        return of(error, detail, requestURI, Map.of());
    }

    public static ProblemDetail of(ErrorCode error, String detail, String requestURI, Map<String, Object> extra) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(error.getHttpStatus(), detail);
        pd.setType(typeDe(error));
        pd.setTitle(error.getTitle());
        pd.setInstance(URI.create(requestURI));
        pd.setProperty(PROP_TIMESTAMP, Instant.now().toString());
        pd.setProperty(PROP_CODE, error.getCode());
        extra.forEach(pd::setProperty);
        return pd;
    }

    /**
     * Red de seguridad: completa un {@link ProblemDetail} que NO construyó esta
     * factory (los que fabrica Spring MVC para sus propios errores, o una
     * librería de terceros) para que cumpla la forma de la casa.
     *
     * <p>Es deliberadamente NO destructiva: si el problema ya trae {@code code}
     * —porque salió de {@link #of}— se respeta tal cual, incluido su
     * {@code type} y su {@code title}. Sobrescribirlos convertiría un
     * {@code VALIDATION_ERROR} en el genérico del status 400 y rompería
     * justamente la estabilidad del código que perseguimos.
     *
     * @param pd         el problema a completar (se modifica in situ)
     * @param status     status HTTP de la respuesta, para derivar el código
     * @param requestURI ruta de la petición, para el {@code instance}
     */
    public static void completar(ProblemDetail pd, int status, String requestURI) {
        Map<String, Object> props = pd.getProperties();

        if (props == null || !props.containsKey(PROP_CODE)) {
            ErrorCode error = ErrorCode.porStatus(status);
            pd.setProperty(PROP_CODE, error.getCode());
            pd.setType(typeDe(error));
            pd.setTitle(error.getTitle());
        }
        if (props == null || !props.containsKey(PROP_TIMESTAMP)) {
            pd.setProperty(PROP_TIMESTAMP, Instant.now().toString());
        }
        if (pd.getInstance() == null) {
            pd.setInstance(URI.create(requestURI));
        }
    }
}
