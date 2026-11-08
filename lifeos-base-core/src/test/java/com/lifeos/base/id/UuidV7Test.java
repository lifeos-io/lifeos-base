package com.lifeos.base.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void hasVersionSevenAndRfcVariant() {
        UUID id = UuidV7.next();
        assertEquals(7, id.version());
        assertEquals(2, id.variant());
    }

    @Test
    void ordersByTime() {
        UUID earlier = UuidV7.at(1_000L);
        UUID later = UuidV7.at(2_000L);
        assertTrue(earlier.compareTo(later) < 0);
    }
}
