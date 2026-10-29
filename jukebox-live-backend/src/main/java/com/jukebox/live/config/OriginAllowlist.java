package com.jukebox.live.config;

import java.util.Arrays;
import java.util.List;

public final class OriginAllowlist {

    private OriginAllowlist() {
    }

    public static List<String> parse(String configuredOrigins) {
        List<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();

        if (origins.isEmpty() || origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("jukebox.allowed-origins must contain exact origins; wildcards are not allowed.");
        }

        return origins;
    }
}
