/**
 * Manejo de errores RFC 9457 (Problem Details) para Spring MVC.
 *
 * <p>Punto de partida: {@link io.github.neisserdev.problemdetails.ErrorCode}
 * (catálogo de errores), {@link io.github.neisserdev.problemdetails.ProblemType}
 * (para declarar errores propios) y
 * {@link io.github.neisserdev.problemdetails.BusinessException} (base de las
 * excepciones que se traducen a {@code application/problem+json}).
 */
package io.github.neisserdev.problemdetails;
