package io.github.neisserdev.problemdetails;

import org.springframework.http.HttpStatusCode;

/**
 * Tipo de problema en el sentido de RFC 9457: un código estable, un título
 * legible y el status HTTP que le corresponde.
 *
 * <p>El catálogo que trae la librería es {@link ErrorCode}. Para los errores
 * propios de una aplicación se declara un enum que implemente esta interfaz y
 * se lanza una subclase de {@link BusinessException} que lo transporte. El
 * manejador global no se toca: traduce cualquier {@code ProblemType}.
 *
 * <p>El código forma parte del miembro {@code type} (se concatena a la base
 * configurada), así que debe ser seguro dentro de un URI. La convención es
 * {@code MAYUSCULAS_CON_GUIONES_BAJOS}, que es justo lo que devuelve
 * {@code name()} en un enum.
 *
 * @see ErrorCode
 * @see BusinessException
 */
public interface ProblemType {

    /**
     * Código estable del error. Se expone como extensión {@code code} y como
     * último segmento del {@code type}.
     *
     * @return el código, sin espacios ni caracteres reservados de URI
     */
    String getCode();

    /**
     * Resumen legible del tipo de problema. No debería cambiar entre
     * ocurrencias; lo específico de cada caso va en el {@code detail}.
     *
     * @return el título
     */
    String getTitle();

    /**
     * Status HTTP con el que se responde este problema.
     *
     * @return el status
     */
    HttpStatusCode getHttpStatus();
}
