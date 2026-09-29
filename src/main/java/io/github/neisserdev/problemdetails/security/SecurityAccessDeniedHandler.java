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
 * Responde 403 cuando un usuario autenticado no tiene permisos. También recibe
 * las {@code AccessDeniedException} de controladores y servicios, que el
 * {@code GlobalExceptionHandler} devuelve a Spring Security.
 */
public class SecurityAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemJsonWriter writer;

    /**
     * @param writer escritor de problem+json
     */
    public SecurityAccessDeniedHandler(ProblemJsonWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writer.write(response, request,
                ErrorCode.ACCESS_DENIED,
                writer.getFactory().detail(ErrorCode.ACCESS_DENIED,
                        "No tienes permisos suficientes para acceder a este recurso"));
    }
}
