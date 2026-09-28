package io.github.neisserdev.problemdetails.integracion;

import jakarta.validation.constraints.Min;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class ServicioDePrueba {

    public String buscar(@Min(1) int n) {
        return "resultado " + n;
    }
}
