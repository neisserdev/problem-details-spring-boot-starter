package io.github.neisserdev.problemdetails.integracion;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Advice propio de la aplicación. Tiene que consultarse antes que el
 * manejador del starter, aunque este tenga un catch-all para Exception.
 */
@RestControllerAdvice
class AdviceDelConsumidor {

    @ExceptionHandler(ControladorDePrueba.ExcepcionDelConsumidor.class)
    ResponseEntity<String> manejar() {
        return ResponseEntity.status(418).body("atendida por la aplicacion");
    }
}
