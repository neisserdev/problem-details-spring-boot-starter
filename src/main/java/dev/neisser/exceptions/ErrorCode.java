package dev.neisser.exceptions;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;

/**
 * Fuente única de verdad de los errores de la API.
 * Cada valor ata un código estable (usado en el {@code type} del ProblemDetail
 * y expuesto como propiedad {@code code}), un título legible y un status HTTP.
 *
 * <p>Al añadir un error de negocio nuevo: se agrega un valor aquí y se crea su
 * excepción extendiendo {@link BusinessException}. El handler no se toca.
 *
 * <p>Los códigos de la sección "Autenticación" son agnósticos del dominio: los
 * usa el módulo auth reutilizable y viajan con este enum a cualquier proyecto.
 *
 * <p>La sección "Protocolo HTTP" existe para que los errores que genera el
 * propio Spring MVC (método no permitido, media type no soportado, ruta
 * inexistente...) también salgan con un {@code code} SEMÁNTICO y estable, en vez
 * de con el nombre del enum {@code HttpStatus}. Así un cliente puede hacer
 * switch sobre {@code code} con una única granularidad, venga el error de donde
 * venga. Ver {@link #porStatus(int)}.
 */
public enum ErrorCode {

    // --- Validación / formato de la petición ---
    /**
     * JSON sintácticamente válido cuyos datos no cumplen las reglas de validación
     * (email inválido, password corta, campo vacío...). 422, no 400: la petición
     * SÍ se pudo interpretar; lo que falla es el contenido, no la sintaxis.
     */
    VALIDATION_ERROR("Error de validación", HttpStatus.UNPROCESSABLE_CONTENT),
    /** JSON malformado o ilegible: aquí sí falla la sintaxis, por eso se queda en 400. */
    MALFORMED_REQUEST("Petición mal formada", HttpStatus.BAD_REQUEST),
    TYPE_MISMATCH("Parámetro con tipo incorrecto", HttpStatus.BAD_REQUEST),
    CONSTRAINT_VIOLATION("Restricción no satisfecha", HttpStatus.BAD_REQUEST),

    // --- Autenticación / autorización (usados por el módulo auth) ---
    UNAUTHORIZED("No autenticado", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("Credenciales inválidas", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("Refresh token inválido", HttpStatus.UNAUTHORIZED),
    ACCOUNT_UNAVAILABLE("Cuenta no disponible", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("Acceso denegado", HttpStatus.FORBIDDEN),
    /** Petición con cookie de sesión desde un origen no autorizado (defensa CSRF). */
    ORIGIN_NOT_ALLOWED("Origen no autorizado", HttpStatus.FORBIDDEN),

    // --- Negocio ---
    RESOURCE_NOT_FOUND("Recurso no encontrado", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_REGISTERED("Email ya registrado", HttpStatus.CONFLICT),
    RESOURCE_CONFLICT("Conflicto con el estado actual del recurso", HttpStatus.CONFLICT),
    BUSINESS_RULE_VIOLATION("Regla de negocio no satisfecha", HttpStatus.UNPROCESSABLE_CONTENT),

    // --- Protocolo HTTP (errores que genera Spring MVC, no el dominio) ---
    ENDPOINT_NOT_FOUND("Endpoint no encontrado", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("Método HTTP no permitido", HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE("Formato de respuesta no aceptable", HttpStatus.NOT_ACCEPTABLE),
    UNSUPPORTED_MEDIA_TYPE("Tipo de contenido no soportado", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    CONTENT_TOO_LARGE("Cuerpo de la petición demasiado grande", HttpStatus.CONTENT_TOO_LARGE),
    TOO_MANY_REQUESTS("Demasiadas peticiones", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE("Servicio no disponible", HttpStatus.SERVICE_UNAVAILABLE),

    // --- Genérico ---
    INTERNAL_ERROR("Error interno del servidor", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String title;
    private final HttpStatus httpStatus;

    ErrorCode(String title, HttpStatus httpStatus) {
        this.title = title;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return name(); }
    public String getTitle() { return title; }
    public HttpStatus getHttpStatus() { return httpStatus; }

    /**
     * Códigos elegidos como representantes canónicos de cada status HTTP, para
     * los errores que NO nacen del dominio (los genera Spring). Varios códigos
     * comparten status (p. ej. cuatro distintos son 400), así que el mapa fija
     * explícitamente cuál representa a cada uno; no se deriva por orden de
     * declaración, que sería frágil ante cualquier reordenación del enum.
     */
    private static final Map<Integer, ErrorCode> POR_STATUS = Stream.of(
                    MALFORMED_REQUEST,      // 400
                    UNAUTHORIZED,           // 401
                    ACCESS_DENIED,          // 403
                    ENDPOINT_NOT_FOUND,     // 404
                    METHOD_NOT_ALLOWED,     // 405
                    NOT_ACCEPTABLE,         // 406
                    RESOURCE_CONFLICT,      // 409
                    CONTENT_TOO_LARGE,      // 413
                    UNSUPPORTED_MEDIA_TYPE, // 415
                    // VALIDATION_ERROR y BUSINESS_RULE_VIOLATION comparten 422 desde que
                    // VALIDATION_ERROR dejó de ser 400; VALIDATION_ERROR es el representante
                    // porque un 422 "de la nada" (sin pasar por un handler explícito) es más
                    // probable que sea un dato de entrada mal formado que una regla de negocio.
                    VALIDATION_ERROR,       // 422
                    TOO_MANY_REQUESTS,      // 429
                    INTERNAL_ERROR,         // 500
                    SERVICE_UNAVAILABLE)    // 503
            .collect(Collectors.toUnmodifiableMap(
                    e -> e.getHttpStatus().value(), Function.identity()));

    /**
     * Código semántico que corresponde a un status HTTP dado. Se usa como red de
     * seguridad para los errores generados por el framework, de modo que ninguna
     * respuesta salga sin un {@code code} de esta enumeración.
     *
     * @return el código canónico del status, o {@link #INTERNAL_ERROR} si no hay
     *         ninguno registrado para ese valor.
     */
    public static ErrorCode porStatus(int status) {
        return POR_STATUS.getOrDefault(status, INTERNAL_ERROR);
    }
}
