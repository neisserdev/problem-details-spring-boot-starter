package io.github.neisserdev.problemdetails.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.neisserdev.problemdetails.ResourceNotFoundException;

@RestController
@RequestMapping("/public")
class TestController {

    private final TestService service;

    TestController(TestService service) {
        this.service = service;
    }

    record NewUser(@NotBlank String name, @NotBlank @Email String email) {
    }

    @GetMapping("/orders/{id}")
    String order(@PathVariable("id") long id) {
        throw new ResourceNotFoundException("Order", id);
    }

    @PostMapping("/orders")
    String createOrder() {
        throw new InsufficientStockException(3);
    }

    @PostMapping("/users")
    String createUser(@Valid @RequestBody NewUser user) {
        return user.name();
    }

    // Built-in validation, without @Validated
    @GetMapping("/page")
    String page(@RequestParam("n") @Min(1) int n) {
        return "page " + n;
    }

    // AOP validation in a @Validated service
    @GetMapping("/service")
    String findInService(@RequestParam("n") int n) {
        return service.find(n);
    }

    @GetMapping("/payment-gateway")
    String paymentGateway() {
        throw new PaymentGatewayUnavailableException();
    }

    // Subclass of DataIntegrityViolationException
    @GetMapping("/duplicate")
    String duplicate() {
        throw new DuplicateKeyException(
                "duplicate key value violates unique constraint \"users_email_key\"");
    }

    @GetMapping("/concurrency")
    String concurrency() {
        throw new OptimisticLockingFailureException("Row was updated or deleted by another transaction");
    }

    @GetMapping("/license")
    String license() {
        throw new LicenseExpiredException();
    }

    @GetMapping("/gone")
    String gone() {
        throw new ResponseStatusException(HttpStatus.GONE, "The resource was removed");
    }

    @GetMapping("/subscription")
    String subscription() {
        throw new SubscriptionExpiredException();
    }

    @PostMapping("/login")
    String login() {
        throw new BadCredentialsException("Bad credentials");
    }

    // Same as a failed @PreAuthorize
    @GetMapping("/admin-only")
    String adminOnly() {
        throw new AccessDeniedException("Access Denied");
    }

    @GetMapping("/consumer")
    String consumer() {
        throw new ConsumerException();
    }

    @GetMapping("/failure")
    String failure() {
        throw new IllegalStateException("unexpected failure");
    }

    @ResponseStatus(code = HttpStatus.PAYMENT_REQUIRED, reason = "Subscription expired")
    static class SubscriptionExpiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    // reason as a message key
    @ResponseStatus(code = HttpStatus.PAYMENT_REQUIRED, reason = "license.expired")
    static class LicenseExpiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    static class ConsumerException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
