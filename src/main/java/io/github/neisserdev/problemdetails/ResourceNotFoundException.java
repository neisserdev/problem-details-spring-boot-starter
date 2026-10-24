package io.github.neisserdev.problemdetails;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Missing resource. Responds 404 Not Found.
 *
 * <p>Services can throw this domain exception instead of access-denied (403 Forbidden)
 * when an authenticated user attempts to access resources belonging to another user.
 * This hides the existence of the resource and prevents ID enumeration / guessing attacks.
 *
 * <p>When created with {@link #ResourceNotFoundException(String, Object)}, includes
 * {@code resource} and {@code resourceId} in the ProblemDetail JSON properties.
 * The {@code detail} is translated with the
 * {@code problemDetails.detail.RESOURCE_NOT_FOUND.resource} key, where {@code {0}}
 * is the resource and {@code {1}} the id.
 */
public class ResourceNotFoundException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private static final String DETAIL_CODE = ProblemDetailsFactory.DETAIL_KEY_PREFIX + "RESOURCE_NOT_FOUND.resource";

    private final String resource;
    private final transient Object id;

    /**
     * Creates an exception specifying the resource name and its identifier.
     *
     * @param resource name of the resource, for example "Order"
     * @param id       requested identifier
     */
    public ResourceNotFoundException(String resource, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "%s with id %s not found".formatted(resource, id));
        this.resource = resource;
        this.id = id;
    }

    /**
     * Creates an exception with a custom message.
     *
     * @param message the detail message
     */
    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
        this.resource = null;
        this.id = null;
    }

    /**
     * Creates an exception with a custom message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message, cause);
        this.resource = null;
        this.id = null;
    }

    /**
     * Creates an exception with the default message.
     */
    public ResourceNotFoundException() {
        super(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
        this.resource = null;
        this.id = null;
    }

    /**
     * @return the resource name, or {@code null} if not specified
     */
    public String getResource() {
        return resource;
    }

    /**
     * @return the resource id, or {@code null} if not specified
     */
    public Object getId() {
        return id;
    }

    @Override
    public Map<String, Object> getProperties() {
        if (resource == null && id == null) {
            return Map.of();
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        if (resource != null) {
            properties.put("resource", String.valueOf(resource));
        }
        if (id != null) {
            properties.put("resourceId", String.valueOf(id));
        }
        return Map.copyOf(properties);
    }

    @Override
    public String getDetailMessageCode() {
        return (resource != null && id != null) ? DETAIL_CODE : null;
    }

    // As text, so MessageFormat does not group the digits of numeric ids
    @Override
    public Object[] getDetailMessageArguments() {
        return (resource != null && id != null)
                ? new Object[] {String.valueOf(resource), String.valueOf(id)}
                : new Object[0];
    }
}
