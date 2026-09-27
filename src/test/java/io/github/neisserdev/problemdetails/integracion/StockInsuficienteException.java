package io.github.neisserdev.problemdetails.integracion;

import java.util.Map;

import io.github.neisserdev.problemdetails.BusinessException;

class StockInsuficienteException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private final int disponible;

    StockInsuficienteException(int disponible) {
        super(ErroresDeTienda.STOCK_INSUFICIENTE, "Solo quedan %d unidades".formatted(disponible));
        this.disponible = disponible;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("disponible", disponible);
    }
}
