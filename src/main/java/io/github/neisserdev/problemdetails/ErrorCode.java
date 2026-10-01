package io.github.neisserdev.problemdetails;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;

/**
 * Built-in error catalog. Each value defines the code, the title and the HTTP status.
 *
 * <p>Spring MVC errors (method not allowed, unknown route, etc.) also get a
 * code from this catalog through {@link #forStatus(int)}.
 *
 * <p>Custom errors implement {@link ProblemType} in a separate enum.
 *
 * <p>The titles returned here are the English defaults. Responses use
 * {@link ProblemDetailsFactory#title(ProblemType)}, which applies the
 * configured language and the {@code problemDetails.title.CODE} translations.
 */
public enum ErrorCode implements ProblemType {

    // Validation and format
    VALIDATION_ERROR("Validation error", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST("Malformed request", HttpStatus.BAD_REQUEST),
    TYPE_MISMATCH("Invalid parameter type", HttpStatus.BAD_REQUEST),
    CONSTRAINT_VIOLATION("Constraint violation", HttpStatus.BAD_REQUEST),

    // Authentication and authorization
    UNAUTHORIZED("Authentication required", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("Invalid credentials", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("Access denied", HttpStatus.FORBIDDEN),

    // Business
    RESOURCE_NOT_FOUND("Resource not found", HttpStatus.NOT_FOUND),
    RESOURCE_CONFLICT("Conflict with the current state of the resource", HttpStatus.CONFLICT),
    BUSINESS_RULE_VIOLATION("Business rule violation", HttpStatus.UNPROCESSABLE_CONTENT),

    // HTTP protocol
    ENDPOINT_NOT_FOUND("Endpoint not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("Method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE("Not acceptable", HttpStatus.NOT_ACCEPTABLE),
    UNSUPPORTED_MEDIA_TYPE("Unsupported media type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    CONTENT_TOO_LARGE("Content too large", HttpStatus.CONTENT_TOO_LARGE),
    TOO_MANY_REQUESTS("Too many requests", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE("Service unavailable", HttpStatus.SERVICE_UNAVAILABLE),

    // Generic
    INTERNAL_ERROR("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

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

    // Representative code of each status for framework errors
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
     * Code that represents an HTTP status. Empty when the status has no
     * representative in the catalog (410, 402, 412, etc.).
     *
     * @param status numeric value of the HTTP status
     * @return the code of the status, if any
     */
    public static Optional<ErrorCode> forStatus(int status) {
        return Optional.ofNullable(BY_STATUS.get(status));
    }
}
