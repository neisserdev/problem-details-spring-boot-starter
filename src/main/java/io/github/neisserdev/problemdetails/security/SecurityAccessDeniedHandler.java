package io.github.neisserdev.problemdetails.security;

import java.io.IOException;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import io.github.neisserdev.problemdetails.ErrorCode;
import io.github.neisserdev.problemdetails.ProblemJsonWriter;

/**
 * 403 en la capa de FILTROS: usuario autenticado pero sin permisos.
 *
 * <p>Cubre también las {@code AccessDeniedException} lanzadas dentro de un
 * controlador o servicio (por ejemplo desde {@code @PreAuthorize}): el
 * {@code GlobalExceptionHandler} las relanza, {@code ExceptionTranslationFilter}
 * las recibe y, si el usuario está autenticado, las entrega a este manejador.
 * Si la petición es anónima, las entrega al
 * {@link SecurityAuthenticationEntryPoint} y la respuesta es 401.
 */
public class SecurityAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemJsonWriter writer;

    /**
     * @param writer escritor de problem+json compartido
     */
    public SecurityAccessDeniedHandler(ProblemJsonWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writer.escribir(response, request,
                ErrorCode.ACCESS_DENIED,
                "No tienes permisos suficientes para acceder a este recurso");
    }
}
