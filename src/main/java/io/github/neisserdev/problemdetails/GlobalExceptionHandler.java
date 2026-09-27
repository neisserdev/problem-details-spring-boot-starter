package io.github.neisserdev.problemdetails;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manejador global de excepciones. Traduce cada familia de errores a una
 * respuesta {@code application/problem+json} uniforme vía {@link ProblemDetailsFactory}.
 *
 * <p>Extiende {@link ResponseEntityExceptionHandler} para reescribir el formato
 * de las excepciones estándar de Spring MVC. Las excepciones de negocio se
 * cubren con un ÚNICO handler sobre la jerarquía {@link BusinessException}:
 * añadir un error nuevo no requiere tocar esta clase.
 *
 * <h2>Garantía de forma</h2>
 * <p>{@link ResponseEntityExceptionHandler} maneja una docena larga de
 * excepciones del framework (405, 406, 415, 413, la 404 de
 * {@code NoResourceFoundException}...). Sobrescribirlas una a una deja siempre
 * huecos: las que no se toquen salen con el ProblemDetail crudo de Spring, sin
 * {@code code}, {@code timestamp} ni {@code instance}.
 *
 * <p>Por eso el punto de control NO son los handlers individuales sino
 * {@link #handleExceptionInternal}, el embudo por el que la clase padre hace
 * pasar TODAS sus respuestas. Completar ahí garantiza el invariante «ningún
 * error sale sin la forma de la casa» con una sola sobrescritura.
 *
 * <h2>Spring Security</h2>
 * <p>Esta clase no importa ninguna clase de Spring Security, para que el
 * starter funcione en proyectos que no la usan: si la firma de un
 * {@code @ExceptionHandler} nombrara {@code AccessDeniedException} y la clase no
 * estuviera en el classpath, la aplicación no arrancaría. Las excepciones de
 * seguridad se reconocen por nombre y se relanzan para que las traduzca
 * {@code ExceptionTranslationFilter}, que sí distingue entre anónimo (401) y
 * autenticado sin permisos (403).
 *
 * <h2>Orden</h2>
 * <p>Va con la precedencia más baja: cualquier {@code @RestControllerAdvice}
 * de la aplicación se consulta antes que este, así que el proyecto consumidor
 * siempre puede tomar el control de una excepción concreta.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String BAD_CREDENTIALS =
            "org.springframework.security.authentication.BadCredentialsException";
    private static final String ACCESS_DENIED =
            "org.springframework.security.access.AccessDeniedException";
    private static final String AUTHENTICATION =
            "org.springframework.security.core.AuthenticationException";

    private final ProblemDetailsFactory fabrica;
    private final boolean seguridadHabilitada;

    /**
     * @param fabrica             factory con la que se construyen todas las respuestas
     * @param seguridadHabilitada si es {@code true}, un {@code BadCredentialsException}
     *                            que llegue al controlador se traduce a
     *                            {@link ErrorCode#INVALID_CREDENTIALS}; si es
     *                            {@code false}, se deja a Spring Security como
     *                            cualquier otra excepción de autenticación
     */
    public GlobalExceptionHandler(ProblemDetailsFactory fabrica, boolean seguridadHabilitada) {
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
        this.seguridadHabilitada = seguridadHabilitada;
    }

    /**
     * @return la factory, para subclases que añadan sus propios handlers
     */
    protected final ProblemDetailsFactory getFabrica() {
        return fabrica;
    }

    /* ---------- Toda la familia de negocio, en un solo punto ---------- */

    /**
     * @param ex      la excepción de negocio
     * @param request la petición en curso
     * @return el problema con el tipo que transporta la excepción
     */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception [{}] on {}: {}",
                ex.getProblemType().getCode(), request.getRequestURI(), ex.getMessage());
        return fabrica.crear(ex, request.getRequestURI());
    }

    /**
     * Restricciones en {@code @RequestParam} / {@code @PathVariable} de un
     * controlador anotado con {@code @Validated} (validación por AOP), o en
     * entidades validadas al persistir.
     *
     * @param ex      la violación
     * @param request la petición en curso
     * @return problema {@link ErrorCode#CONSTRAINT_VIOLATION} con la lista de errores
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<Map<String, String>> errores = ex.getConstraintViolations().stream()
                .map(v -> error(rutaLegible(v.getPropertyPath()), v.getMessage()))
                .toList();
        return problemaDeValidacion(ErrorCode.CONSTRAINT_VIOLATION, errores,
                "Uno o más parámetros no cumplen las restricciones", request.getRequestURI());
    }

    /**
     * Último recurso. Antes de responder 500 respeta dos contratos estándar que
     * un catch-all rompería en silencio: las excepciones de Spring Security
     * (se relanzan) y las anotadas con {@link ResponseStatus}.
     *
     * @param ex      la excepción no manejada
     * @param request la petición en curso
     * @return el problema correspondiente
     * @throws Exception la propia {@code ex} si es de Spring Security
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) throws Exception {
        String uri = request.getRequestURI();

        // DaoAuthenticationProvider propaga BadCredentialsException al controller
        // durante el login. Debe quedar como 401 uniforme, no como 500.
        if (seguridadHabilitada && esInstanciaDe(ex, BAD_CREDENTIALS)) {
            return fabrica.crear(ErrorCode.INVALID_CREDENTIALS,
                    "El correo o la contraseña no son válidos", uri);
        }

        // AccessDenied/Authentication lanzadas DENTRO de controladores o servicios
        // (p. ej. @PreAuthorize). Relanzarlas deja la decisión a
        // ExceptionTranslationFilter: 401 si la petición es anónima, 403 si no.
        // Relanzar la MISMA instancia no genera el aviso "Failure in @ExceptionHandler".
        if (esInstanciaDe(ex, ACCESS_DENIED) || esInstanciaDe(ex, AUTHENTICATION)) {
            throw ex;
        }

        ResponseStatus anotada = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        if (anotada != null) {
            ProblemDetail pd = ProblemDetail.forStatus(anotada.code());
            if (StringUtils.hasText(anotada.reason())) {
                pd.setDetail(anotada.reason());
            }
            fabrica.completar(pd, anotada.code().value(), uri);
            log.warn("Exception with @ResponseStatus({}) on {}: {}", anotada.code().value(), uri, ex.toString());
            return pd;
        }

        log.error("Unhandled exception [{}] on {}", ex.getMessage(), uri, ex);
        return fabrica.crear(ErrorCode.INTERNAL_ERROR, "Ha ocurrido un error interno", uri);
    }

    /* ======== Excepciones estándar de Spring MVC con mensaje propio ========
       Solo se sobrescriben aquellas en las que aportamos un detalle mejor que el
       de Spring (o una lista de errores por campo). El RESTO se cubre solo,
       gracias al embudo de handleExceptionInternal. */

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        // Errores por campo, en forma estructurada: el cliente puede marcar cada
        // campo en su formulario sin tener que analizar una cadena.
        List<Map<String, String>> errores = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                errores.add(error(e.getField(), mensajeDe(e, "Valor no válido"))));

        // Reglas que afectan a varios campos a la vez (anotaciones de clase):
        // sin esto, un fallo de coherencia entre campos se perdía por el camino.
        ex.getBindingResult().getGlobalErrors().forEach(e ->
                errores.add(error("", mensajeDe(e, "Datos no válidos"))));

        ProblemDetail pd = problemaDeValidacion(ErrorCode.VALIDATION_ERROR, errores,
                "Los datos enviados no son válidos", uriDe(request));
        return handleExceptionInternal(ex, pd, headers, ErrorCode.VALIDATION_ERROR.getHttpStatus(), request);
    }

    /**
     * Validación integrada de Spring MVC (6.1+): se activa cuando un parámetro
     * del controlador lleva restricciones ({@code @Min}, {@code @Email}...) y la
     * clase NO está anotada con {@code @Validated}. Sin esta sobrescritura el
     * error salía como un genérico {@code MALFORMED_REQUEST} sin detalle por campo.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        // Un valor de RETORNO inválido es un fallo del servidor, no del cliente.
        if (ex.isForReturnValue()) {
            return super.handleHandlerMethodValidationException(ex, headers, statusCode, request);
        }

        List<Map<String, String>> errores = new ArrayList<>();
        for (ParameterValidationResult resultado : ex.getParameterValidationResults()) {
            String parametro = nombreDe(resultado);
            if (resultado instanceof ParameterErrors errors) {
                errors.getFieldErrors().forEach(e ->
                        errores.add(error(e.getField(), mensajeDe(e, "Valor no válido"))));
                errors.getGlobalErrors().forEach(e ->
                        errores.add(error(parametro, mensajeDe(e, "Datos no válidos"))));
            } else {
                resultado.getResolvableErrors().forEach(e ->
                        errores.add(error(parametro, mensajeDe(e, "Valor no válido"))));
            }
        }
        ex.getCrossParameterValidationResults().forEach(e ->
                errores.add(error("", mensajeDe(e, "Parámetros no válidos"))));

        ProblemDetail pd = problemaDeValidacion(ErrorCode.CONSTRAINT_VIOLATION, errores,
                "Uno o más parámetros no cumplen las restricciones", uriDe(request));
        return handleExceptionInternal(ex, pd, headers, ErrorCode.CONSTRAINT_VIOLATION.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        ProblemDetail pd = fabrica.crear(
                ErrorCode.MALFORMED_REQUEST,
                "El cuerpo de la petición no se puede leer o está mal formado",
                uriDe(request));

        return handleExceptionInternal(ex, pd, headers, ErrorCode.MALFORMED_REQUEST.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        String detalle = "El valor proporcionado no tiene el tipo esperado";
        if (ex instanceof MethodArgumentTypeMismatchException mate && mate.getRequiredType() != null) {
            detalle = "El parámetro '%s' debe ser de tipo %s"
                    .formatted(mate.getName(), mate.getRequiredType().getSimpleName());
        }

        ProblemDetail pd = fabrica.crear(ErrorCode.TYPE_MISMATCH, detalle, uriDe(request));
        return handleExceptionInternal(ex, pd, headers, ErrorCode.TYPE_MISMATCH.getHttpStatus(), request);
    }

    /* ======== EL embudo: toda respuesta de la clase padre pasa por aquí ======== */

    /**
     * Punto único por el que salen TODAS las respuestas construidas por
     * {@link ResponseEntityExceptionHandler}, incluidas las de las excepciones
     * que esta clase no sobrescribe. Se delega primero en el padre (que resuelve
     * el cuerpo a partir del {@code ErrorResponse}) y después se COMPLETA el
     * ProblemDetail resultante con las extensiones de la casa.
     *
     * <p>{@link ProblemDetailsFactory#completar} no pisa lo que ya venga puesto,
     * así que los problemas fabricados arriba conservan su código semántico y
     * solo se rellena lo que falte.
     *
     * <p>Devuelve {@code null} cuando la respuesta ya está comprometida
     * (contrato del padre): en ese caso no hay nada que escribir.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        ResponseEntity<Object> respuesta = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (respuesta == null) {
            return null;
        }
        if (respuesta.getBody() instanceof ProblemDetail pd) {
            fabrica.completar(pd, respuesta.getStatusCode().value(), uriDe(request));
        }
        return respuesta;
    }

    /* ======== Utilidades ======== */

    /**
     * El {@code detail} ENUMERA los fallos en vez de decir "los datos no son
     * válidos". Ese texto genérico obligaba a adivinar qué campo estaba mal
     * —y con formularios de ocho campos, a probar uno por uno—. Además así
     * cualquier cliente muestra algo útil sin leer las propiedades extra.
     *
     * <p>Las tres rutas de validación (cuerpo, parámetros con validación
     * integrada y parámetros con {@code @Validated}) comparten esta forma, así
     * el cliente procesa {@code errores} de una sola manera.
     */
    private ProblemDetail problemaDeValidacion(ErrorCode codigo, List<Map<String, String>> errores,
                                               String detalleGenerico, String uri) {
        String resumen = errores.stream()
                .map(e -> e.get("mensaje"))
                .distinct()
                .collect(Collectors.joining(" "));

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("errores", errores);
        extra.put("count", errores.size());
        return fabrica.crear(codigo, resumen.isBlank() ? detalleGenerico : resumen, uri, extra);
    }

    private static Map<String, String> error(String campo, String mensaje) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("campo", campo);
        error.put("mensaje", mensaje);
        return error;
    }

    private static String mensajeDe(MessageSourceResolvable error, String porDefecto) {
        String mensaje = error.getDefaultMessage();
        return mensaje != null ? mensaje : porDefecto;
    }

    /**
     * Nombre con el que el CLIENTE conoce el parámetro: el de
     * {@code @RequestParam("n")} o {@code @PathVariable("id")} si lo declara,
     * y si no el nombre Java del parámetro.
     */
    private static String nombreDe(ParameterValidationResult resultado) {
        MethodParameter parametro = resultado.getMethodParameter();
        String nombre = null;

        RequestParam requestParam = parametro.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            nombre = primeroConTexto(requestParam.name(), requestParam.value());
        }
        PathVariable pathVariable = parametro.getParameterAnnotation(PathVariable.class);
        if (nombre == null && pathVariable != null) {
            nombre = primeroConTexto(pathVariable.name(), pathVariable.value());
        }
        if (nombre == null) {
            nombre = parametro.getParameterName();
        }
        if (nombre == null) {
            nombre = "arg" + parametro.getParameterIndex();
        }

        if (resultado.getContainerIndex() != null) {
            return nombre + "[" + resultado.getContainerIndex() + "]";
        }
        if (resultado.getContainerKey() != null) {
            return nombre + "[" + resultado.getContainerKey() + "]";
        }
        return nombre;
    }

    private static String primeroConTexto(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a;
        }
        return StringUtils.hasText(b) ? b : null;
    }

    /**
     * Ruta de una violación sin el nodo del método: {@code "page"} en lugar de
     * {@code "listar.page"}. El nombre del método Java es un detalle interno
     * que no le sirve al cliente.
     */
    private static String rutaLegible(Path ruta) {
        StringBuilder sb = new StringBuilder();
        for (Path.Node nodo : ruta) {
            ElementKind tipo = nodo.getKind();
            if (tipo == ElementKind.METHOD || tipo == ElementKind.CONSTRUCTOR) {
                continue;
            }
            if (nodo.isInIterable()) {
                Object posicion = nodo.getIndex() != null ? nodo.getIndex() : nodo.getKey();
                sb.append('[').append(posicion != null ? posicion : "").append(']');
            }
            String nombre = nodo.getName();
            if (nombre != null && !nombre.isEmpty() && !nombre.startsWith("<")) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(nombre);
            }
        }
        return sb.toString();
    }

    private static boolean esInstanciaDe(Throwable ex, String nombreDeClase) {
        for (Class<?> tipo = ex.getClass(); tipo != null; tipo = tipo.getSuperclass()) {
            if (tipo.getName().equals(nombreDeClase)) {
                return true;
            }
        }
        return false;
    }

    private static String uriDe(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return null;
    }
}
