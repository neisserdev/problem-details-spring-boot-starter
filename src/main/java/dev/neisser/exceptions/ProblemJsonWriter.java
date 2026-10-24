package dev.neisser.exceptions;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe un {@code application/problem+json} directamente en la respuesta, para
 * los errores que ocurren en la capa de FILTROS y por tanto nunca llegan al
 * {@code @RestControllerAdvice}.
 *
 * <p>Extraído a un componente único para que el rechazo por origen, el 429 del
 * limitador y los 401/403 de Spring Security produzcan exactamente el mismo
 * cuerpo, con las mismas extensiones, que un error de negocio. La forma de la
 * casa no debería depender de en qué capa se rompió la petición.
 *
 * <p>Usa el {@link JsonMapper} del contexto —no uno construido a mano— porque es
 * el único que lleva registrado el mixin con el que Spring aplana las
 * extensiones de {@link ProblemDetail} al nivel raíz del JSON. Con un mapper
 * propio, {@code code} y {@code timestamp} saldrían anidados bajo
 * {@code "properties"} y estas respuestas divergirían del resto de la API.
 * Conviene blindarlo con un test que compare el JSON de un 401 de filtro con el
 * de un 404 de negocio.
 *
 * <p>Vive en {@code dev.neisser.exceptions} y no en {@code auth.security} porque
 * la dependencia entre modulos va en un solo sentido: auth conoce el modulo de
 * errores, nunca al reves. Ademas lo usan tanto los filtros de auth como los
 * manejadores 401/403 de Spring Security, que ya viven aqui.
 *
 * <p>El bean lo declara {@code ProblemDetailsSecurityAutoConfiguration}; no lleva
 * anotación de estereotipo propia para no obligar a ningún cliente a tener este
 * paquete en su component-scan.
 */
public class ProblemJsonWriter {

    private final JsonMapper jsonMapper;

    public ProblemJsonWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void escribir(HttpServletResponse response, HttpServletRequest request,
                         ErrorCode error, String detalle) throws IOException {
        escribir(response, request, error, detalle, null, null);
    }

    /**
     * @param cabecera valor de una cabecera adicional obligatoria por protocolo
     *                 (p. ej. {@code Retry-After} en un 429), o {@code null}
     */
    public void escribir(HttpServletResponse response, HttpServletRequest request,
                         ErrorCode error, String detalle,
                         String nombreCabecera, String cabecera) throws IOException {

        if (response.isCommitted()) {
            return;
        }
        ProblemDetail problema = ProblemDetails.of(error, detalle, request.getRequestURI());

        if (nombreCabecera != null && cabecera != null) {
            response.setHeader(nombreCabecera, cabecera);
        }
        response.setStatus(error.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(problema));
    }
}
