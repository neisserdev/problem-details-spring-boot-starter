# problem-details-spring-boot-starter

Implementación estándar de manejo de errores basados en RFC 9457 (Problem Details) para Spring Boot 4 y Spring MVC. Al integrar esta dependencia, toda la API unifica sus respuestas de error bajo el formato `application/problem+json`, abarcando la capa de dominio, el enrutamiento de Spring MVC, las validaciones y Spring Security.

Requisitos: Java 17 o superior, Spring Boot 4 y entorno servlet (Spring MVC). WebFlux no está soportado.

## Instalación

### Maven

```xml
<dependency>
    <groupId>io.github.neisserdev</groupId>
    <artifactId>problem-details-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```kotlin
implementation("io.github.neisserdev:problem-details-spring-boot-starter:1.0.0")
```

El starter es ligero y no impone dependencias transitivas. Se adapta dinámicamente a las dependencias presentes en el classpath del proyecto (`spring-boot-starter-webmvc`, `spring-boot-starter-security`, etc.).

## Registro Automático de Componentes

La autoconfiguración inicializa los siguientes componentes según el contexto:

| Bean | Condición de registro |
|------|-----------------------|
| `ProblemDetailsFactory` | Siempre activo en aplicaciones servlet. |
| `GlobalExceptionHandler` | Activo si no existe otro `ResponseEntityExceptionHandler` en el contexto. |
| `ProblemJsonWriter` | Activo si existe un `JsonMapper` de Jackson 3 (provisto por defecto en Spring Boot). |
| `SecurityAuthenticationEntryPoint` (401) | Activo si Spring Security está presente y `problem-details.security.enabled` es `true`. |
| `SecurityAccessDeniedHandler` (403) | Activo si Spring Security está presente y `problem-details.security.enabled` es `true`. |
| Conexión con `http.exceptionHandling()` | Configuración automática inyectada en el `SecurityFilterChain`. |
| `TraceIdProvider` | Activo si Micrometer Tracing está presente y `problem-details.trace-id.enabled` es `true`. |

Estos beans retroceden (back-off) automáticamente si se declara un componente personalizado del mismo tipo. No es necesario habilitar explícitamente `spring.mvc.problemdetails.enabled`; el manejador del starter sustituye la implementación por defecto de Spring.

## Formato de la Respuesta

```json
{
  "type": "https://api.ejemplo.com/problemas/RESOURCE_NOT_FOUND",
  "title": "Recurso no encontrado",
  "status": 404,
  "detail": "Pedido con id 7 no encontrado",
  "instance": "/pedidos/7",
  "timestamp": "2026-09-27T18:42:10.512Z",
  "code": "RESOURCE_NOT_FOUND",
  "resource": "Pedido",
  "resourceId": "7"
}
```

- `type`: URI base configurada concatenada con el código de error.
- `code`: Identificador estable presente en toda respuesta, incluyendo los errores generados por el framework (404, 405, 415, 401). Permite a los clientes de la API implementar lógicas condicionales (`switch`) sin depender exclusivamente del status HTTP.
- `timestamp` e `instance`: Atributos garantizados en cada respuesta.
- Los miembros de extensión (`resource`, `resourceId`) se serializan en el nivel raíz del JSON, en estricto cumplimiento del RFC.

## Configuración

```yaml
problem-details:
  base-type-url: https://api.ejemplo.com/problemas/   # Valor por defecto: /problems/
  security:
    enabled: true              # false: desactiva la inyección automática para 401 y 403
    www-authenticate: Bearer   # vacío: omite la cabecera en respuestas 401
  trace-id:
    enabled: true              # false: no incluye el traceId aunque haya trazas
```

La propiedad `base-type-url` admite rutas relativas (permitidas por el RFC si incluyen la ruta completa) o URLs absolutas. Para entornos productivos, se recomienda una URL resoluble que apunte a la documentación de los códigos de error. El sistema de inicialización valida la correcta formación de la URI durante el arranque y añade una barra final si la base no termina en `/`, `:`, `#` o `=`, previniendo fallos silenciosos en producción.

Nota: `security.enabled=false` desactiva únicamente la integración automática con Spring Security. `ProblemJsonWriter` permanece disponible para su uso en filtros personalizados.

## Excepciones Base

