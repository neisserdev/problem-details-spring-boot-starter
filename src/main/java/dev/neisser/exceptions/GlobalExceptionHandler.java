package dev.neisser.exceptions;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manejador global de excepciones. Traduce cada familia de errores a una
 * respuesta {@code application/problem+json} uniforme vía {@link ProblemDetails}.
 *
 * <p>Extiende {@link ResponseEntityExceptionHandler} para reescribir el formato
 * de las excepciones estándar de Spring MVC. Las excepciones de negocio y de
 * autenticación se cubren con un ÚNICO handler sobre la jerarquía
 * {@link BusinessException}: añadir un error nuevo no requiere tocar esta clase.
 *
 * <h2>Garantía de forma</h2>
 * <p>{@link ResponseEntityExceptionHandler} maneja una docena larga de
 * excepciones del framework (405, 406, 415, 413, la 404 de
 * {@code NoResourceFoundException}, {@code HandlerMethodValidationException}...).
 * Sobrescribirlas una a una deja siempre huecos: las que no se toquen salen con
 * el ProblemDetail crudo de Spring, sin {@code code}, {@code timestamp} ni
 * {@code instance}.
 *
 * <p>Por eso el punto de control NO son los handlers individuales sino
 * {@link #handleExceptionInternal}, el embudo por el que la clase padre hace
 * pasar TODAS sus respuestas. Completar ahí garantiza el invariante «ningún
 * error sale sin la forma de la casa» con una sola sobrescritura, y sin tener
 * que perseguir cada excepción nueva que añada una versión futura de Spring.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /* ---------- Toda la familia de negocio/autenticación, en un solo punto ---------- */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception [{}] on {}: {}",
                ex.getErrorCode().getCode(), request.getRequestURI(), ex.getMessage());
        return ProblemDetails.of(ex, request.getRequestURI());
    }

    /**
     * DaoAuthenticationProvider propaga BadCredentialsException al controller
     * durante el login. Debe conservarse como 401 uniforme y no caer en el
     * manejador genérico con estado 500.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return ProblemDetails.of(
                ErrorCode.INVALID_CREDENTIALS,
                "El correo o la contraseña no son válidos",
                request.getRequestURI());
    }

    /* ---------- AccessDenied lanzado DENTRO de controladores/servicios ----------
       (p. ej. @PreAuthorize en un método). Las denegaciones en la capa de filtros
       las atiende SecurityAccessDeniedHandler; ambas usan la misma factory. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return ProblemDetails.of(
                ErrorCode.ACCESS_DENIED,
                "No tienes permisos suficientes para acceder a este recurso",
                request.getRequestURI());
    }

    /* ---------- Restricciones en @RequestParam / @PathVariable ---------- */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> errores = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return ProblemDetails.of(
                ErrorCode.CONSTRAINT_VIOLATION,
                "Uno o más parámetros no cumplen las restricciones",
                request.getRequestURI(),
                Map.of("errors", errores, "count", errores.size()));
    }

    /* ---------- Último recurso ---------- */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception [{}] on {}", ex.getMessage(), request.getRequestURI(), ex);
        return ProblemDetails.of(ErrorCode.INTERNAL_ERROR, "Ha ocurrido un error interno", request.getRequestURI());
    }

    /* ======== Excepciones estándar de Spring MVC con mensaje propio ========
       Solo se sobrescriben aquellas en las que aportamos un detalle mejor que el
       de Spring (o una lista de errores por campo). El RESTO se cubre solo,
       gracias al embudo de handleExceptionInternal. */

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        // Errores por campo, en forma estructurada (Map<String, String> campo -> mensaje)
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                errors.merge(e.getField(),
                        e.getDefaultMessage() != null ? e.getDefaultMessage() : "Valor no válido",
                        (m1, m2) -> m1 + ", " + m2));

        // Reglas que afectan a varios campos a la vez (anotaciones de clase)
        ex.getBindingResult().getGlobalErrors().forEach(e ->
                errors.merge(e.getObjectName(),
                        e.getDefaultMessage() != null ? e.getDefaultMessage() : "Datos no válidos",
                        (m1, m2) -> m1 + ", " + m2));

        String resumen = errors.values().stream()
                .distinct()
                .collect(Collectors.joining(" "));

        ProblemDetail pd = ProblemDetails.of(
                ErrorCode.VALIDATION_ERROR,
                resumen.isBlank() ? "Los datos enviados no son válidos" : resumen,
                uriDe(request),
                Map.of("errors", errors, "count", errors.size()));

        return handleExceptionInternal(ex, pd, headers, ErrorCode.VALIDATION_ERROR.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        ProblemDetail pd = ProblemDetails.of(
                ErrorCode.MALFORMED_REQUEST,
                "El cuerpo de la petición no se puede leer o está mal formado",
                uriDe(request));

        return handleExceptionInternal(ex, pd, headers, ErrorCode.MALFORMED_REQUEST.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            org.springframework.beans.TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        String detalle = "El valor proporcionado no tiene el tipo esperado";
        if (ex instanceof MethodArgumentTypeMismatchException mate && mate.getRequiredType() != null) {
            detalle = "El parámetro '%s' debe ser de tipo %s"
                    .formatted(mate.getName(), mate.getRequiredType().getSimpleName());
        }

        ProblemDetail pd = ProblemDetails.of(ErrorCode.TYPE_MISMATCH, detalle, uriDe(request));
        return handleExceptionInternal(ex, pd, headers, ErrorCode.TYPE_MISMATCH.getHttpStatus(), request);
    }

    /**
     * {@code Content-Type} no soportado por el endpoint (p. ej. {@code application/xml}
     * a uno que solo acepta JSON). {@link HttpMediaTypeNotSupportedException} ya
     * implementa {@code ErrorResponse} y el padre la reconoce, así que esto ya
     * saldría como 415 sin esta sobrescritura; se deja explícita para fijar el
     * {@code detail} y no depender del fallback genérico de {@link ErrorCode#porStatus}.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        String detalle = ex.getContentType() != null
                ? "El tipo de contenido '%s' no está soportado por este endpoint".formatted(ex.getContentType())
                : "El tipo de contenido de la petición no está soportado por este endpoint";

        ProblemDetail pd = ProblemDetails.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE, detalle, uriDe(request));
        return handleExceptionInternal(ex, pd, headers, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getHttpStatus(), request);
    }

    /**
     * El cuerpo de la petición excede el tamaño máximo configurado en el servidor
     * ({@code spring.servlet.multipart.max-file-size}, {@code max-request-size},
     * {@code server.tomcat.max-swallow-size}...).
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        String uri = uriDe(request);
        log.warn("Cuerpo demasiado grande en {}: límite {} bytes", uri, ex.getMaxUploadSize());
        String detalle = ex.getMaxUploadSize() > 0
                ? "El cuerpo de la petición supera el límite permitido de %d bytes".formatted(ex.getMaxUploadSize())
                : "El cuerpo de la petición supera el límite permitido por el servidor";
        ProblemDetail pd = ProblemDetails.of(ErrorCode.CONTENT_TOO_LARGE, detalle, uri);
        return handleExceptionInternal(ex, pd, headers, ErrorCode.CONTENT_TOO_LARGE.getHttpStatus(), request);
    }

    /* ======== EL embudo: toda respuesta de la clase padre pasa por aquí ======== */

    /**
     * Punto único por el que salen TODAS las respuestas construidas por
     * {@link ResponseEntityExceptionHandler}, incluidas las de las excepciones
     * que esta clase no sobrescribe. Se delega primero en el padre (que resuelve
     * el cuerpo a partir del {@code ErrorResponse}) y después se COMPLETA el
     * ProblemDetail resultante con las extensiones de la casa.
     *
     * <p>{@link ProblemDetails#completar} no pisa lo que ya venga puesto, así que
     * los problemas fabricados arriba con {@code ProblemDetails.of} conservan su
     * código semántico y solo se rellena lo que falte.
     *
     * <p>Devuelve {@code null} cuando la respuesta ya está comprometida
     * (contrato del padre): en ese caso no hay nada que escribir.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        ResponseEntity<Object> respuesta = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (respuesta == null) {
            return null;
        }
        if (respuesta.getBody() instanceof ProblemDetail pd) {
            ProblemDetails.completar(pd, respuesta.getStatusCode().value(), uriDe(request));
        }
        return respuesta;
    }

    private static String uriDe(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
