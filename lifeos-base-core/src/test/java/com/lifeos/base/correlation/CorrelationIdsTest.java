package com.lifeos.base.correlation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CorrelationIdsTest {

    @Test
    void newIdIsAValidUuidV7() {
        String id = CorrelationIds.newId();
        assertTrue(CorrelationIds.isValid(id));
        assertEquals(7, UUID.fromString(id).version());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0197a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b", "mobile:ios.1_2-3"})
    void keepsValidClientValues(String candidate) {
        assertEquals(candidate, CorrelationIds.orNew(candidate));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "has space",
            "line\nbreak",
            "carriage\rreturn",
            "quote\"",
            "<script>",
            "ünicode"
    })
    void replacesUnsafeValues(String candidate) {
        assertFalse(CorrelationIds.isValid(candidate));
        String result = CorrelationIds.orNew(candidate);
        assertNotEquals(candidate, result);
        assertTrue(CorrelationIds.isValid(result));
    }

    @Test
    void enforcesMaximumLength() {
        assertTrue(CorrelationIds.isValid("a".repeat(CorrelationIds.MAX_LENGTH)));
        assertFalse(CorrelationIds.isValid("a".repeat(CorrelationIds.MAX_LENGTH + 1)));
    }
}
