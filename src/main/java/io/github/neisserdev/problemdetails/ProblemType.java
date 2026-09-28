package io.github.neisserdev.problemdetails;

import org.springframework.http.HttpStatusCode;

/**
 * Tipo de problema según RFC 9457. Define el código, el título y el status HTTP.
 *
 * <p>{@link ErrorCode} es el catálogo incluido. Los catálogos propios se
 * declaran como enums que implementan esta interfaz y se lanzan mediante una
 * subclase de {@link BusinessException}.
 *
 * <p>El código forma parte del {@code type}, por lo que debe ser válido dentro
 * de un URI. Convención recomendada: {@code MAYUSCULAS_CON_GUIONES_BAJOS}.
 *
 * @see ErrorCode
 * @see BusinessException
 */
public interface ProblemType {

    /**
     * Código estable del error. Se expone en {@code code} y al final del {@code type}.
     *
     * @return el código, sin espacios ni caracteres reservados de URI
     */
    String getCode();

    /**
     * Resumen del tipo de problema. El detalle de cada caso va en {@code detail}.
     *
     * @return el título
     */
    String getTitle();

    /**
     * Status HTTP de la respuesta.
     *
     * @return el status
     */
    HttpStatusCode getHttpStatus();
}
