package io.github.neisserdev.problemdetails.integracion;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import io.github.neisserdev.problemdetails.ProblemType;

/** Catálogo propio de un proyecto consumidor, sin tocar el de la librería. */
enum ErroresDeTienda implements ProblemType {

    STOCK_INSUFICIENTE("Stock insuficiente", HttpStatus.CONFLICT);

    private final String titulo;
    private final HttpStatus status;

    ErroresDeTienda(String titulo, HttpStatus status) {
        this.titulo = titulo;
        this.status = status;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getTitle() {
        return titulo;
    }

    @Override
    public HttpStatusCode getHttpStatus() {
        return status;
    }
}
