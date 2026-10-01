package io.github.neisserdev.problemdetails.autoconfigure;

import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.github.neisserdev.problemdetails.ProblemDetailsFactory;

/**
 * Starter configuration, under the {@code problem-details} prefix.
 *
 * <pre>
 * problem-details:
 *   base-type-url: https://api.example.com/problems/
 *   language: en
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
     * Base of the "type" of each problem, the error code is appended to it.
     * Accepts relative paths or absolute URLs.
     */
    private String baseTypeUrl = ProblemDetailsFactory.DEFAULT_BASE_TYPE;

    /**
     * Language of the default titles and details. The application MessageSource
     * translations take precedence.
     */
    private Language language = Language.EN;

    private final Security security = new Security();

    private final TraceId traceId = new TraceId();

    /**
     * @return the base of the {@code type}
     */
    public String getBaseTypeUrl() {
        return baseTypeUrl;
    }

    /**
     * @param baseTypeUrl the base of the {@code type}
     */
    public void setBaseTypeUrl(String baseTypeUrl) {
        this.baseTypeUrl = baseTypeUrl;
    }

    /**
     * @return the language of the default texts
     */
    public Language getLanguage() {
        return language;
    }

    /**
     * @param language the language of the default texts
     */
    public void setLanguage(Language language) {
        this.language = language;
    }

    /**
     * @return the Spring Security integration settings
     */
    public Security getSecurity() {
        return security;
    }

    /**
     * @return the traceId settings
     */
    public TraceId getTraceId() {
        return traceId;
    }

    /** Languages of the bundled texts. */
    public enum Language {

        /** English. */
        EN(Locale.ENGLISH),

        /** Spanish. */
        ES(Locale.forLanguageTag("es"));

        private final Locale locale;

        Language(Locale locale) {
            this.locale = locale;
        }

        /**
         * @return the locale of the language
         */
        public Locale getLocale() {
            return locale;
        }
    }

    /** Trace identifier in the responses. */
    public static class TraceId {

        /**
         * Include the traceId of the request when there is an active trace.
         */
        private boolean enabled = true;

        /**
         * @return whether the traceId is included
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled whether the traceId is included
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** Spring Security integration. */
    public static class Security {

        /**
         * Register the 401 entry point and the 403 handler in the filter chain.
         */
        private boolean enabled = true;

        /**
         * Value of WWW-Authenticate in 401 responses. Empty to omit it.
         */
        private String wwwAuthenticate = "Bearer";

        /**
         * @return whether the integration is active
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * @param enabled whether the integration is active
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * @return the value of {@code WWW-Authenticate}
         */
        public String getWwwAuthenticate() {
            return wwwAuthenticate;
        }

        /**
         * @param wwwAuthenticate the value of {@code WWW-Authenticate}
         */
        public void setWwwAuthenticate(String wwwAuthenticate) {
            this.wwwAuthenticate = wwwAuthenticate;
        }
    }
}
