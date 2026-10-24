package dev.neisser.exceptions;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración externa del starter, bajo el prefijo {@code problem-details}.
 *
 * <pre>{@code
 * problem-details:
 *   base-type-url: https://miapp.com/problems/
 *   security:
 *     enabled: true
 * }</pre>
 */
@ConfigurationProperties(prefix = "problem-details")
public class ProblemDetailsProperties {

    /**
     * Prefijo con el que se construye el {@code type} del ProblemDetail:
     * {@code baseTypeUrl + ErrorCode.getCode()}. Debe terminar en {@code /}.
     */
    private String baseTypeUrl = "https://neisser.dev/problems/";

    private final Security security = new Security();

    public String getBaseTypeUrl() { return baseTypeUrl; }
    public void setBaseTypeUrl(String baseTypeUrl) { this.baseTypeUrl = baseTypeUrl; }
    public Security getSecurity() { return security; }

    public static class Security {
        /**
         * Activa {@code SecurityAccessDeniedHandler}, {@code SecurityAuthenticationEntryPoint}
         * y {@code ProblemJsonWriter}. Desactívalo si tu proyecto no usa Spring Security
         * o prefiere manejar 401/403 de filtros por su cuenta.
         */
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
