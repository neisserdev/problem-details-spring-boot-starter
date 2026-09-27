package io.github.neisserdev.problemdetails.integracion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.neisserdev.problemdetails.ResourceNotFoundException;

@RestController
@RequestMapping("/publico")
class ControladorDePrueba {

    record NuevoUsuario(@NotBlank String nombre, @NotBlank @Email String email) {
    }

    @GetMapping("/pedidos/{id}")
    String pedido(@PathVariable("id") long id) {
        throw new ResourceNotFoundException("Pedido", id);
    }

    @PostMapping("/pedidos")
    String crearPedido() {
        throw new StockInsuficienteException(3);
    }

    @PostMapping("/usuarios")
    String crearUsuario(@Valid @RequestBody NuevoUsuario usuario) {
        return usuario.nombre();
    }

    /** Validación integrada de Spring MVC: la clase no lleva @Validated. */
    @GetMapping("/pagina")
    String pagina(@RequestParam("n") @Min(1) int n) {
        return "pagina " + n;
    }

    @GetMapping("/retirado")
    String retirado() {
        throw new ResponseStatusException(HttpStatus.GONE, "El recurso se retiro");
    }

    @GetMapping("/suscripcion")
    String suscripcion() {
        throw new SuscripcionCaducadaException();
    }

    @PostMapping("/login")
    String login() {
        throw new BadCredentialsException("Bad credentials");
    }

    /** Simula un @PreAuthorize que falla dentro del controlador. */
    @GetMapping("/solo-admin")
    String soloAdmin() {
        throw new AccessDeniedException("Access Denied");
    }

    @GetMapping("/consumidor")
    String consumidor() {
        throw new ExcepcionDelConsumidor();
    }

    @GetMapping("/fallo")
    String fallo() {
        throw new IllegalStateException("fallo inesperado");
    }

    @ResponseStatus(code = HttpStatus.PAYMENT_REQUIRED, reason = "Suscripcion caducada")
    static class SuscripcionCaducadaException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class ExcepcionDelConsumidor extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
