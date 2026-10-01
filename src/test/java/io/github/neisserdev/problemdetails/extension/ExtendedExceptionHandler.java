package io.github.neisserdev.problemdetails.extension;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.neisserdev.problemdetails.ErrorCode;
import io.github.neisserdev.problemdetails.GlobalExceptionHandler;
import io.github.neisserdev.problemdetails.ProblemDetailsFactory;
import io.github.neisserdev.problemdetails.autoconfigure.ProblemDetailsProperties;

@RestControllerAdvice
class ExtendedExceptionHandler extends GlobalExceptionHandler {

    ExtendedExceptionHandler(ProblemDetailsFactory factory, ProblemDetailsProperties properties) {
        super(factory, properties.getSecurity().isEnabled());
    }

    @ExceptionHandler(ItemRetiredException.class)
    ProblemDetail itemRetired(ItemRetiredException ex, HttpServletRequest request) {
        return getFactory().create(ErrorCode.RESOURCE_NOT_FOUND, "The item was retired", request.getRequestURI());
    }

    static class ItemRetiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
