package io.github.neisserdev.problemdetails.integration;

import java.util.Map;

import io.github.neisserdev.problemdetails.BusinessException;

class PaymentGatewayUnavailableException extends BusinessException {

    private static final long serialVersionUID = 1L;

    PaymentGatewayUnavailableException() {
        super(StoreErrors.PAYMENT_GATEWAY_UNAVAILABLE, "The payment gateway is not responding");
    }

    @Override
    public Map<String, String> getHeaders() {
        return Map.of("Retry-After", "120");
    }
}
