package io.github.neisserdev.problemdetails;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes {@code application/problem+json} straight to the response. Meant for
 * filters, where errors never reach the {@code @RestControllerAdvice}.
 *
 * <pre>{@code
 * writer.write(response, request, ErrorCode.TOO_MANY_REQUESTS,
 *         "Request limit exceeded", "Retry-After", "30");
 * }</pre>
 *
 * <p>Requires the Spring Boot {@link JsonMapper}, which serializes the
 * {@link ProblemDetail} extensions at the root of the JSON.
 */
public class ProblemJsonWriter {

    private final JsonMapper jsonMapper;
    private final ProblemDetailsFactory factory;

    /**
     * @param jsonMapper mapper of the Spring context
     * @param factory    factory that builds the problems
     */
    public ProblemJsonWriter(JsonMapper jsonMapper, ProblemDetailsFactory factory) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    /**
     * @return the factory that builds the problems
     */
    public ProblemDetailsFactory getFactory() {
        return factory;
    }

    /**
     * @param response response to write to
     * @param request  current request, for the {@code instance}
     * @param type     problem type
     * @param detail   specific explanation of this occurrence
     * @throws IOException if writing the response fails
     */
    public void write(HttpServletResponse response, HttpServletRequest request,
                      ProblemType type, String detail) throws IOException {
        write(response, request, type, detail, null, null);
    }

    /**
     * @param response    response to write to
     * @param request     current request, for the {@code instance}
     * @param type        problem type
     * @param detail      specific explanation of this occurrence
     * @param headerName  additional header, for example {@code Retry-After}, or {@code null}
     * @param headerValue value of that header, or {@code null}
     * @throws IOException if writing the response fails
     */
    public void write(HttpServletResponse response, HttpServletRequest request,
                      ProblemType type, String detail,
                      String headerName, String headerValue) throws IOException {

        if (headerName != null && headerValue != null && !response.isCommitted()) {
            response.setHeader(headerName, headerValue);
        }
        write(response, factory.create(type, detail, request.getRequestURI()));
    }

    /**
     * Writes an already built problem, for example with custom extensions.
     *
     * @param response response to write to
     * @param problem  problem to serialize, its {@code status} is the one of the response
     * @throws IOException if writing the response fails
     */
    public void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        // Response already sent, it cannot be written
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(problem));
    }
}
