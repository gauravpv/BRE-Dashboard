package com.bredashboard.tester.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AesCbcCryptoTest {

    private static final String KEY = "7ZL83Y7KMVN54ARHCYHUKRYTGUQ6ENHJ"; // 32 chars
    private static final String IV = "SO8A89C2R60J5AG6";                 // 16 chars

    @Test
    void encryptThenDecrypt_roundTripsJson() {
        String plain = "{\"mobile\":\"9999999999\"}";

        String cipher = AesCbcCrypto.encryptUtf8(plain, KEY, IV);

        assertThat(cipher).isNotBlank().doesNotContain("mobile");
        assertThat(AesCbcCrypto.decryptUtf8(cipher, KEY, IV)).isEqualTo(plain);
    }

    /**
     * Per-API responses come back as a quoted Base64 string, mirroring the Postman
     * post-response script's {@code .replace(/^"|"$/g, '')}.
     */
    @Test
    void decrypt_stripsSurroundingQuotesAndWhitespace() {
        String plain = "{\"status\":\"SUCCESS\"}";
        String quoted = "  \"" + AesCbcCrypto.encryptUtf8(plain, KEY, IV) + "\"  ";

        assertThat(AesCbcCrypto.decryptUtf8(quoted, KEY, IV)).isEqualTo(plain);
    }

    @Test
    void encrypt_emptyJsonObjectIsSupported() {
        String cipher = AesCbcCrypto.encryptUtf8("{}", KEY, IV);

        assertThat(AesCbcCrypto.decryptUtf8(cipher, KEY, IV)).isEqualTo("{}");
    }
}
