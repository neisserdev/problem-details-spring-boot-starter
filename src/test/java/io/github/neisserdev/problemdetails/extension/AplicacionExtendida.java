package io.github.neisserdev.problemdetails.extension;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// Aplicación que sustituye el manejador por una subclase propia
@SpringBootApplication
public class AplicacionExtendida {

    @Bean
    SecurityFilterChain cadenaExtendida(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(peticiones -> peticiones.anyRequest().permitAll())
                .build();
    }
}
