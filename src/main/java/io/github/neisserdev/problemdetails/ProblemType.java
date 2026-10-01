package io.github.neisserdev.problemdetails;

import org.springframework.http.HttpStatusCode;

/**
 * RFC 9457 problem type. Defines the code, the title and the HTTP status.
 *
 * <p>{@link ErrorCode} is the built-in catalog. Custom catalogs are declared
 * as enums that implement this interface and are thrown through a subclass
 * of {@link BusinessException}.
 *
 * <p>The code is part of the {@code type}, so it must be valid inside a URI.
 * Recommended convention: {@code UPPER_CASE_WITH_UNDERSCORES}.
 *
 * @see ErrorCode
 * @see BusinessException
 */
public interface ProblemType {

    /**
     * Stable error code. Exposed in {@code code} and at the end of the {@code type}.
     *
     * @return the code, without spaces or reserved URI characters
     */
    String getCode();

    /**
     * Summary of the problem type. The specifics of each occurrence go in {@code detail}.
     *
     * @return the title
     */
    String getTitle();

    /**
     * HTTP status of the response.
     *
     * @return the status
     */
    HttpStatusCode getHttpStatus();
}
