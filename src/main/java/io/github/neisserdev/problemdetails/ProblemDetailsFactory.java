package io.github.neisserdev.problemdetails;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Factory central de {@link ProblemDetail} (RFC 9457).
 * Garantiza que TODO error de la API salga con la misma forma:
 * {@code type}, {@code title}, {@code status}, {@code detail}, {@code instance},
 * más las extensiones {@code timestamp} y {@code code}.
 *
 * <p>Es un bean y no una clase de métodos estáticos porque la base del
 * {@code type} es configuración. Guardarla en un campo estático haría que dos
 * contextos de Spring vivos en la misma JVM (algo normal en una batería de
 * tests con caché de contextos) se pisaran la configuración entre sí.
 *
 * <h2>Base del type</h2>
 * <p>El {@code type} de cada problema es la base configurada más el código
 * ({@code https://api.ejemplo.com/problems/RESOURCE_NOT_FOUND}). Si la base no
 * termina en {@code /}, {@code :}, {@code #} ni {@code =}, se le añade una
 * barra. Por defecto es la ruta relativa {@code /problems/}, que RFC 9457
 * admite siempre que incluya la ruta completa; en producción conviene una URL
 * absoluta que sirva documentación de cada error.
 */
public class ProblemDetailsFactory {

    /** Base del {@code type} cuando no se configura otra. */
    public static final String BASE_TYPE_POR_DEFECTO = "/problems/";

    /** Extensión presente en TODA respuesta de error: instante en que se produjo. */
    public static final String PROP_TIMESTAMP = "timestamp";

    /** Extensión presente en TODA respuesta de error: código estable del problema. */
    public static final String PROP_CODE = "code";

    private static final Set<String> RESERVADAS = Set.of(PROP_TIMESTAMP, PROP_CODE);

    private final String baseType;

    /** Crea la factory con la base por defecto ({@value #BASE_TYPE_POR_DEFECTO}). */
    public ProblemDetailsFactory() {
        this(BASE_TYPE_POR_DEFECTO);
    }

    /**
     * @param baseTypeUrl base a la que se concatena el código para formar el {@code type}
     * @throws IllegalArgumentException si la base está vacía o no forma un URI válido.
     *         Falla al arrancar la aplicación y no con el primer error en producción.
     */
    public ProblemDetailsFactory(String baseTypeUrl) {
        if (baseTypeUrl == null || baseTypeUrl.isBlank()) {
            throw new IllegalArgumentException("problem-details.base-type-url no puede estar vacío");
        }
        String base = baseTypeUrl.strip();
        if (!terminaEnSeparador(base)) {
            base = base + "/";
        }
        try {
            URI.create(base + "EJEMPLO");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "problem-details.base-type-url no forma un URI válido: '" + baseTypeUrl + "'", e);
        }
        this.baseType = base;
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
     * @param tipo tipo de problema
     * @return la base más el código
     */
    public URI tipoDe(ProblemType tipo) {
        return tipoDe(tipo.getCode());
    }

    /**
     * URI del {@code type} para un código dado.
     *
     * @param codigo código del problema
     * @return la base más el código
     * @throws IllegalStateException si el código contiene caracteres no válidos en un URI
     */
    public URI tipoDe(String codigo) {
        try {
            return URI.create(baseType + codigo);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("El código de error '" + codigo
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
    public ProblemDetail crear(BusinessException ex, String requestUri) {
        return crear(ex.getProblemType(), ex.getMessage(), requestUri, ex.getProperties());
    }

    /**
     * @param tipo       tipo de problema
     * @param detalle    explicación específica de esta ocurrencia
     * @param requestUri ruta de la petición, para el {@code instance}
     * @return el problema listo para devolver
     */
    public ProblemDetail crear(ProblemType tipo, String detalle, String requestUri) {
        return crear(tipo, detalle, requestUri, Map.of());
    }

    /**
     * @param tipo       tipo de problema
     * @param detalle    explicación específica de esta ocurrencia
     * @param requestUri ruta de la petición, para el {@code instance}
     * @param extra      miembros de extensión; {@code code} y {@code timestamp}
     *                   están reservados y se ignoran si vienen aquí
     * @return el problema listo para devolver
     */
    public ProblemDetail crear(ProblemType tipo, String detalle, String requestUri, Map<String, ?> extra) {
        Objects.requireNonNull(tipo, "tipo");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(tipo.getHttpStatus(), detalle);
        pd.setType(tipoDe(tipo));
        pd.setTitle(tipo.getTitle());
        pd.setInstance(instanciaDe(requestUri));
        pd.setProperty(PROP_TIMESTAMP, Instant.now().toString());
        pd.setProperty(PROP_CODE, tipo.getCode());
        extra.forEach((clave, valor) -> {
            if (!RESERVADAS.contains(clave)) {
                pd.setProperty(clave, valor);
            }
        });
        return pd;
    }

    /**
     * Red de seguridad: completa un {@link ProblemDetail} que NO construyó esta
     * factory (los que fabrica Spring MVC para sus propios errores, o una
     * librería de terceros) para que cumpla la forma de la casa.
     *
     * <p>Es deliberadamente NO destructiva: si el problema ya trae {@code code}
     * —porque salió de {@link #crear}— se respeta tal cual, incluido su
     * {@code type} y su {@code title}. Sobrescribirlos convertiría un
     * {@code VALIDATION_ERROR} en el genérico del status 400 y rompería
     * justamente la estabilidad del código que perseguimos.
     *
     * <p>Si el status no tiene un código canónico en {@link ErrorCode} (410,
     * 402...), el código se deriva del propio status ({@code GONE},
     * {@code PAYMENT_REQUIRED}) y el título queda como la frase estándar.
     *
     * @param pd         el problema a completar (se modifica in situ)
     * @param status     status HTTP de la respuesta, para derivar el código
     * @param requestUri ruta de la petición, para el {@code instance}
     */
    public void completar(ProblemDetail pd, int status, String requestUri) {
        Map<String, Object> props = pd.getProperties();

        if (props == null || !props.containsKey(PROP_CODE)) {
            Optional<ErrorCode> canonico = ErrorCode.porStatus(status);
            if (canonico.isPresent()) {
                ErrorCode error = canonico.get();
                pd.setProperty(PROP_CODE, error.getCode());
                pd.setType(tipoDe(error));
                pd.setTitle(error.getTitle());
            } else {
                String codigo = codigoGenericoDe(status);
                pd.setProperty(PROP_CODE, codigo);
                pd.setType(tipoDe(codigo));
            }
        }
        if (props == null || !props.containsKey(PROP_TIMESTAMP)) {
            pd.setProperty(PROP_TIMESTAMP, Instant.now().toString());
        }
        if (pd.getInstance() == null) {
            pd.setInstance(instanciaDe(requestUri));
        }
    }

    /**
     * Código para un status sin representante canónico: el nombre de la
     * constante vigente de {@link HttpStatus} o {@code HTTP_<n>} si no la hay.
     */
    static String codigoGenericoDe(int status) {
        String codigo = CODIGOS_HTTP_VIGENTES.get(status);
        return codigo != null ? codigo : "HTTP_" + status;
    }

    /**
     * Nombre de cada status HTTP según las constantes NO deprecadas de
     * {@link HttpStatus}. Spring 7 deprecó varias por alinearse con RFC 9110
     * ({@code PAYLOAD_TOO_LARGE}, {@code UNPROCESSABLE_ENTITY},
     * {@code I_AM_A_TEAPOT}...). Filtrarlas aquí evita que un código expuesto
     * al cliente dependa del orden de declaración del enum de Spring o de las
     * deprecaciones que vengan en versiones futuras: un status que solo tenga
     * constantes deprecadas sale como {@code HTTP_<n>}.
     */
    private static final Map<Integer, String> CODIGOS_HTTP_VIGENTES = codigosHttpVigentes();

    private static Map<Integer, String> codigosHttpVigentes() {
        Map<Integer, String> codigos = new HashMap<>();
        for (HttpStatus status : HttpStatus.values()) {
            if (!estaDeprecado(status)) {
                codigos.putIfAbsent(status.value(), status.name());
            }
        }
        return Map.copyOf(codigos);
    }

    private static boolean estaDeprecado(HttpStatus status) {
        try {
            return HttpStatus.class.getField(status.name()).isAnnotationPresent(Deprecated.class);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    /**
     * El {@code instance} es opcional en RFC 9457. Si la ruta trae caracteres
     * que {@link URI} no acepta, se omite en lugar de lanzar una excepción
     * dentro del propio manejador de errores.
     */
    private static URI instanciaDe(String requestUri) {
        if (requestUri == null || requestUri.isEmpty()) {
            return null;
        }
        try {
            return URI.create(requestUri);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean terminaEnSeparador(String base) {
        return base.endsWith("/") || base.endsWith(":") || base.endsWith("#") || base.endsWith("=");
    }
}
