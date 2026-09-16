package com.sunmoon.appserver.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    // Same disk-path leak, different Spring Boot subsystem — see
    // sun-moon-java-platform-order's MetricsConfig for the full explanation.
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> redactDiskSpacePathTag() {
        return registry -> registry.config()
                .meterFilter(MeterFilter.replaceTagValues("path", PathRedactor::redact));
    }
}
