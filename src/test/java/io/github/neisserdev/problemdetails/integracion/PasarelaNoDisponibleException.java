package io.github.neisserdev.problemdetails.integracion;

import io.github.neisserdev.problemdetails.BusinessException;

class PasarelaNoDisponibleException extends BusinessException {

    private static final long serialVersionUID = 1L;

    PasarelaNoDisponibleException() {
        super(ErroresDeTienda.PASARELA_NO_DISPONIBLE, "La pasarela de pago no responde");
    }
}
