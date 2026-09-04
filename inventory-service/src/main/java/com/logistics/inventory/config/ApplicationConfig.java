package com.logistics.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationConfig {

    /**
     * Time is a dependency like any other. Injecting a {@link Clock} is what lets the reservation
     * expiry tests move time instead of sleeping.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
