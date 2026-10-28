package io.github.neisserdev.problemdetails;

import org.jspecify.annotations.Nullable;

/**
 * Conflict with the current state of the resource, for example a repeated unique value. Responds 409.
 */
public class ResourceConflictException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message detail of the conflict
     */
    public ResourceConflictException(@Nullable String message) {
        super(ErrorCode.RESOURCE_CONFLICT, message);
    }

    /**
     * @param message detail of the conflict
     * @param cause   the cause
     */
    public ResourceConflictException(@Nullable String message, @Nullable Throwable cause) {
        super(ErrorCode.RESOURCE_CONFLICT, message, cause);
    }
}
