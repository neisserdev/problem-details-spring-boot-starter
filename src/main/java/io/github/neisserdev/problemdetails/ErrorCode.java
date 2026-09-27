package io.github.neisserdev.problemdetails;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;

/**
 * Catálogo base de errores de la API.
 * Cada valor ata un código estable (usado en el {@code type} del ProblemDetail
 * y expuesto como propiedad {@code code}), un título legible y un status HTTP.
 *
 * <p>Para errores propios de una aplicación no hace falta tocar este enum: se
 * declara otro que implemente {@link ProblemType} y se crea su excepción
 * extendiendo {@link BusinessException}. El handler no se toca.
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
public enum ErrorCode implements ProblemType {

    // --- Validación / formato de la petición ---
    VALIDATION_ERROR("Error de validación", HttpStatus.BAD_REQUEST),
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
    /** 422 Unprocessable Content (RFC 9110; antes "Unprocessable Entity"). */
    BUSINESS_RULE_VIOLATION("Regla de negocio no satisfecha", HttpStatus.UNPROCESSABLE_CONTENT),

    // --- Protocolo HTTP (errores que genera Spring MVC, no el dominio) ---
    ENDPOINT_NOT_FOUND("Endpoint no encontrado", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("Método HTTP no permitido", HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE("Formato de respuesta no aceptable", HttpStatus.NOT_ACCEPTABLE),
    UNSUPPORTED_MEDIA_TYPE("Tipo de contenido no soportado", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    /** 413. Nombre de RFC 9110; Spring 7 deprecó el antiguo {@code PAYLOAD_TOO_LARGE}. */
    CONTENT_TOO_LARGE("Contenido de la petición demasiado grande", HttpStatus.CONTENT_TOO_LARGE),
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

    @Override
    public String getCode() { return name(); }

    @Override
    public String getTitle() { return title; }

    @Override
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
                    BUSINESS_RULE_VIOLATION,// 422
                    TOO_MANY_REQUESTS,      // 429
                    INTERNAL_ERROR,         // 500
                    SERVICE_UNAVAILABLE)    // 503
            .collect(Collectors.toUnmodifiableMap(
                    e -> e.getHttpStatus().value(), Function.identity()));

    /**
     * Código semántico que representa a un status HTTP dado. Lo usa la
     * {@link ProblemDetailsFactory} para completar los errores generados por el
     * framework.
     *
     * <p>Devuelve vacío para los status sin representante (410, 402, 412...).
     * Antes caían en {@link #INTERNAL_ERROR}, lo que etiquetaba, por ejemplo,
     * un 410 como "Error interno del servidor"; ahora la factory les asigna un
     * código derivado del propio status.
     *
     * @param status valor numérico del status HTTP
     * @return el código canónico del status, si lo hay
     */
    public static Optional<ErrorCode> porStatus(int status) {
        return Optional.ofNullable(POR_STATUS.get(status));
    }
}
