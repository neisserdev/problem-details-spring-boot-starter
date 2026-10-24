package dev.neisser.exceptions;

/**
 * Una regla de negocio no se cumple (p. ej. transición de estado no permitida).
 * La petición está bien formada pero no es procesable: mapea a 422.
 */
public class BusinessRuleViolationException extends BusinessException {

    public BusinessRuleViolationException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
