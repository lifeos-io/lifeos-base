package com.lifeos.base.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

class CommonErrorCodeTest {

    @ParameterizedTest
    @EnumSource(CommonErrorCode.class)
    void followsTheErrorCodeConventions(CommonErrorCode code) {
        assertTrue(ErrorCode.CODE_FORMAT.matcher(code.code()).matches(), code.code());
        assertTrue(code.status() >= 400 && code.status() <= 599, code.code());
        assertFalse(code.title().isBlank(), code.code());
    }

    @Test
    void codesAreUnique() {
        long distinct = Arrays.stream(CommonErrorCode.values()).map(CommonErrorCode::code).distinct().count();
        assertEquals(CommonErrorCode.values().length, distinct);
    }

    @Test
    void codeFormatRejectsNonConformingNames() {
        assertFalse(ErrorCode.CODE_FORMAT.matcher("notFound").matches());
        assertFalse(ErrorCode.CODE_FORMAT.matcher("NOT__FOUND").matches());
        assertFalse(ErrorCode.CODE_FORMAT.matcher("_NOT_FOUND").matches());
        assertFalse(ErrorCode.CODE_FORMAT.matcher("NOT_FOUND_").matches());
        assertTrue(ErrorCode.CODE_FORMAT.matcher("STRATEGY_CAPACITY_EXCEEDED").matches());
    }
}
