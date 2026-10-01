package io.github.neisserdev.problemdetails;

import java.util.Map;

/**
 * Missing resource. Responds 404 and includes {@code resource} and {@code resourceId} in the JSON.
 *
 * <p>The {@code detail} is translated with the
 * {@code problemDetails.detail.RESOURCE_NOT_FOUND.resource} key, where {@code {0}}
 * is the resource and {@code {1}} the id.
 */
public class ResourceNotFoundException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private static final String DETAIL_CODE = ProblemDetailsFactory.DETAIL_KEY_PREFIX + "RESOURCE_NOT_FOUND.resource";

    private final String resource;
    private final transient Object id;

    /**
     * @param resource name of the resource, for example "Order"
     * @param id       requested identifier
     */
    public ResourceNotFoundException(String resource, Object id) {
        super(ErrorCode.RESOURCE_NOT_FOUND, "%s with id %s not found".formatted(resource, id));
        this.resource = resource;
        this.id = id;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("resource", String.valueOf(resource), "resourceId", String.valueOf(id));
    }

    @Override
    public String getDetailMessageCode() {
        return DETAIL_CODE;
    }

    // As text, so MessageFormat does not group the digits of numeric ids
    @Override
    public Object[] getDetailMessageArguments() {
        return new Object[] {String.valueOf(resource), String.valueOf(id)};
    }
}
