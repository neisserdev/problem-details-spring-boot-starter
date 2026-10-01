package io.github.neisserdev.problemdetails;

import java.util.Map;
import java.util.Objects;

/**
 * Base of business exceptions. Each subclass declares its {@link ProblemType}
 * and the global handler turns it into a {@code ProblemDetail}.
 *
 * <p>The entries of {@link #getProperties()} are added as extension members
 * of the JSON and those of {@link #getHeaders()} as HTTP headers.
 *
 * <p>The {@code detail} is the exception message. Override
 * {@link #getDetailMessageCode()} to translate it with a message key.
 */
public abstract class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ProblemType problemType;

    /**
     * @param problemType problem type
     * @param message     text of the {@code detail}
     */
    protected BusinessException(ProblemType problemType, String message) {
        super(message);
        this.problemType = Objects.requireNonNull(problemType, "problemType");
    }

    /**
     * @param problemType problem type
     * @param message     text of the {@code detail}
     * @param cause       original cause, for the logs only
     */
    protected BusinessException(ProblemType problemType, String message, Throwable cause) {
        super(message, cause);
        this.problemType = Objects.requireNonNull(problemType, "problemType");
    }

    /**
     * @return the problem type
     */
    public ProblemType getProblemType() { return problemType; }

    /**
     * Extension members of the ProblemDetail. The {@code code} and
     * {@code timestamp} keys are reserved and ignored.
     *
     * @return additional properties, empty by default
     */
    public Map<String, Object> getProperties() { return Map.of(); }

    /**
     * HTTP headers of the response, for example {@code Retry-After} in a 429 or 503.
     *
     * @return additional headers, empty by default
     */
    public Map<String, String> getHeaders() { return Map.of(); }

    /**
     * Message key of the {@code detail}, resolved with the application
     * {@code MessageSource} like titles. When the key has no translation, the
     * exception message is used.
     *
     * @return the key, or {@code null} to always use the exception message
     */
    public String getDetailMessageCode() { return null; }

    /**
     * Arguments for the {@code {0}}, {@code {1}}... placeholders of the
     * {@link #getDetailMessageCode() message key}.
     *
     * @return the arguments, empty by default
     */
    public Object[] getDetailMessageArguments() { return new Object[0]; }
}
