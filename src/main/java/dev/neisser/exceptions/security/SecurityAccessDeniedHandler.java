package dev.neisser.exceptions.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import dev.neisser.exceptions.ErrorCode;
import dev.neisser.exceptions.ProblemJsonWriter;

/**
 * 403 en la capa de FILTROS: usuario autenticado pero sin permisos, bloqueado
 * antes de llegar al controlador. El caso analogo dentro de controladores
 * (@PreAuthorize) lo atiende el GlobalExceptionHandler; ambos producen una
 * salida identica porque ambos pasan por la misma factory de ProblemDetail.
 *
 * <p>El bean lo declara {@code ProblemDetailsSecurityAutoConfiguration}, condicionado
 * a {@code problem-details.security.enabled} y a que Spring Security esté presente.
 */
public class SecurityAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemJsonWriter writer;

    public SecurityAccessDeniedHandler(ProblemJsonWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writer.escribir(response, request,
                ErrorCode.ACCESS_DENIED,
                "No tienes permisos suficientes para acceder a este recurso");
    }
}
