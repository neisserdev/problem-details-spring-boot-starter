package io.github.neisserdev.problemdetails;

import org.jspecify.annotations.Nullable;

/**
 * Violated business rule, for example a state transition that is not allowed. Responds 422.
 */
public class BusinessRuleViolationException extends BusinessException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message detail of the violated rule
     */
    public BusinessRuleViolationException(@Nullable String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    /**
     * @param message detail of the violated rule
     * @param cause   the cause
     */
    public BusinessRuleViolationException(@Nullable String message, @Nullable Throwable cause) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message, cause);
    }
}
