package io.github.neisserdev.problemdetails;

/**
 * La operación entra en conflicto con el estado actual del recurso
 * (p. ej. un valor único ya existente). Mapea a 409.
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
