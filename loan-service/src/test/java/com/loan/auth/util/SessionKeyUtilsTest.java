package com.loan.auth.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionKeyUtilsTest {

    @Test
    void sameNumericIdIsIsolatedByUserType() {
        assertNotEquals(SessionKeyUtils.key("STAFF", 1L), SessionKeyUtils.key("CHANNEL", 1L));
        assertNotEquals(SessionKeyUtils.key("CHANNEL", 1L), SessionKeyUtils.key("CUSTOMER", 1L));
        assertEquals("loan:session:STAFF:1", SessionKeyUtils.key(" staff ", 1L));
    }

    @Test
    void rejectsIncompleteIdentity() {
        assertThrows(IllegalArgumentException.class, () -> SessionKeyUtils.key(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> SessionKeyUtils.key("STAFF", null));
    }
}
