package io.github.neisserdev.problemdetails.integration;

import java.util.Map;

import io.github.neisserdev.problemdetails.BusinessException;

class InsufficientStockException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private final int available;

    InsufficientStockException(int available) {
        super(StoreErrors.INSUFFICIENT_STOCK, "Only %d units left".formatted(available));
        this.available = available;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("available", available);
    }
}
