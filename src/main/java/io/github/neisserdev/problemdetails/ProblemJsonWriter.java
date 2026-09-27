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
 * Escribe un {@code application/problem+json} directamente en la respuesta, para
 * los errores que ocurren en la capa de FILTROS y por tanto nunca llegan al
 * {@code @RestControllerAdvice}: los 401/403 de Spring Security, un limitador
 * de peticiones que responde 429, una validación de origen...
 *
 * <p>La autoconfiguración lo registra como bean siempre que haya un
 * {@link JsonMapper} en el contexto, esté o no activado el módulo de seguridad.
 * Así un filtro propio puede inyectarlo y producir exactamente el mismo cuerpo,
 * con las mismas extensiones, que un error de negocio:
 *
 * <pre>{@code
 * writer.escribir(response, request, ErrorCode.TOO_MANY_REQUESTS,
 *         "Has superado el límite de peticiones", "Retry-After", "30");
 * }</pre>
 *
 * <p>Usa el {@link JsonMapper} del contexto y no uno construido a mano porque
 * es el que lleva registrado el mixin con el que Spring Boot aplana las
 * extensiones de {@link ProblemDetail} al nivel raíz del JSON. Con un mapper
 * propio, {@code code} y {@code timestamp} saldrían anidados bajo
 * {@code "properties"} y estas respuestas divergirían del resto de la API.
 */
public class ProblemJsonWriter {

    private final JsonMapper jsonMapper;
    private final ProblemDetailsFactory fabrica;

    /**
     * @param jsonMapper mapper del contexto de Spring (con el mixin de ProblemDetail)
     * @param fabrica    factory con la que se construyen los problemas
     */
    public ProblemJsonWriter(JsonMapper jsonMapper, ProblemDetailsFactory fabrica) {
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
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
     * @param nombreCabecera nombre de una cabecera adicional obligatoria por
     *                       protocolo (p. ej. {@code Retry-After} en un 429), o {@code null}
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
     * Escribe un problema ya construido. Útil cuando hace falta añadir
     * miembros de extensión propios antes de responder.
     *
     * @param response respuesta en la que escribir
     * @param problema problema a serializar; su {@code status} se usa como status HTTP
     * @throws IOException si falla la escritura en la respuesta
     */
    public void escribir(HttpServletResponse response, ProblemDetail problema) throws IOException {
        // Si otro componente ya empezó a enviar la respuesta, cualquier escritura
        // corrompería lo que el cliente está recibiendo. No hay nada que hacer.
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(problema.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(problema));
    }
}
