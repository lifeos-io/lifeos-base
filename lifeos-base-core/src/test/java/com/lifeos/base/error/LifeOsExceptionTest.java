package com.lifeos.base.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LifeOsExceptionTest {

    @Test
    void carriesCodeDetailAndDetails() {
        var ex = new LifeOsException(CommonErrorCode.CONFLICT, "Goal is archived", Map.of("goalStatus", "ABANDONED"));

        assertSame(CommonErrorCode.CONFLICT, ex.errorCode());
        assertEquals("Goal is archived", ex.getMessage());
        assertEquals(Map.of("goalStatus", "ABANDONED"), ex.details());
        assertNull(ex.getCause());
    }

    @Test
    void detailsAreEmptyByDefault() {
        assertTrue(new LifeOsException(CommonErrorCode.NOT_FOUND, "Goal not found").details().isEmpty());
    }

    @Test
    void detailsAreAnImmutableCopy() {
        Map<String, Object> source = new HashMap<>(Map.of("limitMinutes", 240));
        var ex = new LifeOsException(CommonErrorCode.VALIDATION_FAILED, "Over capacity", source);

        source.put("limitMinutes", 999);

        assertEquals(240, ex.details().get("limitMinutes"));
        assertThrows(UnsupportedOperationException.class, () -> ex.details().put("x", 1));
    }

    @Test
    void keepsTheCause() {
        var cause = new IllegalStateException("db down");
        var ex = new LifeOsException(CommonErrorCode.UPSTREAM_UNAVAILABLE, "Try again later", Map.of(), cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void rejectsMissingParts() {
        assertThrows(NullPointerException.class, () -> new LifeOsException(null, "detail"));
        assertThrows(NullPointerException.class, () -> new LifeOsException(CommonErrorCode.CONFLICT, null));
        assertThrows(NullPointerException.class,
                () -> new LifeOsException(CommonErrorCode.CONFLICT, "detail", null));
        Map<String, Object> withNullValue = new HashMap<>();
        withNullValue.put("key", null);
        assertThrows(NullPointerException.class,
                () -> new LifeOsException(CommonErrorCode.CONFLICT, "detail", withNullValue));
    }
}
