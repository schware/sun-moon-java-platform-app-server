package com.sunmoon.appserver.config;

/**
 * Replaces the container's working-directory prefix ({@code /app}, from
 * the Dockerfile's WORKDIR) with the service name before paths reach
 * externally-visible endpoints (health details, metrics tags). Same fix
 * as sun-moon-java-platform-order. Changing SERVICE_NAME here is the
 * step that is easy to forget when copying a service (docs/CONVENTIONS §5).
 */
final class PathRedactor {

    private static final String MARKER = "/app";
    private static final String SERVICE_NAME = "app-server";

    private PathRedactor() {
    }

    static String redact(String absolutePath) {
        int index = absolutePath.indexOf(MARKER);
        return index >= 0 ? SERVICE_NAME + absolutePath.substring(index + MARKER.length()) : absolutePath;
    }
}
