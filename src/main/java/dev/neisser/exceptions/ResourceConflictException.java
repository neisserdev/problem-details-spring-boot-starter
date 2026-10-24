package dev.neisser.exceptions;

/**
 * La operación entra en conflicto con el estado actual del recurso
 * (p. ej. un valor único ya existente). Mapea a 409.
 */
public class ResourceConflictException extends BusinessException {

    public ResourceConflictException(String message) {
        super(ErrorCode.RESOURCE_CONFLICT, message);
    }
}
