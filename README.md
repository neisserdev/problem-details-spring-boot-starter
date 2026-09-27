# problem-details-spring-boot-starter

Manejo de errores [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (Problem Details) para Spring Boot 4 y Spring MVC. Se añade la dependencia y toda la API responde los errores con el mismo `application/problem+json`, vengan de tu dominio, de Spring MVC, de la validación o de Spring Security.

Requisitos: Java 17 o superior, Spring Boot 4 y una aplicación servlet (Spring MVC). WebFlux no está soportado.

## Instalación

Maven

```xml
<dependency>
    <groupId>io.github.neisserdev</groupId>
    <artifactId>problem-details-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Gradle

```kotlin
implementation("io.github.neisserdev:problem-details-spring-boot-starter:0.1.0")
```

El starter no arrastra Spring MVC, Jackson ni Spring Security. Usa los que ya traiga tu proyecto (`spring-boot-starter-webmvc`, `spring-boot-starter-security`...) y se adapta a lo que encuentre.

## Qué registra

Sin escribir código ni configuración:

| Bean | Cuándo |
|------|--------|
| `ProblemDetailsFactory` | Siempre en una app servlet |
| `GlobalExceptionHandler` | Si no defines otro `ResponseEntityExceptionHandler` |
| `ProblemJsonWriter` | Si hay un `JsonMapper` de Jackson 3 (lo crea Spring Boot) |
| `SecurityAuthenticationEntryPoint` (401) | Si Spring Security está en el classpath y `problem-details.security.enabled` no es `false` |
| `SecurityAccessDeniedHandler` (403) | Igual que el anterior |
| Conexión con `http.exceptionHandling()` | Igual que el anterior; no hace falta tocar tu `SecurityFilterChain` |

Todos se retiran si declaras un bean propio del mismo tipo. Tampoco hace falta `spring.mvc.problemdetails.enabled`: el manejador del starter sustituye al que activa esa propiedad.

## Forma de la respuesta

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

- `type` es la base configurada más el código.
- `code` es estable y está en toda respuesta, incluidos los 404, 405, 415 o 401 que genera el framework. Un cliente puede hacer `switch` sobre él sin mirar el status.
- `timestamp` e `instance` también están siempre.
- Los miembros de extensión (`resource`, `resourceId`...) van en la raíz del JSON, como pide el RFC.

## Configuración

```yaml
problem-details:
  base-type-url: https://api.ejemplo.com/problemas/   # por defecto /problems/
  security:
    enabled: true              # false: no registra ni conecta el 401 y el 403
    www-authenticate: Bearer   # vacío: no se envía la cabecera en los 401
```

`base-type-url` admite una ruta relativa (el RFC la permite si incluye la ruta completa) o una URL absoluta. Lo recomendable en producción es una URL tuya que sirva documentación de cada código. Si no termina en `/`, `:`, `#` o `=` se le añade una barra, y si no forma un URI válido la aplicación no arranca, para enterarte en el despliegue y no con el primer error en producción.

`security.enabled=false` desactiva solo la integración con Spring Security. `ProblemJsonWriter` sigue registrado porque no es exclusivo de seguridad: lo usan también filtros propios como un limitador de peticiones (ver más abajo).

## Lanzar errores

La librería trae excepciones para los casos habituales:

```java
throw new ResourceNotFoundException("Pedido", id);                  // 404
throw new ResourceConflictException("El email ya está registrado");  // 409
throw new BusinessRuleViolationException("El pedido ya se envió");   // 422
```

Y un catálogo de códigos en `ErrorCode`:

| Código | Status |
|--------|--------|
| `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `TYPE_MISMATCH`, `CONSTRAINT_VIOLATION` | 400 |
| `UNAUTHORIZED`, `INVALID_CREDENTIALS`, `INVALID_REFRESH_TOKEN`, `ACCOUNT_UNAVAILABLE` | 401 |
| `ACCESS_DENIED`, `ORIGIN_NOT_ALLOWED` | 403 |
| `RESOURCE_NOT_FOUND`, `ENDPOINT_NOT_FOUND` | 404 |
| `METHOD_NOT_ALLOWED` | 405 |
| `NOT_ACCEPTABLE` | 406 |
| `EMAIL_ALREADY_REGISTERED`, `RESOURCE_CONFLICT` | 409 |
| `CONTENT_TOO_LARGE` | 413 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 |
| `BUSINESS_RULE_VIOLATION` | 422 |
| `TOO_MANY_REQUESTS` | 429 |
| `INTERNAL_ERROR` | 500 |
| `SERVICE_UNAVAILABLE` | 503 |

Los nombres siguen RFC 9110, igual que Spring Framework 7 (que deprecó `PAYLOAD_TOO_LARGE` y `UNPROCESSABLE_ENTITY`). Si el framework responde con un status que no está en la tabla, como un 410, el código se deriva del status (`GONE`) y nunca de una constante deprecada de Spring.

## Errores propios

No hace falta tocar la librería. Declara tu catálogo implementando `ProblemType` y una excepción que extienda `BusinessException`:

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
        return Map.of("disponible", disponible);   // se añade a la raíz del JSON
    }
}
```

El código tiene que poder ir dentro de un URI; la convención es `MAYUSCULAS_CON_GUIONES_BAJOS`. Las claves `code` y `timestamp` están reservadas y se ignoran si aparecen en `getProperties()`.

## Validación

Las tres vías de validación responden con la misma forma, así el cliente procesa los errores de una sola manera:

- `@Valid @RequestBody` produce `VALIDATION_ERROR`.
- Restricciones en `@RequestParam` o `@PathVariable` (validación integrada de Spring MVC) producen `CONSTRAINT_VIOLATION`.
- Restricciones en clases con `@Validated` (`ConstraintViolationException`) producen `CONSTRAINT_VIOLATION`.

```json
{
  "type": "/problems/VALIDATION_ERROR",
  "title": "Error de validación",
  "status": 400,
  "detail": "no debe estar vacío debe ser una dirección de correo electrónico con formato correcto",
  "instance": "/usuarios",
  "timestamp": "2026-09-27T18:42:10.512Z",
  "code": "VALIDATION_ERROR",
  "errores": [
    { "campo": "nombre", "mensaje": "no debe estar vacío" },
    { "campo": "email", "mensaje": "debe ser una dirección de correo electrónico con formato correcto" }
  ],
  "count": 2
}
```

En los parámetros, `campo` es el nombre que ve el cliente (`@RequestParam("n")` sale como `n`). Los fallos de reglas de clase entre varios campos salen con `campo` vacío.

## Spring Security

Con Spring Security en el classpath el starter conecta solo el entry point 401 y el manejador 403 a cada `HttpSecurity`, gracias a los beans `Customizer` que Spring Security 7 aplica automáticamente. Tu cadena puede quedarse así:

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

Comportamiento:

- Petición anónima a un recurso protegido: 401 `UNAUTHORIZED` con `WWW-Authenticate: Bearer`.
- Usuario autenticado sin permisos: 403 `ACCESS_DENIED`.
- `AccessDeniedException` lanzada dentro de un controlador o servicio (por ejemplo por `@PreAuthorize`): el manejador la devuelve a Spring Security, que responde 401 si la petición es anónima y 403 si no. Antes se respondía siempre 403, que es incorrecto para un anónimo.
- `BadCredentialsException` que llega al controlador (un login que llama al `AuthenticationManager`): 401 `INVALID_CREDENTIALS`.

Si tu cadena llama a `.exceptionHandling(...)` explícitamente, tu configuración gana: la del starter se aplica antes.

Algunos filtros tienen su propio entry point y no pasan por `exceptionHandling()`. Es el caso de `oauth2ResourceServer` con un token inválido o caducado y de `httpBasic` con credenciales incorrectas. Para que también respondan en problem+json, pásales el bean:

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

Si usas un filtro JWT propio que rechaza tokens por su cuenta, inyecta `ProblemJsonWriter` como en la sección siguiente.

## Filtros propios

Los errores que ocurren en un filtro no llegan a ningún `@RestControllerAdvice`. `ProblemJsonWriter` escribe la misma respuesta que produciría el manejador:

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
            writer.escribir(response, request, ErrorCode.TOO_MANY_REQUESTS,
                    "Has superado el límite de peticiones", "Retry-After", "30");
            return;
        }
        chain.doFilter(request, response);
    }
}
```

Usa el `JsonMapper` de Spring Boot, que es el que sabe poner las extensiones en la raíz del JSON. Con un `JsonMapper` construido a mano, `code` y `timestamp` saldrían anidados bajo `properties`.

## Personalizar

Tus `@RestControllerAdvice` se consultan antes que el del starter, que va con la menor precedencia. Para una excepción concreta basta con manejarla en tu propio advice.

Para cambiar el comportamiento general, extiende el manejador. Al existir tu subclase, el del starter se retira:

```java
@RestControllerAdvice
class ManejadorDeErrores extends GlobalExceptionHandler {

