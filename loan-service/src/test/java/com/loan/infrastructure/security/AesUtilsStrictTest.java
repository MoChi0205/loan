package com.loan.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AesUtilsStrictTest {

    @BeforeEach
    void configure() {
        new AesUtils().setConfiguredKey("test-aes-key");
    }

    @Test
    void plaintextPhoneIsNotAcceptedAsCiphertext() {
        assertNull(AesUtils.decrypt("13800000001"));
    }

    @Test
    void encryptedPhoneRoundTrips() {
        String cipher = AesUtils.encrypt("13800000001");
        assertNotEquals("13800000001", cipher);
        assertEquals("13800000001", AesUtils.decrypt(cipher));
    }
}
