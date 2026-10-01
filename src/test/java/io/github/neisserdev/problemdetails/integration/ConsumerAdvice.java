package io.github.neisserdev.problemdetails.integration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Must take precedence over the starter handler
@RestControllerAdvice
class ConsumerAdvice {

    @ExceptionHandler(TestController.ConsumerException.class)
    ResponseEntity<String> handle() {
        return ResponseEntity.status(418).body("handled by the application");
    }
}
