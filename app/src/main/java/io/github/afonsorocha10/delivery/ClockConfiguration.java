package io.github.afonsorocha10.delivery;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of the current time. Code asks for a {@link Clock} instead of calling Instant.now(),
 * so tests can use a fixed clock and the demo data can be dated in the past.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
