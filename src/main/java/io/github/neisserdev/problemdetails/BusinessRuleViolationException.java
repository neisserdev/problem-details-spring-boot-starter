package io.github.neisserdev.problemdetails;

/**
 * Una regla de negocio no se cumple (p. ej. transición de estado no permitida).
 * La petición está bien formada pero no es procesable: mapea a 422.
 */
public class BusinessRuleViolationException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message detalle de la regla incumplida
     */
    public BusinessRuleViolationException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
