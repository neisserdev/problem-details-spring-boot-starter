package io.github.neisserdev.problemdetails;

/**
 * Regla de negocio incumplida, por ejemplo una transición de estado no permitida. Responde 422.
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