El starter incluye excepciones predefinidas para los flujos de negocio más comunes:

```java
throw new ResourceNotFoundException("Pedido", id);                   // 404
throw new ResourceConflictException("El email ya está registrado");  // 409
throw new BusinessRuleViolationException("El pedido ya se envió");   // 422
```

Catálogo predefinido en `ErrorCode`:

| Código | Status HTTP |
|--------|-------------|
| `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `TYPE_MISMATCH`, `CONSTRAINT_VIOLATION` | 400 |
| `UNAUTHORIZED`, `INVALID_CREDENTIALS` | 401 |
| `ACCESS_DENIED` | 403 |
| `RESOURCE_NOT_FOUND`, `ENDPOINT_NOT_FOUND` | 404 |
| `METHOD_NOT_ALLOWED` | 405 |
| `NOT_ACCEPTABLE` | 406 |
| `RESOURCE_CONFLICT` | 409 |
| `CONTENT_TOO_LARGE` | 413 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 |
| `BUSINESS_RULE_VIOLATION` | 422 |
| `TOO_MANY_REQUESTS` | 429 |
| `INTERNAL_ERROR` | 500 |
| `SERVICE_UNAVAILABLE` | 503 |

La nomenclatura se alinea con el estándar RFC 9110 y Spring Framework 7. Si el framework emite un estado HTTP no contemplado explícitamente en la tabla, el código de negocio se deriva semánticamente del status.

## Extensibilidad de Dominio

El diseño permite extender el manejo de errores definiendo catálogos de negocio propios. Se requiere implementar la interfaz `ProblemType` y crear una excepción que extienda de `BusinessException`:

```java
public enum ErroresDeTienda implements ProblemType {

    STOCK_INSUFICIENTE("Stock insuficiente", HttpStatus.CONFLICT),
    CUPON_CADUCADO("Cupón caducado", HttpStatus.UNPROCESSABLE_CONTENT);

    private final String titulo;
    private final HttpStatus status;

    ErroresDeTienda(String titulo, HttpStatus status) {
        this.titulo = titulo;
        this.status = status;
    }

    @Override public String getCode() { return name(); }
    @Override public String getTitle() { return titulo; }
    @Override public HttpStatusCode getHttpStatus() { return status; }
}
```

```java
public class StockInsuficienteException extends BusinessException {

    private final int disponible;

