package io.github.neisserdev.problemdetails.security;

import java.io.IOException;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import io.github.neisserdev.problemdetails.ErrorCode;
import io.github.neisserdev.problemdetails.ProblemJsonWriter;

/**
 * Responds 403 when an authenticated user lacks permissions. It also receives
 * the {@code AccessDeniedException} of controllers and services, which
 * {@code GlobalExceptionHandler} hands back to Spring Security.
 */
public class SecurityAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityAccessDeniedHandler.class);

    private final ProblemJsonWriter writer;

    /**
     * @param writer problem+json writer
     */
    public SecurityAccessDeniedHandler(ProblemJsonWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.debug("Access denied on {}: {}", request.getRequestURI(), accessDeniedException.getMessage());
        writer.write(response, request,
                ErrorCode.ACCESS_DENIED,
                writer.getFactory().detail(ErrorCode.ACCESS_DENIED,
                        "You do not have permission to access this resource"));
    }
}
