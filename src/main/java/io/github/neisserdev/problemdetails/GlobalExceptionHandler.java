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
 * Manejador global de excepciones. Convierte los errores de negocio, de Spring
 * MVC y de validación en respuestas {@code application/problem+json}.
 *
 * <p>Todas las respuestas de {@link ResponseEntityExceptionHandler} pasan por
 * {@link #handleExceptionInternal}, donde se completan con {@code code},
 * {@code timestamp} e {@code instance}.
 *
 * <p>No importa clases de Spring Security para funcionar sin ella en el
 * classpath. Sus excepciones se reconocen por nombre y se relanzan a
 * {@code ExceptionTranslationFilter}.
 *
 * <p>Tiene la precedencia más baja, los {@code @RestControllerAdvice} de la
 * aplicación se consultan antes.
 *
 * <p>Las excepciones de negocio y las de {@link ResponseStatus} se registran en
 * DEBUG si son 4xx y en ERROR si son 5xx.
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

    private static final String PROP_ERRORS = "errors";
    private static final String PROP_COUNT = "count";
    private static final String PROP_FIELD = "field";
    private static final String PROP_DETAIL = "detail";

    private final ProblemDetailsFactory fabrica;
    private final boolean seguridadHabilitada;

    /**
     * @param fabrica             factory de las respuestas
     * @param seguridadHabilitada si es {@code true}, {@code BadCredentialsException}
     *                            responde {@link ErrorCode#INVALID_CREDENTIALS}
     */
    public GlobalExceptionHandler(ProblemDetailsFactory fabrica, boolean seguridadHabilitada) {
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
        this.seguridadHabilitada = seguridadHabilitada;
    }

    /**
     * @return la factory, para subclases
     */
    protected final ProblemDetailsFactory getFabrica() {
        return fabrica;
    }

    // Negocio

    /**
     * @param ex      la excepción de negocio
     * @param request la petición en curso
     * @return el problema con el tipo de la excepción
     */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        String codigo = ex.getProblemType().getCode();
        if (ex.getProblemType().getHttpStatus().is5xxServerError()) {
            log.error("Business exception [{}] on {}: {}", codigo, request.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.debug("Business exception [{}] on {}: {}", codigo, request.getRequestURI(), ex.getMessage());
        }
        return fabrica.crear(ex, request.getRequestURI());
    }

    /**
     * Restricciones en controladores con {@code @Validated} o en entidades al persistir.
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
     * Excepciones no controladas. Relanza las de Spring Security y respeta
     * {@link ResponseStatus}, el resto responde 500.
     *
     * @param ex      la excepción no manejada
     * @param request la petición en curso
     * @return el problema correspondiente
     * @throws Exception la propia {@code ex} si es de Spring Security
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) throws Exception {
        String uri = request.getRequestURI();

        // BadCredentialsException del login llega al controlador
        if (seguridadHabilitada && esInstanciaDe(ex, BAD_CREDENTIALS)) {
            return fabrica.crear(ErrorCode.INVALID_CREDENTIALS,
                    fabrica.detalle(ErrorCode.INVALID_CREDENTIALS, "El correo o la contraseña no son válidos"), uri);
        }

        // Spring Security decide entre 401 y 403
        if (esInstanciaDe(ex, ACCESS_DENIED) || esInstanciaDe(ex, AUTHENTICATION)) {
            throw ex;
        }

        ResponseStatus anotada = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        if (anotada != null) {
            ProblemDetail pd = ProblemDetail.forStatus(anotada.code());
            if (StringUtils.hasText(anotada.reason())) {
                // reason admite una clave de mensaje, igual que en Spring MVC
                pd.setDetail(fabrica.mensaje(anotada.reason(), anotada.reason()));
            }
            fabrica.completar(pd, anotada.code().value(), uri);
            if (anotada.code().is5xxServerError()) {
                log.error("Exception with @ResponseStatus({}) on {}", anotada.code().value(), uri, ex);
            } else {
                log.debug("Exception with @ResponseStatus({}) on {}: {}", anotada.code().value(), uri, ex.toString());
            }
            return pd;
        }

        log.error("Unhandled exception [{}] on {}", ex.getMessage(), uri, ex);
        return fabrica.crear(ErrorCode.INTERNAL_ERROR,
                fabrica.detalle(ErrorCode.INTERNAL_ERROR, "Ha ocurrido un error interno"), uri);
    }

    // Excepciones de Spring MVC con mensaje propio

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        // Errores por campo
        List<Map<String, String>> errores = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                errores.add(error(e.getField(), mensajeDe(e, "Valor no válido"))));

        // Reglas a nivel de clase
        ex.getBindingResult().getGlobalErrors().forEach(e ->
                errores.add(error("", mensajeDe(e, "Datos no válidos"))));

        ProblemDetail pd = problemaDeValidacion(ErrorCode.VALIDATION_ERROR, errores,
                "Los datos enviados no son válidos", uriDe(request));
        return handleExceptionInternal(ex, pd, headers, ErrorCode.VALIDATION_ERROR.getHttpStatus(), request);
    }

    /**
     * Validación integrada de Spring MVC en parámetros sin {@code @Validated}.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        // Valor de retorno inválido, error del servidor
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
                fabrica.detalle(ErrorCode.MALFORMED_REQUEST, "El cuerpo de la petición no se puede leer o está mal formado"),
                uriDe(request));

        return handleExceptionInternal(ex, pd, headers, ErrorCode.MALFORMED_REQUEST.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        String detalle;
        if (ex instanceof MethodArgumentTypeMismatchException mate && mate.getRequiredType() != null) {
            detalle = fabrica.mensaje(ProblemDetailsFactory.PREFIJO_DETALLE + "TYPE_MISMATCH.parameter",
                    "El parámetro ''{0}'' debe ser de tipo {1}",
                    mate.getName(), mate.getRequiredType().getSimpleName());
        } else {
            detalle = fabrica.detalle(ErrorCode.TYPE_MISMATCH, "El valor proporcionado no tiene el tipo esperado");
        }

        ProblemDetail pd = fabrica.crear(ErrorCode.TYPE_MISMATCH, detalle, uriDe(request));
        return handleExceptionInternal(ex, pd, headers, ErrorCode.TYPE_MISMATCH.getHttpStatus(), request);
    }

    // Salida común de ResponseEntityExceptionHandler

    /**
     * Completa el ProblemDetail de cualquier respuesta con los campos que falten.
     * Devuelve {@code null} si la respuesta ya fue enviada.
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

    // Utilidades

    // Forma común de los errores de validación, el detail lista los mensajes
    private ProblemDetail problemaDeValidacion(ErrorCode codigo, List<Map<String, String>> errores,
                                               String detalleGenerico, String uri) {
        String resumen = errores.stream()
                .map(e -> e.get(PROP_DETAIL))
                .distinct()
                .collect(Collectors.joining(", "));

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(PROP_ERRORS, errores);
        extra.put(PROP_COUNT, errores.size());
        String detalle = resumen.isBlank() ? fabrica.detalle(codigo, detalleGenerico) : resumen;
        return fabrica.crear(codigo, detalle, uri, extra);
    }

    private static Map<String, String> error(String campo, String mensaje) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put(PROP_FIELD, campo);
        error.put(PROP_DETAIL, mensaje);
        return error;
    }

    private static String mensajeDe(MessageSourceResolvable error, String porDefecto) {
        String mensaje = error.getDefaultMessage();
        return mensaje != null ? mensaje : porDefecto;
    }

    // Nombre de @RequestParam o @PathVariable, si no el del parámetro Java
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

    // Ruta de la violación sin el nombre del método: "page" en lugar de "listar.page"
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
