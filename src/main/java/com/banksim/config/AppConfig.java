package com.banksim.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {

    /** Injectable clock: production uses UTC system time, tests can pass a fixed or mutable one. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
