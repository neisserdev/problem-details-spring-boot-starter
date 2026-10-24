package dev.neisser.exceptions;

import java.util.Map;

/**
 * Base de todas las excepciones de negocio y de autenticación de la aplicación.
 *
 * <p>Cada subtipo transporta su propio {@link ErrorCode}, de modo que un único
 * {@code @ExceptionHandler(BusinessException.class)} traduce toda la familia a
 * un {@code ProblemDetail}. Añadir un error nuevo no toca el handler.
 *
 * <p>Las subclases pueden aportar propiedades adicionales (vía
 * {@link #getProperties()}) que se serializan como miembros de extensión del
 * ProblemDetail, conforme a RFC 9457.
 */
public abstract class BusinessException extends RuntimeException {

    private final transient ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }

    /** Propiedades extra para el ProblemDetail (miembros de extensión RFC 9457). */
    public Map<String, Object> getProperties() { return Map.of(); }
}
