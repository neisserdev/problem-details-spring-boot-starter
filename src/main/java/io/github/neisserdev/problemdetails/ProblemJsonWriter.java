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
 * Escribe {@code application/problem+json} directamente en la respuesta. Se usa
 * en filtros, donde los errores no llegan al {@code @RestControllerAdvice}.
 *
 * <pre>{@code
 * writer.write(response, request, ErrorCode.TOO_MANY_REQUESTS,
 *         "Has superado el límite de peticiones", "Retry-After", "30");
 * }</pre>
 *
 * <p>Requiere el {@link JsonMapper} de Spring Boot, que serializa las
 * extensiones de {@link ProblemDetail} en la raíz del JSON.
 */
public class ProblemJsonWriter {

    private final JsonMapper jsonMapper;
    private final ProblemDetailsFactory factory;

    /**
     * @param jsonMapper mapper del contexto de Spring
     * @param factory    factory con la que se construyen los problemas
     */
    public ProblemJsonWriter(JsonMapper jsonMapper, ProblemDetailsFactory factory) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    /**
     * @return la factory con la que se construyen los problemas
     */
    public ProblemDetailsFactory getFactory() {
        return factory;
    }

    /**
     * @param response respuesta en la que escribir
     * @param request  petición en curso, para el {@code instance}
     * @param type     tipo de problema
     * @param detail   explicación específica de esta ocurrencia
     * @throws IOException si falla la escritura en la respuesta
     */
    public void write(HttpServletResponse response, HttpServletRequest request,
                      ProblemType type, String detail) throws IOException {
        write(response, request, type, detail, null, null);
    }

    /**
     * @param response    respuesta en la que escribir
     * @param request     petición en curso, para el {@code instance}
     * @param type        tipo de problema
     * @param detail      explicación específica de esta ocurrencia
     * @param headerName  cabecera adicional, por ejemplo {@code Retry-After}, o {@code null}
     * @param headerValue valor de esa cabecera, o {@code null}
     * @throws IOException si falla la escritura en la respuesta
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
     * Escribe un problema ya construido, por ejemplo con extensiones propias.
     *
     * @param response respuesta en la que escribir
     * @param problem  problema a serializar, su {@code status} es el de la respuesta
     * @throws IOException si falla la escritura en la respuesta
     */
    public void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        // Respuesta ya enviada, no se puede escribir
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(problem));
    }
}
