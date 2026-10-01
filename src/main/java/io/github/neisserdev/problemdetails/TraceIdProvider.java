package io.github.neisserdev.problemdetails;

/**
 * Provides the trace identifier of the current request.
 *
 * <p>One is registered automatically when Micrometer Tracing is on the
 * classpath. A custom bean of this type replaces it.
 */
@FunctionalInterface
public interface TraceIdProvider {

    /**
     * @return the current traceId, or {@code null} when there is no trace
     */
    String currentTraceId();
}
