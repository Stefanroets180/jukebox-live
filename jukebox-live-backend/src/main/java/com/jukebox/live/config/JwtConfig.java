package com.jukebox.live.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    private final Environment environment;

    @Bean
    public String jwtSecret() {
        String secret = environment.getProperty("jukebox.jwt.secret");
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException(
                "JWT secret must be set via JWT_SECRET environment variable. " +
                "For local development, set: export JWT_SECRET='your-256-bit-secret-key-here'"
            );
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                "JWT secret must be at least 32 characters for security. Current length: " + secret.length()
            );
        }
        return secret;
    }
}
