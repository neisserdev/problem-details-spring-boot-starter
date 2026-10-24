package dev.neisser.exceptions.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import dev.neisser.exceptions.ErrorCode;
import dev.neisser.exceptions.ProblemJsonWriter;

/**
 * 401 en la capa de FILTROS: peticion sin autenticar que intenta acceder a un
 * recurso protegido. Ocurre antes de llegar a cualquier @RestControllerAdvice,
 * por eso necesita su propio punto de entrada.
 *
 * <p>La escritura se delega en {@link ProblemJsonWriter}, el mismo componente
 * que usan el limitador de peticiones y la validacion de origen, de modo que
 * todos los errores nacidos en filtros comparten cuerpo, cabeceras y
 * extensiones.
 *
 * <p>El bean lo declara {@code ProblemDetailsSecurityAutoConfiguration}, condicionado
 * a {@code problem-details.security.enabled} y a que Spring Security esté presente.
 */
public class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemJsonWriter writer;

    public SecurityAuthenticationEntryPoint(ProblemJsonWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // RFC 6750 3: toda respuesta 401 a un recurso protegido DEBE incluir el
        // challenge WWW-Authenticate del esquema Bearer. El detalle legible por
        // maquina (codigo, causa) viaja en el cuerpo problem+json (RFC 9457); el
        // header cumple el contrato de transporte, sin acoplar este modulo a las
        // clases OAuth2 del resource server.
        writer.escribir(response, request,
                ErrorCode.UNAUTHORIZED,
                "Se requiere autenticacion para acceder a este recurso",
                HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }
}
