package io.github.neisserdev.problemdetails.integration;

import jakarta.validation.constraints.Min;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class TestService {

    public String find(@Min(1) int n) {
        return "result " + n;
    }
}
