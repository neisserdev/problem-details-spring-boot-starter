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
 * </pre>
 */
@ConfigurationProperties(prefix = "problem-details")
public class ProblemDetailsProperties {

    /**
     * Base del "type" de cada problema, se le concatena el código del error.
     * Admite rutas relativas o URLs absolutas.
     */
    private String baseTypeUrl = ProblemDetailsFactory.BASE_TYPE_POR_DEFECTO;

    private final Security security = new Security();

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
