package io.github.neisserdev.problemdetails.extension;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// Application that replaces the handler with its own subclass
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ExtendedApplication {

    @Bean
    SecurityFilterChain extendedSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }
}
