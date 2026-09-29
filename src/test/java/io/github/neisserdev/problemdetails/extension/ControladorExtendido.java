package io.github.neisserdev.problemdetails.extension;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.neisserdev.problemdetails.ResourceNotFoundException;

@RestController
@RequestMapping("/extension")
class ControladorExtendido {

    @GetMapping("/articulos/{id}")
    String articulo(@PathVariable("id") long id) {
        throw new ResourceNotFoundException("Articulo", id);
    }

    @GetMapping("/retirado")
    String retirado() {
        throw new ManejadorExtendido.ArticuloRetiradoException();
    }

    @GetMapping("/fallo")
    String fallo() {
        throw new IllegalStateException("fallo inesperado");
    }
}
