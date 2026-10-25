package io.github.neisserdev.problemdetails;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;

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
 * <p>Registers {@link ProblemDetailJacksonMixin} when the given
 * {@link JsonMapper} does not have a mix-in for {@link ProblemDetail}, so
 * extension members are always serialized at the root of the JSON.
 */
public class ProblemJsonWriter {

    private final JsonMapper jsonMapper;
    private final ProblemDetailsFactory factory;

    /**
     * @param jsonMapper mapper of the Spring context
     * @param factory    factory that builds the problems
     */
    public ProblemJsonWriter(JsonMapper jsonMapper, ProblemDetailsFactory factory) {
        Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.jsonMapper = jsonMapper.serializationConfig() != null
                && jsonMapper.serializationConfig().findMixInClassFor(ProblemDetail.class) == null
                ? jsonMapper.rebuild().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build()
                : jsonMapper;
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
                      ProblemType type, @Nullable String detail) throws IOException {
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
                      ProblemType type, @Nullable String detail,
                      @Nullable String headerName, @Nullable String headerValue) throws IOException {

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
