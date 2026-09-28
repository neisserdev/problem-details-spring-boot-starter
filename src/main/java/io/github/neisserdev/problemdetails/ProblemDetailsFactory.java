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
 */
public class ProblemDetailsFactory {

    /** Base del {@code type} cuando no se configura otra. */
    public static final String BASE_TYPE_POR_DEFECTO = "/problems/";

    /** Instante en que se produjo el error. */
    public static final String PROP_TIMESTAMP = "timestamp";

    /** Código estable del error. */
    public static final String PROP_CODE = "code";

    /** Prefijo de las claves de título: {@code problemDetails.title.CODIGO}. */
    public static final String PREFIJO_TITULO = "problemDetails.title.";

    /** Prefijo de las claves de detalle: {@code problemDetails.detail.CODIGO}. */
    public static final String PREFIJO_DETALLE = "problemDetails.detail.";

    private static final Set<String> RESERVADAS = Set.of(PROP_TIMESTAMP, PROP_CODE);

    private final String baseType;
    private final MessageSource mensajes;

    /** Crea la factory con la base por defecto ({@value #BASE_TYPE_POR_DEFECTO}). */
    public ProblemDetailsFactory() {
        this(BASE_TYPE_POR_DEFECTO);
    }

    /**
     * @param baseTypeUrl base a la que se concatena el código para formar el {@code type}
     * @throws IllegalArgumentException si la base está vacía o no forma un URI válido
     */
    public ProblemDetailsFactory(String baseTypeUrl) {
        this(baseTypeUrl, null);
    }

    /**
     * @param baseTypeUrl base a la que se concatena el código para formar el {@code type}
     * @param mensajes    fuente de mensajes para traducir títulos y detalles, o {@code null}
     * @throws IllegalArgumentException si la base está vacía o no forma un URI válido
     */
    public ProblemDetailsFactory(String baseTypeUrl, MessageSource mensajes) {
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
        this.mensajes = mensajes;
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
     * @param extra      miembros de extensión, se ignoran {@code code} y {@code timestamp}
     * @return el problema listo para devolver
     */
    public ProblemDetail crear(ProblemType tipo, String detalle, String requestUri, Map<String, ?> extra) {
        Objects.requireNonNull(tipo, "tipo");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(tipo.getHttpStatus(), detalle);
        pd.setType(tipoDe(tipo));
        pd.setTitle(titulo(tipo));
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
    public void completar(ProblemDetail pd, int status, String requestUri) {
        Map<String, Object> props = pd.getProperties();

        if (props == null || !props.containsKey(PROP_CODE)) {
            Optional<ErrorCode> canonico = ErrorCode.porStatus(status);
            if (canonico.isPresent()) {
                ErrorCode error = canonico.get();
                pd.setProperty(PROP_CODE, error.getCode());
                pd.setType(tipoDe(error));
                pd.setTitle(titulo(error));
            } else {
                String codigo = codigoGenericoDe(status);
                pd.setProperty(PROP_CODE, codigo);
                pd.setType(tipoDe(codigo));
                if (pd.getTitle() != null) {
                    pd.setTitle(mensaje(PREFIJO_TITULO + codigo, pd.getTitle()));
                }
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
     * Título del tipo de problema, traducido con {@code problemDetails.title.CODIGO}.
     *
     * @param tipo tipo de problema
     * @return el título traducido o el de {@link ProblemType#getTitle()}
     */
    public String titulo(ProblemType tipo) {
        return mensaje(PREFIJO_TITULO + tipo.getCode(), tipo.getTitle());
    }

    /**
     * Detalle fijo de un tipo de problema, traducido con {@code problemDetails.detail.CODIGO}.
     *
     * @param tipo       tipo de problema
     * @param porDefecto texto si no hay traducción
     * @param argumentos argumentos para los marcadores {@code {0}}, {@code {1}}...
     * @return el detalle traducido o el texto por defecto
     */
    public String detalle(ProblemType tipo, String porDefecto, Object... argumentos) {
        return mensaje(PREFIJO_DETALLE + tipo.getCode(), porDefecto, argumentos);
    }

    /**
     * Resuelve una clave en el idioma de la petición.
     *
     * <p>Con argumentos, el texto sigue el formato de {@link MessageFormat}:
     * las comillas simples literales se escriben dobles ({@code ''}).
     *
     * @param clave      clave del mensaje
     * @param porDefecto texto si la clave no existe
     * @param argumentos argumentos para los marcadores {@code {0}}, {@code {1}}...
     * @return el mensaje resuelto
     */
    public String mensaje(String clave, String porDefecto, Object... argumentos) {
        Locale locale = LocaleContextHolder.getLocale();
        if (mensajes != null) {
            return mensajes.getMessage(clave, argumentos, porDefecto, locale);
        }
        if (porDefecto == null || ObjectUtils.isEmpty(argumentos)) {
            return porDefecto;
        }
        try {
            return new MessageFormat(porDefecto, locale).format(argumentos);
        } catch (IllegalArgumentException e) {
            return porDefecto;
        }
    }

    // Nombre de la constante de HttpStatus o HTTP_<n> si no existe
    static String codigoGenericoDe(int status) {
        String codigo = CODIGOS_HTTP_VIGENTES.get(status);
        return codigo != null ? codigo : "HTTP_" + status;
    }

    // Solo constantes vigentes, Spring 7 deprecó PAYLOAD_TOO_LARGE, UNPROCESSABLE_ENTITY, etc.
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

    // instance es opcional, si la ruta no es un URI válido se omite
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
