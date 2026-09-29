package io.github.neisserdev.problemdetails;

/**
 * Proporciona el identificador de la traza de la petición en curso.
 *
 * <p>Con Micrometer Tracing en el classpath se registra uno automáticamente.
 * Un bean propio de este tipo lo sustituye.
 */
@FunctionalInterface
public interface TraceIdProvider {

    /**
     * @return el traceId actual, o {@code null} si no hay traza
     */
    String currentTraceId();
}
