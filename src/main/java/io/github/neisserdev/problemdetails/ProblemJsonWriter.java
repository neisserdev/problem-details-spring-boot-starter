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
 * writer.escribir(response, request, ErrorCode.TOO_MANY_REQUESTS,
 *         "Has superado el límite de peticiones", "Retry-After", "30");
 * }</pre>
 *
 * <p>Requiere el {@link JsonMapper} de Spring Boot, que serializa las
 * extensiones de {@link ProblemDetail} en la raíz del JSON.
 */
public class ProblemJsonWriter {

    private final JsonMapper jsonMapper;
    private final ProblemDetailsFactory fabrica;

    /**
     * @param jsonMapper mapper del contexto de Spring
     * @param fabrica    factory con la que se construyen los problemas
     */
    public ProblemJsonWriter(JsonMapper jsonMapper, ProblemDetailsFactory fabrica) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
    }

    /**
     * @return la factory con la que se construyen los problemas
     */
    public ProblemDetailsFactory getFabrica() {
        return fabrica;
    }

    /**
     * @param response respuesta en la que escribir
     * @param request  petición en curso, para el {@code instance}
     * @param tipo     tipo de problema
     * @param detalle  explicación específica de esta ocurrencia
     * @throws IOException si falla la escritura en la respuesta
     */
    public void escribir(HttpServletResponse response, HttpServletRequest request,
                         ProblemType tipo, String detalle) throws IOException {
        escribir(response, request, tipo, detalle, null, null);
    }

    /**
     * @param response       respuesta en la que escribir
     * @param request        petición en curso, para el {@code instance}
     * @param tipo           tipo de problema
     * @param detalle        explicación específica de esta ocurrencia
     * @param nombreCabecera cabecera adicional, por ejemplo {@code Retry-After}, o {@code null}
     * @param cabecera       valor de esa cabecera, o {@code null}
     * @throws IOException si falla la escritura en la respuesta
     */
    public void escribir(HttpServletResponse response, HttpServletRequest request,
                         ProblemType tipo, String detalle,
                         String nombreCabecera, String cabecera) throws IOException {

        if (nombreCabecera != null && cabecera != null && !response.isCommitted()) {
            response.setHeader(nombreCabecera, cabecera);
        }
        escribir(response, fabrica.crear(tipo, detalle, request.getRequestURI()));
    }

    /**
     * Escribe un problema ya construido, por ejemplo con extensiones propias.
     *
     * @param response respuesta en la que escribir
     * @param problema problema a serializar, su {@code status} es el de la respuesta
     * @throws IOException si falla la escritura en la respuesta
     */
    public void escribir(HttpServletResponse response, ProblemDetail problema) throws IOException {
        // Respuesta ya enviada, no se puede escribir
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(problema.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(problema));
    }
}
