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
 * Responde 401 cuando una petición sin autenticar accede a un recurso protegido.
 *
 * <p>Envía la cabecera {@code WWW-Authenticate} que exige RFC 9110. El valor por
 * defecto es {@code Bearer} y se cambia con
 * {@code problem-details.security.www-authenticate}.
 */
public class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemJsonWriter writer;
    private final String wwwAuthenticate;

    /**
     * @param writer          escritor de problem+json
     * @param wwwAuthenticate valor de {@code WWW-Authenticate}, vacío o {@code null} para omitirla
     */
    public SecurityAuthenticationEntryPoint(ProblemJsonWriter writer, String wwwAuthenticate) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.wwwAuthenticate = StringUtils.hasText(wwwAuthenticate) ? wwwAuthenticate.strip() : null;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writer.write(response, request,
                ErrorCode.UNAUTHORIZED,
                writer.getFactory().detail(ErrorCode.UNAUTHORIZED,
                        "Se requiere autenticación para acceder a este recurso"),
                wwwAuthenticate != null ? HttpHeaders.WWW_AUTHENTICATE : null,
                wwwAuthenticate);
    }
}
