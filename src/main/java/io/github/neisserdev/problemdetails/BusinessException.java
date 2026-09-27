package io.github.neisserdev.problemdetails;

import java.util.Map;
import java.util.Objects;

/**
 * Base de todas las excepciones de negocio y de autenticación de la aplicación.
 *
 * <p>Cada subtipo transporta su propio {@link ProblemType}, de modo que un único
 * {@code @ExceptionHandler(BusinessException.class)} traduce toda la familia a
 * un {@code ProblemDetail}. Añadir un error nuevo no toca el handler.
 *
 * <p>Las subclases pueden aportar propiedades adicionales (vía
 * {@link #getProperties()}) que se serializan como miembros de extensión del
 * ProblemDetail, conforme a RFC 9457.
 */
public abstract class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ProblemType problemType;

    /**
     * @param problemType tipo de problema que representa esta excepción
     * @param message     texto que se usará como {@code detail}
     */
    protected BusinessException(ProblemType problemType, String message) {
        super(message);
        this.problemType = Objects.requireNonNull(problemType, "problemType");
    }

    /**
     * @param problemType tipo de problema que representa esta excepción
     * @param message     texto que se usará como {@code detail}
     * @param cause       causa original, solo para los logs
     */
    protected BusinessException(ProblemType problemType, String message, Throwable cause) {
        super(message, cause);
        this.problemType = Objects.requireNonNull(problemType, "problemType");
    }

    /**
     * @return el tipo de problema que transporta la excepción
     */
    public ProblemType getProblemType() { return problemType; }

    /**
     * Propiedades extra para el ProblemDetail (miembros de extensión RFC 9457).
     * Las claves {@code code} y {@code timestamp} están reservadas y se ignoran.
     *
     * @return mapa de propiedades, vacío por defecto
     */
    public Map<String, Object> getProperties() { return Map.of(); }
}
