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
 * Responds 401 when an unauthenticated request accesses a protected resource.
 *
 * <p>Sends the {@code WWW-Authenticate} header required by RFC 9110. The
 * default value is {@code Bearer} and it is changed with
 * {@code problem-details.security.www-authenticate}.
 */
public class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemJsonWriter writer;
    private final String wwwAuthenticate;

    /**
     * @param writer          problem+json writer
     * @param wwwAuthenticate value of {@code WWW-Authenticate}, empty or {@code null} to omit it
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
                        "Authentication is required to access this resource"),
                wwwAuthenticate != null ? HttpHeaders.WWW_AUTHENTICATE : null,
                wwwAuthenticate);
    }
}
