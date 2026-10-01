package io.github.neisserdev.problemdetails;

/**
 * Conflict with the current state of the resource, for example a repeated unique value. Responds 409.
 */
public class ResourceConflictException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message detail of the conflict
     */
    public ResourceConflictException(String message) {
        super(ErrorCode.RESOURCE_CONFLICT, message);
    }
}
