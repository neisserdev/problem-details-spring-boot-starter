package io.github.neisserdev.problemdetails.integracion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// Sin exceptionHandling(), el starter lo configura solo
@Configuration(proxyBeanMethods = false)
class SeguridadDePrueba {

    @Bean
    SecurityFilterChain cadenaDePrueba(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers("/publico/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .build();
    }
}
