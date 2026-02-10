package com.tinto.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Spring Security configuration for Tinto.
 *
 * For the development phase, this permits all requests.
 * API key authentication will be wired in once the auth subsystem
 * (MasterAccessKey / ApiKey entities + filter) is fully implemented.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() } // REST API — CSRF not needed
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // Actuator health for container healthcheck
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    // Allow all API endpoints for now (auth filter added later)
                    .requestMatchers("/api/**", "/v1/**").permitAll()
                    // Permit everything else during development
                    .anyRequest().permitAll()
            }

        return http.build()
    }
}
