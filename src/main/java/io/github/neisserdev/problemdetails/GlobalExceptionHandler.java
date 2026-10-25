package io.github.neisserdev.problemdetails;

import java.lang.annotation.Annotation;
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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global exception handler. Turns business, Spring MVC and validation errors
 * into {@code application/problem+json} responses.
 *
 * <p>Every response of {@link ResponseEntityExceptionHandler} goes through
 * {@link #handleExceptionInternal}, where it is completed with {@code code},
 * {@code timestamp} and {@code instance}, and the Spring MVC detail is
 * translated to the configured language.
 *
 * <p>It does not import Spring Security classes, so it works without it on the
 * classpath. Its exceptions are recognized by name and rethrown to
 * {@code ExceptionTranslationFilter}.
 *
 * <p>It has the lowest precedence, the {@code @RestControllerAdvice} components
 * of the application are consulted first.
 *
 * <p>Business exceptions and {@link ResponseStatus} exceptions are logged at
 * DEBUG when they are 4xx and at ERROR when they are 5xx. Data conflicts, at DEBUG.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

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
     * @param factory         factory of the responses
     * @param securityEnabled when {@code true}, {@code BadCredentialsException}
     *                        responds {@link ErrorCode#INVALID_CREDENTIALS}
     */
    public GlobalExceptionHandler(ProblemDetailsFactory factory, boolean securityEnabled) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.securityEnabled = securityEnabled;
    }

    /**
     * @return the factory, for subclasses
     */
    protected final ProblemDetailsFactory getFactory() {
        return factory;
    }

    // Business

    /**
     * @param ex      the business exception
     * @param request the current request
     * @return the problem with the type of the exception
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
     * Constraints in {@code @Validated} beans or in entities when they are persisted.
     *
     * @param ex      the violation
     * @param request the current request
     * @return {@link ErrorCode#CONSTRAINT_VIOLATION} problem with the list of errors
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> error(readablePath(v.getPropertyPath()), v.getMessage()))
                .toList();
        return validationProblem(ErrorCode.CONSTRAINT_VIOLATION, errors,
                "One or more parameters do not meet the constraints", request.getRequestURI());
    }

    /**
     * Unhandled exceptions. Rethrows the Spring Security ones, responds 409 to
     * data conflicts, honors {@link ResponseStatus} and responds 500 to the rest.
     *
     * @param ex      the unhandled exception
     * @param request the current request
     * @return the matching problem
     * @throws Exception the given {@code ex} itself when it comes from Spring Security
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) throws Exception {
        String uri = request.getRequestURI();

        // A login done in a controller throws it here
        if (securityEnabled && isInstanceOf(ex, BAD_CREDENTIALS)) {
            return factory.create(ErrorCode.INVALID_CREDENTIALS,
                    factory.detail(ErrorCode.INVALID_CREDENTIALS, "The credentials are not valid"), uri);
        }

        // Spring Security decides between 401 and 403
        if (isInstanceOf(ex, ACCESS_DENIED) || isInstanceOf(ex, AUTHENTICATION)) {
            throw ex;
        }

        // The database message is not exposed, it reveals tables and constraints
        if (isInstanceOf(ex, DATA_INTEGRITY_VIOLATION)) {
            log.debug("Data integrity violation on {}: {}", uri, ex.getMessage());
            return factory.create(ErrorCode.RESOURCE_CONFLICT,
                    factory.message(ProblemDetailsFactory.DETAIL_KEY_PREFIX + "RESOURCE_CONFLICT.integrity",
                            "The operation conflicts with existing data"), uri);
        }
        if (isInstanceOf(ex, OPTIMISTIC_LOCKING_FAILURE)) {
            log.debug("Optimistic locking failure on {}: {}", uri, ex.getMessage());
            return factory.create(ErrorCode.RESOURCE_CONFLICT,
                    factory.message(ProblemDetailsFactory.DETAIL_KEY_PREFIX + "RESOURCE_CONFLICT.concurrency",
                            "The resource was modified by another operation. Reload it and try again"), uri);
        }

        ResponseStatus annotated = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        if (annotated != null) {
            ProblemDetail pd = ProblemDetail.forStatus(annotated.code());
            if (StringUtils.hasText(annotated.reason())) {
                // reason accepts a message key, as in Spring MVC
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

        String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
        log.error("Unhandled exception [{}] on {}", message, uri, ex);
        return factory.create(ErrorCode.INTERNAL_ERROR,
                factory.detail(ErrorCode.INTERNAL_ERROR, "An internal error occurred"), uri);
    }

    // Spring MVC exceptions with their own detail

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        // Field and object validation errors formatted as Map<String, String> field -> message
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                errors.merge(e.getField(), messageOf(e, "Invalid value"), (m1, m2) -> m1 + ", " + m2));

        ex.getBindingResult().getGlobalErrors().forEach(e ->
                errors.merge(e.getObjectName(), messageOf(e, "Invalid data"), (m1, m2) -> m1 + ", " + m2));

        String summary = errors.values().stream()
                .distinct()
                .collect(Collectors.joining(", "));

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put(PROP_ERRORS, errors);
        extra.put(PROP_COUNT, errors.size());
        String detail = summary.isBlank()
                ? factory.detail(ErrorCode.VALIDATION_ERROR, "The submitted data is not valid")
                : summary;

        ProblemDetail pd = factory.create(ErrorCode.VALIDATION_ERROR, detail, uriOf(request), extra);
        return handleExceptionInternal(ex, pd, headers, ErrorCode.VALIDATION_ERROR.getHttpStatus(), request);
    }

    /**
     * Built-in Spring MVC validation of parameters without {@code @Validated}.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        // Invalid return value, a server error
        if (ex.isForReturnValue()) {
            log.error("Invalid return value of {} on {}", ex.getMethod(), uriOf(request), ex);
            return super.handleHandlerMethodValidationException(ex, headers, statusCode, request);
        }

        List<Map<String, String>> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            addErrors(result, parameterName(result), false, errors);
        }
        addCrossParameterErrors(ex, errors);

        ProblemDetail pd = validationProblem(ErrorCode.CONSTRAINT_VIOLATION, errors,
                "One or more parameters do not meet the constraints", uriOf(request));
        return handleExceptionInternal(ex, pd, headers, ErrorCode.CONSTRAINT_VIOLATION.getHttpStatus(), request);
    }

    /**
     * Method validation of {@code @Validated} beans when
     * {@code spring.validation.method.adapt-constraint-violations} is enabled.
     * Responds like {@link #handleConstraintViolation}, so enabling the property
     * does not change the response.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodValidationException(
            MethodValidationException ex, HttpHeaders headers,
            HttpStatus status, WebRequest request) {

        // Invalid return value, a server error
        if (ex.isForReturnValue()) {
            log.error("Invalid return value of {} on {}", ex.getMethod(), uriOf(request), ex);
            return super.handleMethodValidationException(ex, headers, status, request);
        }

        List<Map<String, String>> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            boolean prefixFields = !result.getMethodParameter().hasParameterAnnotation(RequestBody.class);
            addErrors(result, parameterName(result), prefixFields, errors);
        }
        addCrossParameterErrors(ex, errors);

        ProblemDetail pd = validationProblem(ErrorCode.CONSTRAINT_VIOLATION, errors,
                "One or more parameters do not meet the constraints", uriOf(request));
        return handleExceptionInternal(ex, pd, headers, ErrorCode.CONSTRAINT_VIOLATION.getHttpStatus(), request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        ProblemDetail pd = factory.create(
                ErrorCode.MALFORMED_REQUEST,
                factory.detail(ErrorCode.MALFORMED_REQUEST, "The request body is malformed or cannot be read"),
                uriOf(request));

        return handleExceptionInternal(ex, pd, headers, ErrorCode.MALFORMED_REQUEST.getHttpStatus(), request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        String detail;
        if (ex instanceof MethodArgumentTypeMismatchException mate && mate.getRequiredType() != null) {
            detail = factory.message(ProblemDetailsFactory.DETAIL_KEY_PREFIX + "TYPE_MISMATCH.parameter",
                    "Parameter ''{0}'' must be of type {1}",
                    mate.getName(), mate.getRequiredType().getSimpleName());
        } else {
            detail = factory.detail(ErrorCode.TYPE_MISMATCH, "The provided value does not have the expected type");
        }

        ProblemDetail pd = factory.create(ErrorCode.TYPE_MISMATCH, detail, uriOf(request));
        return handleExceptionInternal(ex, pd, headers, ErrorCode.TYPE_MISMATCH.getHttpStatus(), request);
    }

    /**
     * Unsupported {@code Content-Type} header (e.g. {@code application/xml} to a JSON endpoint).
     * Responds with 415 {@link HttpStatus#UNSUPPORTED_MEDIA_TYPE} and type {@code /problems/UNSUPPORTED_MEDIA_TYPE}.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        String detail;
        if (ex.getContentType() != null) {
            detail = factory.message(
                    "problemDetail.org.springframework.web.HttpMediaTypeNotSupportedException",
                    "Content type ''{0}'' is not supported",
                    ex.getContentType());
        } else if (ex.getDetailMessageCode().endsWith(".parseError")) {
            detail = factory.message(ex.getDetailMessageCode(), "Could not parse Content-Type.");
        } else {
            detail = factory.detail(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Content type is not supported");
        }

        ProblemDetail pd = factory.create(ErrorCode.UNSUPPORTED_MEDIA_TYPE, detail, uriOf(request));
        return handleExceptionInternal(ex, pd, headers, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getHttpStatus(), request);
    }

    /**
     * Request body exceeds the maximum permitted size configured on the server
     * (e.g. {@code server.tomcat.max-http-form-post-size}, {@code max-swallow-size}).
     * Responds with 413 {@link HttpStatus#CONTENT_TOO_LARGE} and type {@code /problems/CONTENT_TOO_LARGE}.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        log.warn("Payload too large on {}: limit {} bytes", uriOf(request), ex.getMaxUploadSize());
        String detail = ex.getMaxUploadSize() > 0
                ? factory.message(ProblemDetailsFactory.DETAIL_KEY_PREFIX + "CONTENT_TOO_LARGE.limit",
                        "The request body exceeds the maximum allowed size of {0} bytes", ex.getMaxUploadSize())
                : factory.detail(ErrorCode.CONTENT_TOO_LARGE, "The request body exceeds the maximum permitted size");

        ProblemDetail pd = factory.create(ErrorCode.CONTENT_TOO_LARGE, detail, uriOf(request));
        return handleExceptionInternal(ex, pd, headers, ErrorCode.CONTENT_TOO_LARGE.getHttpStatus(), request);
    }

    // Common exit point of ResponseEntityExceptionHandler

    /**
     * Completes the ProblemDetail of every response with the missing fields.
     * Returns {@code null} when the response was already sent.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        ResponseEntity<Object> responseEntity = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (responseEntity == null) {
            return null;
        }
        if (responseEntity.getBody() instanceof ProblemDetail pd) {
            // Only problems built by Spring MVC, the ones of this handler already have a code
            Map<String, Object> props = pd.getProperties();
            if (props == null || !props.containsKey(ProblemDetailsFactory.PROP_CODE)) {
                translateFrameworkDetail(ex, pd);
            }
            factory.complete(pd, responseEntity.getStatusCode().value(), uriOf(request));
        }
        return responseEntity;
    }

    // Utilities

    // Spring MVC detail in the configured language, unless the application translates it
    private void translateFrameworkDetail(Exception ex, ProblemDetail pd) {
        String code;
        Object[] args;
        if (ex instanceof ErrorResponse errorResponse) {
            code = errorResponse.getDetailMessageCode();
            args = errorResponse.getDetailMessageArguments();
        } else {
            code = ErrorResponse.getDefaultDetailMessageCode(ex.getClass(), null);
            args = null;
        }
        String detail = factory.resolve(code, args);
        if (detail != null) {
            pd.setDetail(detail);
        }
    }

    // Common shape of validation errors, the detail lists the messages
    private ProblemDetail validationProblem(ErrorCode code, List<Map<String, String>> errors,
                                            String genericDetail, @Nullable String uri) {
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

    // Errors of one parameter. With prefixFields the fields of a validated bean
    // include the parameter, as in the path of a ConstraintViolation: "user.email"
    private static void addErrors(ParameterValidationResult result, String parameter,
                                  boolean prefixFields, List<Map<String, String>> errors) {
        if (result instanceof ParameterErrors parameterErrors) {
            parameterErrors.getFieldErrors().forEach(e -> errors.add(error(
                    prefixFields ? parameter + "." + e.getField() : e.getField(),
                    messageOf(e, "Invalid value"))));
            parameterErrors.getGlobalErrors().forEach(e ->
                    errors.add(error(parameter, messageOf(e, "Invalid data"))));
        } else {
            result.getResolvableErrors().forEach(e ->
                    errors.add(error(parameter, messageOf(e, "Invalid value"))));
        }
    }

    private static void addCrossParameterErrors(MethodValidationResult result, List<Map<String, String>> errors) {
        result.getCrossParameterValidationResults().forEach(e ->
                errors.add(error("", messageOf(e, "Invalid parameters"))));
    }

    // Name seen by the client: the one of the web annotation (@RequestParam,
    // @PathVariable, @RequestHeader...), otherwise the Java parameter name.
    // Read directly instead of HandlerMethodValidationException.visitResults,
    // which fails on @RequestPart and @ModelAttribute without ParameterErrors.
    private static String parameterName(ParameterValidationResult result) {
        MethodParameter parameter = result.getMethodParameter();
        String name = annotatedName(parameter);
        return withContainerPosition(name != null ? name : declaredName(parameter), result);
    }

    private static @Nullable String annotatedName(MethodParameter parameter) {
        for (Annotation annotation : parameter.getParameterAnnotations()) {
            if (annotation instanceof RequestParam a) {
                return firstWithText(a.name(), a.value());
            }
            if (annotation instanceof PathVariable a) {
                return firstWithText(a.name(), a.value());
            }
            if (annotation instanceof RequestHeader a) {
                return firstWithText(a.name(), a.value());
            }
            if (annotation instanceof CookieValue a) {
                return firstWithText(a.name(), a.value());
            }
            if (annotation instanceof MatrixVariable a) {
                return firstWithText(a.name(), a.value());
            }
            if (annotation instanceof RequestPart a) {
                return firstWithText(a.name(), a.value());
            }
            if (annotation instanceof ModelAttribute a) {
                return firstWithText(a.name(), a.value());
            }
        }
        return null;
    }

    private static String declaredName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        if (name != null) {
            return name;
        }
        return parameter.getParameterIndex() >= 0
                ? parameter.getParameter().getName()
                : "arg" + parameter.getParameterIndex();
    }

    // Element of a List or Map parameter: "ids[2]", "filters[status]"
    private static String withContainerPosition(String name, ParameterValidationResult result) {
        if (result.getContainerIndex() != null) {
            return name + "[" + result.getContainerIndex() + "]";
        }
        if (result.getContainerKey() != null) {
            return name + "[" + result.getContainerKey() + "]";
        }
        return name;
    }

    private static @Nullable String firstWithText(@Nullable String a, @Nullable String b) {
        if (StringUtils.hasText(a)) {
            return a;
        }
        return StringUtils.hasText(b) ? b : null;
    }

    // Path of the violation without the method name: "page" instead of "list.page"
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

    // DispatcherServlet exposes the current response
    private static void applyHeaders(@Nullable Map<String, String> headers) {
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

    private static @Nullable String uriOf(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return null;
    }
}