    ManejadorDeErrores(ProblemDetailsFactory fabrica, ProblemDetailsProperties propiedades) {
        super(fabrica, propiedades.getSecurity().isEnabled());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail edicionConcurrente(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return getFabrica().crear(ErrorCode.RESOURCE_CONFLICT,
                "Otro usuario modificó el recurso mientras lo editabas", request.getRequestURI());
    }
}
```

Cualquier otro bean (`ProblemDetailsFactory`, `ProblemJsonWriter`, el entry point o el manejador 403) se sustituye declarando uno propio del mismo tipo.

## Limitaciones

- Solo Spring MVC. En WebFlux la autoconfiguración no se activa.
- Una excepción lanzada en un filtro propio que no la capture termina en la página `/error` de Spring Boot, que tiene su propio formato. Captúrala y usa `ProblemJsonWriter`.
- Compilado y probado con Spring Boot 4.1.1 (Spring Framework 7.0, Spring Security 7.1, Jackson 3). Las APIs que usa existen desde Boot 4.0, pero 4.0.x no está en las pruebas.

## Migrar desde dev.neisser.exceptions

1. Borra el paquete copiado en tu proyecto y añade la dependencia.
2. Cambia los imports de `dev.neisser.exceptions` a `io.github.neisserdev.problemdetails` (y `.security`).
3. Quita el cableado manual de `SecurityConfig`. Si prefieres dejar `.exceptionHandling(...)`, inyecta los beans del starter en lugar de instanciar las clases.
4. `BusinessException.getErrorCode()` pasa a ser `getProblemType()` y devuelve un `ProblemType`. `ErrorCode` lo implementa, así que `ErrorCode.X` sigue funcionando donde lo usabas.
5. La clase estática `ProblemDetails` pasa a ser el bean `ProblemDetailsFactory` (`of` se llama `crear`, `typeDe` se llama `tipoDe`).
6. `ErrorCode.PAYLOAD_TOO_LARGE` pasa a ser `CONTENT_TOO_LARGE`, y cambia el `code` que ve el cliente en los 413.
7. `ErrorCode.porStatus(int)` devuelve `Optional`. Un status sin representante ya no sale como `INTERNAL_ERROR` sino con un código derivado (`GONE`, `PAYMENT_REQUIRED`...).
8. Los errores de `ConstraintViolationException` salen en `errores` como lista de `{campo, mensaje}` en vez de `errors` como lista de textos.
9. La base del `type` era fija (`https://neisser.dev/problems/`). Ahora es `/problems/` por defecto; configura `problem-details.base-type-url` si quieres conservar la anterior.
10. El manejador pasó de la mayor a la menor precedencia, para no tapar los advices de la aplicación.
11. `AccessDeniedException` dentro de un controlador responde 401 si la petición es anónima (antes siempre 403).
12. Las excepciones anotadas con `@ResponseStatus` conservan su status en lugar de responder 500.

## Licencia

[MIT](LICENSE)
