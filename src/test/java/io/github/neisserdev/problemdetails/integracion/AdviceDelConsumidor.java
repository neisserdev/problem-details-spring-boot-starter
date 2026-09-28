package io.github.neisserdev.problemdetails.integracion;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Debe tener prioridad sobre el manejador del starter
@RestControllerAdvice
class AdviceDelConsumidor {

    @ExceptionHandler(ControladorDePrueba.ExcepcionDelConsumidor.class)
    ResponseEntity<String> manejar() {
        return ResponseEntity.status(418).body("atendida por la aplicacion");
    }
}
