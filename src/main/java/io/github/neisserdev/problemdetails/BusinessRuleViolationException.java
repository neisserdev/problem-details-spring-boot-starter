package io.github.neisserdev.problemdetails;

/**
 * Violated business rule, for example a state transition that is not allowed. Responds 422.
 */
public class BusinessRuleViolationException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message detail of the violated rule
     */
    public BusinessRuleViolationException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
