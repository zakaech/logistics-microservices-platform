package com.logistics.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationConfig {

    /**
     * Time is a dependency like any other.
     *
     * <p>Injecting a {@link Clock} instead of calling {@code Instant.now()} makes token expiry and
     * refresh-token rotation testable by moving the clock rather than by sleeping.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
