# problem-details-spring-boot-starter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.neisserdev/problem-details-spring-boot-starter)](https://central.sonatype.com/artifact/io.github.neisserdev/problem-details-spring-boot-starter)
[![CI](https://github.com/neisserdev/problem-details-spring-boot-starter/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/neisserdev/problem-details-spring-boot-starter/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

[🇪🇸 Español](README.es.md) | [🇬🇧 English](README.md)

Implementación estándar de manejo de errores basados en RFC 9457 (Problem Details) para Spring Boot 4 y Spring MVC. Al integrar esta dependencia, toda la API unifica sus respuestas de error bajo el formato `application/problem+json`, abarcando la capa de dominio, el enrutamiento de Spring MVC, las validaciones y Spring Security.

Requisitos: Java 17 o superior, Spring Boot 4 y entorno servlet (Spring MVC). WebFlux no está soportado. Funciona en imágenes nativas de GraalVM, el starter registra las pistas que necesita.

## Instalación

### Maven

```xml
<dependency>
    <groupId>io.github.neisserdev</groupId>
    <artifactId>problem-details-spring-boot-starter</artifactId>
    <version>1.1.1</version>
</dependency>
```

### Gradle

```kotlin
implementation("io.github.neisserdev:problem-details-spring-boot-starter:1.1.1")
```

El starter solo aporta `spring-boot-autoconfigure`, `slf4j-api` y la API de Jakarta Validation. Spring MVC, Jackson, Spring Security y Micrometer Tracing son opcionales y se usan cuando el proyecto ya los incluye (`spring-boot-starter-webmvc`, `spring-boot-starter-security`, etc.).

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

Los textos por defecto están en inglés. Con `problem-details.language: es` se devuelven en español, como en los ejemplos de esta guía.

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
  language: es                 # en (por defecto) | es
  security:
    enabled: true              # false: desactiva la inyección automática para 401 y 403
    www-authenticate: Bearer   # vacío: omite la cabecera en respuestas 401
  trace-id:
    enabled: true              # false: no incluye el traceId aunque haya trazas
```

La propiedad `base-type-url` admite rutas relativas (permitidas por el RFC si incluyen la ruta completa) o URLs absolutas. Para entornos productivos, se recomienda una URL resoluble que apunte a la documentación de los códigos de error. El sistema de inicialización valida la correcta formación de la URI durante el arranque y añade una barra final si la base no termina en `/`, `:`, `#` o `=`, previniendo fallos silenciosos en producción.

`language` define el idioma de los títulos y detalles que aporta el starter. Las traducciones de la aplicación tienen prioridad, como se explica en [Internacionalización](#internacionalización).

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

El `detail` es el mensaje de la excepción. Para traducirlo se devuelve una clave de mensaje y sus argumentos; si la clave no tiene traducción se usa el mensaje:

```java
@Override
public String getDetailMessageCode() {
    return "tienda.stockInsuficiente";   // Solo quedan {0} unidades
}

@Override
public Object[] getDetailMessageArguments() {
    return new Object[] {disponible};
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
- Restricciones en métodos de beans anotados con `@Validated` (`ConstraintViolationException`) generan `CONSTRAINT_VIOLATION`.

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

Los títulos y los detalles se resuelven en este orden:

1. El `MessageSource` de la aplicación, en el idioma de la petición (`Accept-Language`).
2. Los textos incluidos en el starter, en el idioma de `problem-details.language` (`en` por defecto, también `es`).

Con `es` se traducen además los detalles que genera Spring MVC (405, 415, parámetros obligatorios, etc.), salvo que la aplicación defina las claves de Spring (`problemDetail.<clase de la excepción>`). Los archivos `io/github/neisserdev/problemdetails/messages.properties` y `messages_es.properties` del jar contienen todas las claves y sirven de plantilla para otros idiomas.

| Clave | Uso |
|-------|-----|
| `problemDetails.title.<CODIGO>` | Título de cualquier `ProblemType`, incluidos los catálogos propios. |
| `problemDetails.detail.<CODIGO>` | Detalle fijo de `INTERNAL_ERROR`, `MALFORMED_REQUEST`, `TYPE_MISMATCH`, `VALIDATION_ERROR`, `CONSTRAINT_VIOLATION`, `INVALID_CREDENTIALS`, `UNAUTHORIZED` y `ACCESS_DENIED`. |
| `problemDetails.detail.TYPE_MISMATCH.parameter` | Detalle de `TYPE_MISMATCH` con el nombre (`{0}`) y el tipo (`{1}`) del parámetro. |
| `problemDetails.detail.RESOURCE_CONFLICT.integrity` | Detalle de las violaciones de restricciones de la base de datos. |
| `problemDetails.detail.RESOURCE_CONFLICT.concurrency` | Detalle de los conflictos de bloqueo optimista. |
| `problemDetails.detail.RESOURCE_NOT_FOUND.resource` | Detalle de `ResourceNotFoundException` con el recurso (`{0}`) y el id (`{1}`). |

Ejemplo de `messages_fr.properties`:

```properties
problemDetails.title.RESOURCE_NOT_FOUND=Ressource introuvable
problemDetails.detail.UNAUTHORIZED=Une authentification est requise pour accéder à cette ressource
problemDetails.detail.TYPE_MISMATCH.parameter=Le paramètre ''{0}'' doit être de type {1}
```

Los mensajes con argumentos siguen el formato de `MessageFormat`, por lo que las comillas simples se escriben dobles (`''`). El `reason` de `@ResponseStatus` también se resuelve como clave de mensaje, igual que en Spring MVC. Las excepciones de negocio traducen su `detail` con `getDetailMessageCode()`. Los mensajes de validación provienen de Bean Validation y siguen el idioma de la petición.

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
- `AccessDeniedException` en capa de servicio (ej. mediante `@PreAuthorize`): el manejador la delega a Spring Security, que retorna 401 para usuarios anónimos y 403 para autenticados.
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
- Requiere Spring Boot 4.0 o superior (Spring Framework 7, Spring Security 7, Jackson 3). El CI ejecuta las pruebas con Spring Boot 4.0 y con la versión del pom, sobre Java 17, 21 y 25.

## Licencia

MIT
