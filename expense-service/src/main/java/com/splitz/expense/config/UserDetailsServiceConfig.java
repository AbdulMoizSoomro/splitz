package com.splitz.expense.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

@Configuration
public class UserDetailsServiceConfig {

    @Bean
    public UserDetailsService userDetailsService() {
        return username
                -> User.withUsername(username)
                        .password("") // Not used for JWT validation
                        .authorities(List.of()) // Roles can be extracted from JWT claims in the filter
                        .build();
    }
}
