package io.github.neisserdev.problemdetails.integration;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import io.github.neisserdev.problemdetails.ProblemType;

// Custom catalog of a consumer project
enum StoreErrors implements ProblemType {

    INSUFFICIENT_STOCK("Insufficient stock", HttpStatus.CONFLICT),
    PAYMENT_GATEWAY_UNAVAILABLE("Payment gateway unavailable", HttpStatus.SERVICE_UNAVAILABLE);

    private final String title;
    private final HttpStatus status;

    StoreErrors(String title, HttpStatus status) {
        this.title = title;
        this.status = status;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public HttpStatusCode getHttpStatus() {
        return status;
    }
}
