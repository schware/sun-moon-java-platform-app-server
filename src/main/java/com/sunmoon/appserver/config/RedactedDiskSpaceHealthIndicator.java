package com.sunmoon.appserver.config;

import org.springframework.boot.actuate.autoconfigure.system.DiskSpaceHealthIndicatorProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.system.DiskSpaceHealthIndicator;
import org.springframework.stereotype.Component;

import java.io.File;

// See sun-moon-java-platform-order's class of the same name for why this
// override exists (redacts the server's home directory from
// /actuator/health's disk "path" detail).
@Component("diskSpaceHealthIndicator")
public class RedactedDiskSpaceHealthIndicator extends DiskSpaceHealthIndicator {

    private final File path;

    public RedactedDiskSpaceHealthIndicator(DiskSpaceHealthIndicatorProperties properties) {
        super(properties.getPath(), properties.getThreshold());
        this.path = properties.getPath();
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        super.doHealthCheck(builder);
        builder.withDetail("path", PathRedactor.redact(path.getAbsolutePath()));
    }
}
