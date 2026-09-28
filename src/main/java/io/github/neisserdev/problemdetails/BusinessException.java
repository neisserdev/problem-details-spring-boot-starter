package io.github.neisserdev.problemdetails;

import java.util.Map;
import java.util.Objects;

/**
 * Base de las excepciones de negocio. Cada subclase indica su {@link ProblemType}
 * y el manejador global la convierte en {@code ProblemDetail}.
 *
 * <p>Las propiedades de {@link #getProperties()} se añaden como miembros de
 * extensión del JSON.
 */
public abstract class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ProblemType problemType;

    /**
     * @param problemType tipo de problema
     * @param message     texto del {@code detail}
     */
    protected BusinessException(ProblemType problemType, String message) {
        super(message);
        this.problemType = Objects.requireNonNull(problemType, "problemType");
    }

    /**
     * @param problemType tipo de problema
     * @param message     texto del {@code detail}
     * @param cause       causa original, solo para los logs
     */
    protected BusinessException(ProblemType problemType, String message, Throwable cause) {
        super(message, cause);
        this.problemType = Objects.requireNonNull(problemType, "problemType");
    }

    /**
     * @return el tipo de problema
     */
    public ProblemType getProblemType() { return problemType; }

    /**
     * Miembros de extensión del ProblemDetail. Las claves {@code code} y
     * {@code timestamp} están reservadas y se ignoran.
     *
     * @return propiedades adicionales, vacío por defecto
     */
    public Map<String, Object> getProperties() { return Map.of(); }
}
