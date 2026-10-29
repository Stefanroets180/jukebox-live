package com.jukebox.live.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OriginAllowlistTest {

    @Test
    void parsesAndTrimsExactOrigins() {
        assertEquals(
                List.of("http://localhost:3000", "http://192.168.1.23:3000"),
                OriginAllowlist.parse(" http://localhost:3000, http://192.168.1.23:3000 "));
    }

    @Test
    void rejectsWildcardOrigins() {
        assertThrows(IllegalArgumentException.class,
                () -> OriginAllowlist.parse("http://localhost:3000,*"));
    }

    @Test
    void rejectsAnEmptyAllowlist() {
        assertThrows(IllegalArgumentException.class, () -> OriginAllowlist.parse(" , "));
    }
}
