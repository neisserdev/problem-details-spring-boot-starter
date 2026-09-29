package io.github.neisserdev.problemdetails;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;

/**
 * Catálogo base de errores. Cada valor define el código, el título y el status HTTP.
 *
 * <p>Los errores de Spring MVC (método no permitido, ruta inexistente, etc.)
 * también reciben un código de este catálogo mediante {@link #forStatus(int)}.
 *
 * <p>Para errores propios se implementa {@link ProblemType} en un enum aparte.
 *
 * <p>Los títulos se pueden traducir con la clave {@code problemDetails.title.CODIGO}.
 */
public enum ErrorCode implements ProblemType {

    // Validación y formato
    VALIDATION_ERROR("Error de validación", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST("Petición mal formada", HttpStatus.BAD_REQUEST),
    TYPE_MISMATCH("Parámetro con tipo incorrecto", HttpStatus.BAD_REQUEST),
    CONSTRAINT_VIOLATION("Restricción no satisfecha", HttpStatus.BAD_REQUEST),

    // Autenticación y autorización
    UNAUTHORIZED("No autenticado", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("Credenciales inválidas", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("Acceso denegado", HttpStatus.FORBIDDEN),

    // Negocio
    RESOURCE_NOT_FOUND("Recurso no encontrado", HttpStatus.NOT_FOUND),
    RESOURCE_CONFLICT("Conflicto con el estado actual del recurso", HttpStatus.CONFLICT),
    BUSINESS_RULE_VIOLATION("Regla de negocio no satisfecha", HttpStatus.UNPROCESSABLE_CONTENT),

    // Protocolo HTTP
    ENDPOINT_NOT_FOUND("Endpoint no encontrado", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("Método HTTP no permitido", HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE("Formato de respuesta no aceptable", HttpStatus.NOT_ACCEPTABLE),
    UNSUPPORTED_MEDIA_TYPE("Tipo de contenido no soportado", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    CONTENT_TOO_LARGE("Contenido de la petición demasiado grande", HttpStatus.CONTENT_TOO_LARGE),
    TOO_MANY_REQUESTS("Demasiadas peticiones", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE("Servicio no disponible", HttpStatus.SERVICE_UNAVAILABLE),

    // Genérico
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

    // Representante de cada status para los errores del framework
    private static final Map<Integer, ErrorCode> BY_STATUS = Stream.of(
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
     * Código que representa a un status HTTP. Vacío si el status no tiene
     * representante en el catálogo (410, 402, 412, etc.).
     *
     * @param status valor numérico del status HTTP
     * @return el código del status, si existe
     */
    public static Optional<ErrorCode> forStatus(int status) {
        return Optional.ofNullable(BY_STATUS.get(status));
    }
}
