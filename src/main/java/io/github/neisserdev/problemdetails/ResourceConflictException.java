package io.github.neisserdev.problemdetails;

/**
 * Conflicto con el estado actual del recurso, por ejemplo un valor único repetido. Responde 409.
 */
public class ResourceConflictException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message detalle del conflicto
     */
    public ResourceConflictException(String message) {
        super(ErrorCode.RESOURCE_CONFLICT, message);
    }
}
