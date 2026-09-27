package io.github.neisserdev.problemdetails.security;

import java.io.IOException;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.util.StringUtils;

import io.github.neisserdev.problemdetails.ErrorCode;
import io.github.neisserdev.problemdetails.ProblemJsonWriter;

/**
 * 401 en la capa de FILTROS: petición sin autenticar que intenta acceder a un
 * recurso protegido. Ocurre antes de llegar a cualquier
 * {@code @RestControllerAdvice}, por eso necesita su propio punto de entrada.
 *
 * <p>La escritura se delega en {@link ProblemJsonWriter}, así que el cuerpo
 * tiene la misma forma que cualquier otro error de la API.
 *
 * <p>RFC 9110 exige que toda respuesta 401 incluya la cabecera
 * {@code WWW-Authenticate} con el esquema que el cliente debe usar. Por defecto
 * se envía {@code Bearer} (RFC 6750); es configurable con
 * {@code problem-details.security.www-authenticate}.
 */
public class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemJsonWriter writer;
    private final String wwwAuthenticate;

    /**
     * @param writer          escritor de problem+json compartido
     * @param wwwAuthenticate valor de la cabecera {@code WWW-Authenticate}, o
     *                        {@code null}/vacío para no enviarla
     */
    public SecurityAuthenticationEntryPoint(ProblemJsonWriter writer, String wwwAuthenticate) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.wwwAuthenticate = StringUtils.hasText(wwwAuthenticate) ? wwwAuthenticate.strip() : null;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writer.escribir(response, request,
                ErrorCode.UNAUTHORIZED,
                "Se requiere autenticación para acceder a este recurso",
                wwwAuthenticate != null ? HttpHeaders.WWW_AUTHENTICATE : null,
                wwwAuthenticate);
    }
}