    public StockInsuficienteException(int disponible) {
        super(ErroresDeTienda.STOCK_INSUFICIENTE, "Solo quedan %d unidades".formatted(disponible));
        this.disponible = disponible;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("disponible", disponible);   // Se inyecta en la raíz del JSON
    }
}
```

El identificador `code` debe ser apto para componer una URI (convención recomendada: `MAYUSCULAS_CON_GUIONES_BAJOS`). Las claves `code` y `timestamp` están reservadas por el estándar y se omitirán si se incluyen de forma duplicada en `getProperties()`.

Las cabeceras HTTP de la respuesta se declaran sobrescribiendo `getHeaders()`, por ejemplo para indicar cuándo reintentar:

```java
@Override
public Map<String, String> getHeaders() {
    return Map.of("Retry-After", "120");
}
```

## Identificador de Traza

Con Micrometer Tracing en el proyecto (por ejemplo mediante `spring-boot-starter-opentelemetry` o `spring-boot-starter-zipkin`), cada respuesta de error incluye el `traceId` de la petición:

```json
{
  "type": "/problems/INTERNAL_ERROR",
  "status": 500,
  "code": "INTERNAL_ERROR",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

El cliente puede reportar ese valor y la petición se localiza directamente en los logs y en el sistema de trazas. Sin Micrometer Tracing, o si la petición no tiene traza activa, el atributo no aparece. Se desactiva con `problem-details.trace-id.enabled=false`.

Para otra fuente de trazas, por ejemplo el agente de OpenTelemetry, basta con declarar un bean propio:

```java
@Bean
TraceIdProvider traceIdProvider() {
    return () -> MDC.get("trace_id");
}
```

## Conflictos de Datos

Las excepciones de la capa de acceso a datos de Spring responden 409 `RESOURCE_CONFLICT`:

- `DataIntegrityViolationException` y sus subclases, como `DuplicateKeyException`: violaciones de restricciones de la base de datos (clave única, clave foránea, etc.).
- `OptimisticLockingFailureException` y sus subclases: conflictos de bloqueo optimista con `@Version`.

El detalle es un texto genérico. El mensaje original de la base de datos no se expone al cliente, ya que revela nombres de tablas y restricciones. Estas excepciones se detectan sin requerir Spring Data en el classpath, y un `@ExceptionHandler` propio para ellas tiene prioridad sobre este comportamiento.

## Validación de Datos

Las distintas vías de validación unifican su salida bajo una misma estructura para simplificar el consumo por parte de la aplicación cliente:

- `@Valid @RequestBody` genera `VALIDATION_ERROR`.
- Restricciones en `@RequestParam` o `@PathVariable` generan `CONSTRAINT_VIOLATION`.
- Violaciones a nivel de clase (`@Validated` / `ConstraintViolationException`) generan `CONSTRAINT_VIOLATION`.

```json
{
  "type": "/problems/VALIDATION_ERROR",
  "title": "Error de validación",
  "status": 400,
  "detail": "no debe estar vacío, debe ser una dirección de correo electrónico con formato correcto",
  "instance": "/usuarios",
  "timestamp": "2026-09-27T18:42:10.512Z",
  "code": "VALIDATION_ERROR",
  "errors": [
    { "field": "nombre", "detail": "no debe estar vacío" },
    { "field": "email", "detail": "debe ser una dirección de correo electrónico con formato correcto" }
  ],
  "count": 2
}
```

En los parámetros de entrada, el atributo `field` refleja el nombre expuesto en la API (`@RequestParam("n")` se serializa como `"n"`). Los errores de validación a nivel de clase que involucran múltiples propiedades exponen el atributo `field` vacío.

## Internacionalización

Los títulos y los detalles fijos se resuelven con el `MessageSource` de la aplicación según el idioma de la petición (`Accept-Language`). Si una clave no está definida se usa el texto en español incluido en el starter.

| Clave | Uso |
|-------|-----|
| `problemDetails.title.<CODIGO>` | Título de cualquier `ProblemType`, incluidos los catálogos propios. |
| `problemDetails.detail.<CODIGO>` | Detalle fijo de `INTERNAL_ERROR`, `MALFORMED_REQUEST`, `TYPE_MISMATCH`, `VALIDATION_ERROR`, `CONSTRAINT_VIOLATION`, `INVALID_CREDENTIALS`, `UNAUTHORIZED` y `ACCESS_DENIED`. |
| `problemDetails.detail.TYPE_MISMATCH.parameter` | Detalle de `TYPE_MISMATCH` con el nombre (`{0}`) y el tipo (`{1}`) del parámetro. |
| `problemDetails.detail.RESOURCE_CONFLICT.integrity` | Detalle de las violaciones de restricciones de la base de datos. |
| `problemDetails.detail.RESOURCE_CONFLICT.concurrency` | Detalle de los conflictos de bloqueo optimista. |

Ejemplo de `messages_en.properties`:

```properties
problemDetails.title.RESOURCE_NOT_FOUND=Resource not found
problemDetails.detail.UNAUTHORIZED=Authentication is required to access this resource
problemDetails.detail.TYPE_MISMATCH.parameter=Parameter ''{0}'' must be of type {1}
```

Los mensajes con argumentos siguen el formato de `MessageFormat`, por lo que las comillas simples se escriben dobles (`''`). El `reason` de `@ResponseStatus` también se resuelve como clave de mensaje, igual que en Spring MVC. El `detail` de las excepciones de negocio es el mensaje de la propia excepción y no se traduce. Los mensajes de validación provienen de Bean Validation y los detalles de los errores de Spring MVC se personalizan con las claves de Spring (`problemDetail.<clase de la excepción>`).

## Registro de Errores

Las excepciones de negocio y las anotadas con `@ResponseStatus` se registran en nivel DEBUG si son 4xx y en ERROR con la traza completa si son 5xx. Los conflictos de datos se registran en DEBUG y las excepciones no controladas siempre en ERROR. Para ver los 4xx durante el desarrollo:

```yaml
logging:
  level:
    io.github.neisserdev.problemdetails: debug
```

## Integración con Spring Security

Con Spring Security presente, el starter inyecta el entry point (401) y el handler (403) en la cadena de filtros mediante beans `Customizer` de Spring Security 7. La configuración declarativa mantiene una estructura limpia:

```java
@Bean
SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http
            .authorizeHttpRequests(peticiones -> peticiones
                    .requestMatchers("/publico/**").permitAll()
                    .anyRequest().authenticated())
            .build();
}
```

Comportamiento estándar:

- Petición anónima a recurso protegido: 401 `UNAUTHORIZED` con cabecera `WWW-Authenticate`.
- Usuario autenticado sin permisos: 403 `ACCESS_DENIED`.
- `AccessDeniedException` en capa de servicio: (ej. mediante `@PreAuthorize`). El manejador delega a Spring Security, retornando 401 para usuarios anónimos y 403 para autenticados, corrigiendo la respuesta 403 por defecto de Spring MVC.
- `BadCredentialsException` en controlador: 401 `INVALID_CREDENTIALS`.

Si la configuración invoca `.exceptionHandling(...)` explícitamente, esta tiene precedencia sobre la autoconfiguración. Para componentes de seguridad que capturan errores antes de llegar al `exceptionHandling` (como `oauth2ResourceServer` con un token caducado o `httpBasic`), es necesario pasar explícitamente la referencia del entry point:

```java
@Bean
SecurityFilterChain api(HttpSecurity http, SecurityAuthenticationEntryPoint entryPoint) throws Exception {
    return http
            .oauth2ResourceServer(oauth -> oauth
                    .jwt(Customizer.withDefaults())
                    .authenticationEntryPoint(entryPoint))
            .authorizeHttpRequests(peticiones -> peticiones.anyRequest().authenticated())
            .build();
}
```

## Manejo de Errores en Filtros Personalizados

Las excepciones originadas dentro de filtros (`Filter` o `OncePerRequestFilter`) no son interceptadas por los `@RestControllerAdvice`. Para serializar la respuesta bajo el mismo formato RFC 9457 en la capa de filtros, se debe inyectar el componente `ProblemJsonWriter`:

```java
@Component
class LimiteDePeticionesFilter extends OncePerRequestFilter {

    private final ProblemJsonWriter writer;

    LimiteDePeticionesFilter(ProblemJsonWriter writer) {
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (superaElLimite(request)) {
            writer.write(response, request, ErrorCode.TOO_MANY_REQUESTS,
                    "Has superado el límite de peticiones", "Retry-After", "30");
            return;
        }
        chain.doFilter(request, response);
    }
}
```

Esta herramienta utiliza el `JsonMapper` central para garantizar que los atributos de extensión se acoplen correctamente en el nivel raíz del documento.

## Modificación del Comportamiento Base

Los componentes `@RestControllerAdvice` del proyecto tienen prioridad sobre el manejador global del starter. Para sobrescribir una respuesta específica, basta con manejar la excepción en un componente local. Para alterar el comportamiento general, se debe extender la clase `GlobalExceptionHandler`. La autoconfiguración detectará la nueva instancia y desactivará el manejador por defecto:

```java
@RestControllerAdvice
class ManejadorDeErrores extends GlobalExceptionHandler {

    ManejadorDeErrores(ProblemDetailsFactory fabrica, ProblemDetailsProperties propiedades) {
        super(fabrica, propiedades.getSecurity().isEnabled());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ProblemDetail entidadNoEncontrada(EntityNotFoundException ex, HttpServletRequest request) {
        return getFactory().create(ErrorCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no existe", request.getRequestURI());
    }
}
```

Este patrón de sustitución aplica a cualquier componente central (`ProblemDetailsFactory`, `ProblemJsonWriter`, Entry Points).

## Limitaciones

- Compatibilidad exclusiva con entornos Servlet (Spring MVC). No aplicable a aplicaciones WebFlux.
- Excepciones no capturadas en filtros personalizados son delegadas al `BasicErrorController` de Spring Boot. Se recomienda implementar bloques `try/catch` y utilizar `ProblemJsonWriter`.
- Compilado con Spring Boot 4.1.1 (Spring Framework 7.0, Spring Security 7.1, Jackson 3). El CI ejecuta las pruebas con Spring Boot 4.0.8 y 4.1.1 sobre Java 17, 21 y 25.

## Licencia

MIT
