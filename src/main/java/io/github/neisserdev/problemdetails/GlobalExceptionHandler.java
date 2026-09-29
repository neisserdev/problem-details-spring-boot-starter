package io.github.neisserdev.problemdetails;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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
 * DEBUG si son 4xx y en ERROR si son 5xx. Los conflictos de datos, en DEBUG.
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
    private static final String DATA_INTEGRITY_VIOLATION =
            "org.springframework.dao.DataIntegrityViolationException";
    private static final String OPTIMISTIC_LOCKING_FAILURE =
            "org.springframework.dao.OptimisticLockingFailureException";

    private static final String PROP_ERRORS = "errors";
    private static final String PROP_COUNT = "count";
    private static final String PROP_FIELD = "field";
    private static final String PROP_DETAIL = "detail";

    private final ProblemDetailsFactory factory;
    private final boolean securityEnabled;

    /**
     * @param factory         factory de las respuestas
     * @param securityEnabled si es {@code true}, {@code BadCredentialsException}
     *                        responde {@link ErrorCode#INVALID_CREDENTIALS}
     */
    public GlobalExceptionHandler(ProblemDetailsFactory factory, boolean securityEnabled) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.securityEnabled = securityEnabled;
    }

    /**
     * @return la factory, para subclases
     */
    protected final ProblemDetailsFactory getFactory() {
        return factory;
    }

    // Negocio

    /**
     * @param ex      la excepción de negocio
     * @param request la petición en curso
     * @return el problema con el tipo de la excepción
     */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        String code = ex.getProblemType().getCode();
        if (ex.getProblemType().getHttpStatus().is5xxServerError()) {
            log.error("Business exception [{}] on {}: {}", code, request.getRequestURI(), ex.getMessage(), ex);
        } else {
            log.debug("Business exception [{}] on {}: {}", code, request.getRequestURI(), ex.getMessage());
        }
        applyHeaders(ex.getHeaders());
        return factory.create(ex, request.getRequestURI());
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
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> error(readablePath(v.getPropertyPath()), v.getMessage()))
                .toList();
        return validationProblem(ErrorCode.CONSTRAINT_VIOLATION, errors,
                "Uno o más parámetros no cumplen las restricciones", request.getRequestURI());
    }

    /**
     * Excepciones no controladas. Relanza las de Spring Security, responde 409 a
     * los conflictos de datos, respeta {@link ResponseStatus} y el resto responde 500.
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
        if (securityEnabled && isInstanceOf(ex, BAD_CREDENTIALS)) {
            return factory.create(ErrorCode.INVALID_CREDENTIALS,
                    factory.detail(ErrorCode.INVALID_CREDENTIALS, "Las credenciales no son válidas"), uri);
        }

        // Spring Security decide entre 401 y 403
        if (isInstanceOf(ex, ACCESS_DENIED) || isInstanceOf(ex, AUTHENTICATION)) {
            throw ex;
        }

        // El mensaje de la base de datos no se expone, revela tablas y restricciones
        if (isInstanceOf(ex, DATA_INTEGRITY_VIOLATION)) {
            log.debug("Data integrity violation on {}: {}", uri, ex.getMessage());
            return factory.create(ErrorCode.RESOURCE_CONFLICT,
                    factory.message(ProblemDetailsFactory.DETAIL_KEY_PREFIX + "RESOURCE_CONFLICT.integrity",
                            "La operación entra en conflicto con datos existentes"), uri);
        }
        if (isInstanceOf(ex, OPTIMISTIC_LOCKING_FAILURE)) {
            log.debug("Optimistic locking failure on {}: {}", uri, ex.getMessage());
            return factory.create(ErrorCode.RESOURCE_CONFLICT,
                    factory.message(ProblemDetailsFactory.DETAIL_KEY_PREFIX + "RESOURCE_CONFLICT.concurrency",
                            "El recurso fue modificado por otra operación. Vuelve a cargarlo e inténtalo de nuevo"), uri);
        }

        ResponseStatus annotated = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        if (annotated != null) {
            ProblemDetail pd = ProblemDetail.forStatus(annotated.code());
            if (StringUtils.hasText(annotated.reason())) {
                // reason admite una clave de mensaje, igual que en Spring MVC
                pd.setDetail(factory.message(annotated.reason(), annotated.reason()));
            }
            factory.complete(pd, annotated.code().value(), uri);
            if (annotated.code().is5xxServerError()) {
                log.error("Exception with @ResponseStatus({}) on {}", annotated.code().value(), uri, ex);
            } else {
                log.debug("Exception with @ResponseStatus({}) on {}: {}", annotated.code().value(), uri, ex.toString());
            }
            return pd;
        }

        log.error("Unhandled exception [{}] on {}", ex.getMessage(), uri, ex);
        return factory.create(ErrorCode.INTERNAL_ERROR,
                factory.detail(ErrorCode.INTERNAL_ERROR, "Ha ocurrido un error interno"), uri);
    }

    // Excepciones de Spring MVC con mensaje propio

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        // Errores por campo
        List<Map<String, String>> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                errors.add(error(e.getField(), messageOf(e, "Valor no válido"))));

        // Reglas a nivel de clase
        ex.getBindingResult().getGlobalErrors().forEach(e ->
                errors.add(error("", messageOf(e, "Datos no válidos"))));

        ProblemDetail pd = validationProblem(ErrorCode.VALIDATION_ERROR, errors,
                "Los datos enviados no son válidos", uriOf(request));
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

        List<Map<String, String>> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String parameter = parameterName(result);
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors().forEach(e ->
                        errors.add(error(e.getField(), messageOf(e, "Valor no válido"))));
                parameterErrors.getGlobalErrors().forEach(e ->
                        errors.add(error(parameter, messageOf(e, "Datos no válidos"))));
            } else {
                result.getResolvableErrors().forEach(e ->
                        errors.add(error(parameter, messageOf(e, "Valor no válido"))));
            }
        }
        ex.getCrossParameterValidationResults().forEach(e ->
                errors.add(error("", messageOf(e, "Parámetros no válidos"))));

        ProblemDetail pd = validationProblem(ErrorCode.CONSTRAINT_VIOLATION, errors,
                "Uno o más parámetros no cumplen las restricciones", uriOf(request));
        return handleExceptionInternal(ex, pd, headers, ErrorCode.CONSTRAINT_VIOLATION.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        ProblemDetail pd = factory.create(
                ErrorCode.MALFORMED_REQUEST,
                factory.detail(ErrorCode.MALFORMED_REQUEST, "El cuerpo de la petición no se puede leer o está mal formado"),
                uriOf(request));

        return handleExceptionInternal(ex, pd, headers, ErrorCode.MALFORMED_REQUEST.getHttpStatus(), request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        String detail;
        if (ex instanceof MethodArgumentTypeMismatchException mate && mate.getRequiredType() != null) {
            detail = factory.message(ProblemDetailsFactory.DETAIL_KEY_PREFIX + "TYPE_MISMATCH.parameter",
                    "El parámetro ''{0}'' debe ser de tipo {1}",
                    mate.getName(), mate.getRequiredType().getSimpleName());
        } else {
            detail = factory.detail(ErrorCode.TYPE_MISMATCH, "El valor proporcionado no tiene el tipo esperado");
        }

        ProblemDetail pd = factory.create(ErrorCode.TYPE_MISMATCH, detail, uriOf(request));
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

        ResponseEntity<Object> responseEntity = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (responseEntity == null) {
            return null;
        }
        if (responseEntity.getBody() instanceof ProblemDetail pd) {
            factory.complete(pd, responseEntity.getStatusCode().value(), uriOf(request));
        }
        return responseEntity;
    }

    // Utilidades

    // Forma común de los errores de validación, el detail lista los mensajes
    private ProblemDetail validationProblem(ErrorCode code, List<Map<String, String>> errors,
                                            String genericDetail, String uri) {
        String summary = errors.stream()
                .map(e -> e.get(PROP_DETAIL))
                .distinct()
                .collect(Collectors.joining(", "));

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(PROP_ERRORS, errors);
        extra.put(PROP_COUNT, errors.size());
        String detail = summary.isBlank() ? factory.detail(code, genericDetail) : summary;
        return factory.create(code, detail, uri, extra);
    }

    private static Map<String, String> error(String field, String message) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put(PROP_FIELD, field);
        error.put(PROP_DETAIL, message);
        return error;
    }

    private static String messageOf(MessageSourceResolvable error, String defaultText) {
        String message = error.getDefaultMessage();
        return message != null ? message : defaultText;
    }

    // Nombre de @RequestParam o @PathVariable, si no el del parámetro Java
    private static String parameterName(ParameterValidationResult result) {
        MethodParameter parameter = result.getMethodParameter();
        String name = null;

        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            name = firstWithText(requestParam.name(), requestParam.value());
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (name == null && pathVariable != null) {
            name = firstWithText(pathVariable.name(), pathVariable.value());
        }
        if (name == null) {
            name = parameter.getParameterName();
        }
        if (name == null) {
            name = "arg" + parameter.getParameterIndex();
        }

        if (result.getContainerIndex() != null) {
            return name + "[" + result.getContainerIndex() + "]";
        }
        if (result.getContainerKey() != null) {
            return name + "[" + result.getContainerKey() + "]";
        }
        return name;
    }

    private static String firstWithText(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a;
        }
        return StringUtils.hasText(b) ? b : null;
    }

    // Ruta de la violación sin el nombre del método: "page" en lugar de "listar.page"
    private static String readablePath(Path path) {
        StringBuilder sb = new StringBuilder();
        for (Path.Node node : path) {
            ElementKind kind = node.getKind();
            if (kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR) {
                continue;
            }
            if (node.isInIterable()) {
                Object position = node.getIndex() != null ? node.getIndex() : node.getKey();
                sb.append('[').append(position != null ? position : "").append(']');
            }
            String name = node.getName();
            if (name != null && !name.isEmpty() && !name.startsWith("<")) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(name);
            }
        }
        return sb.toString();
    }

    // DispatcherServlet expone la respuesta en curso
    private static void applyHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletResponse response = attributes.getResponse();
            if (response != null) {
                headers.forEach((name, value) -> {
                    if (name != null && value != null) {
                        response.setHeader(name, value);
                    }
                });
            }
        }
    }

    private static boolean isInstanceOf(Throwable ex, String className) {
        for (Class<?> type = ex.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    private static String uriOf(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return null;
    }
}
