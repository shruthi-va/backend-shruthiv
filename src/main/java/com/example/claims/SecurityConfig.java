package com.example.claims;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;

@Configuration
class SecurityConfig {
    static final String STAFF_USERNAME = "staff";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/csrf", "/api/claims", "/api/claims/*")
                        .hasAnyRole("CLAIMANT", "STAFF", "MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/claims").hasRole("CLAIMANT")
                        .requestMatchers(HttpMethod.POST, "/api/claims/*/assignment", "/api/claims/*/decision")
                        .hasRole("MANAGER")
                        .requestMatchers(HttpMethod.POST, "/api/claims/*/review").hasRole("STAFF")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    InMemoryUserDetailsManager users(
            @Value("${claims.auth.claimant-password}") String claimant,
            @Value("${claims.auth.staff-password}") String staff,
            @Value("${claims.auth.manager-password}") String manager) {
        Assert.hasText(claimant, "Set CLAIMS_CLAIMANT_PASSWORD");
        Assert.hasText(staff, "Set CLAIMS_STAFF_PASSWORD");
        Assert.hasText(manager, "Set CLAIMS_MANAGER_PASSWORD");
        Assert.isTrue(!claimant.equals(staff) && !claimant.equals(manager) && !staff.equals(manager),
                "Use a different password for each claims role");
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return new InMemoryUserDetailsManager(
                User.withUsername("claimant").password(encoder.encode(claimant)).roles("CLAIMANT").build(),
                User.withUsername(STAFF_USERNAME).password(encoder.encode(staff)).roles("STAFF").build(),
                User.withUsername("manager").password(encoder.encode(manager)).roles("MANAGER").build());
    }
}
