package io.github.neisserdev.problemdetails.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.github.neisserdev.problemdetails.ProblemDetailsFactory;

/**
 * Configuración del starter, bajo el prefijo {@code problem-details}.
 *
 * <pre>
 * problem-details:
 *   base-type-url: https://api.ejemplo.com/problemas/
 *   security:
 *     enabled: true
 *     www-authenticate: Bearer
 *   trace-id:
 *     enabled: true
 * </pre>
 */
@ConfigurationProperties(prefix = "problem-details")
public class ProblemDetailsProperties {

    /**
     * Base del "type" de cada problema, se le concatena el código del error.
     * Admite rutas relativas o URLs absolutas.
     */
    private String baseTypeUrl = ProblemDetailsFactory.DEFAULT_BASE_TYPE;

    private final Security security = new Security();

    private final TraceId traceId = new TraceId();

    /**
     * @return la base del {@code type}
     */
    public String getBaseTypeUrl() {
        return baseTypeUrl;
    }

    /**
     * @param baseTypeUrl la base del {@code type}
     */
    public void setBaseTypeUrl(String baseTypeUrl) {
        this.baseTypeUrl = baseTypeUrl;
    }

    /**
     * @return la configuración de la integración con Spring Security
     */
    public Security getSecurity() {
        return security;
    }

    /**
     * @return la configuración del traceId
     */
    public TraceId getTraceId() {
        return traceId;
    }

    /** Identificador de traza en las respuestas. */
    public static class TraceId {

        /**
         * Incluye el traceId de la petición cuando hay trazas activas.
         */
        private boolean enabled = true;

        /**
         * @return si se incluye el traceId
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled si se incluye el traceId
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** Integración con Spring Security. */
    public static class Security {

        /**
         * Registra el entry point 401 y el manejador 403 en la cadena de filtros.
         */
        private boolean enabled = true;

        /**
         * Valor de WWW-Authenticate en las respuestas 401. Vacío para omitirla.
         */
        private String wwwAuthenticate = "Bearer";

        /**
         * @return si la integración está activa
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled si la integración está activa
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return el valor de {@code WWW-Authenticate}
         */
        public String getWwwAuthenticate() {
            return wwwAuthenticate;
        }

        /**
         * @param wwwAuthenticate el valor de {@code WWW-Authenticate}
         */
        public void setWwwAuthenticate(String wwwAuthenticate) {
            this.wwwAuthenticate = wwwAuthenticate;
        }
    }
}
