package com.github.hpgrahsl.kryptonite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.config.ConfigReader;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmEnvelopeKeyset;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.google.crypto.tink.aead.AeadConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class KryptoniteEnvelopeKeysetTest {

    @BeforeAll
    static void registerTink() throws Exception {
        AeadConfig.register();
    }

    static final String ALGORITHM_ID = Kryptonite.CIPHERSPEC_ID_LUT.get(
            CipherSpec.fromName(TinkAesGcmEnvelopeKeyset.CIPHER_ALGORITHM));

    @ParameterizedTest
    @MethodSource("provideEnvelopeKeysetParams")
    @DisplayName("envelope keyset: decrypt(encrypt(plaintext)) == plaintext")
    void testEnvelopeKeysetRoundTrip(TinkKeyVault keyVault, byte[] plaintext, PayloadMetaData metadata) {
        try (var kryptonite = new Kryptonite(keyVault)) {
            byte[] ciphertext = kryptonite.cipherFieldRaw(plaintext, metadata);
            byte[] decrypted = kryptonite.decipherFieldRaw(ciphertext, metadata);
            assertArrayEquals(plaintext, decrypted);
        }
    }

    @Test
    @DisplayName("envelope keyset: wrong wrapAad on decrypt throws")
    void testEnvelopeKeysetWrongWrapAadFails() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKeyset();
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var kekHandle = keyVault.readKeysetHandle("keyA");
            byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
            byte[] encryptAad = "aad".getBytes(StandardCharsets.UTF_8);
            byte[] wrapAad = "keyA".getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = algorithm.cipher(plaintext, kekHandle, encryptAad, wrapAad);
            byte[] wrongWrapAad = "wrong".getBytes(StandardCharsets.UTF_8);
            assertThrows(Exception.class, () -> algorithm.decipher(ciphertext, kekHandle, encryptAad, wrongWrapAad));
        }
    }

    @Test
    @DisplayName("envelope keyset: wrong encryptAad on decrypt throws")
    void testEnvelopeKeysetWrongEncryptAadFails() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKeyset();
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var kekHandle = keyVault.readKeysetHandle("keyA");
            byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
            byte[] encryptAad = "aad".getBytes(StandardCharsets.UTF_8);
            byte[] wrapAad = "keyA".getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = algorithm.cipher(plaintext, kekHandle, encryptAad, wrapAad);
            byte[] wrongEncryptAad = "wrong".getBytes(StandardCharsets.UTF_8);
            assertThrows(Exception.class, () -> algorithm.decipher(ciphertext, kekHandle, wrongEncryptAad, wrapAad));
        }
    }

    @Test
    @DisplayName("envelope keyset: decrypting with wrong key throws")
    void testEnvelopeKeysetCrossKeyDecryptFails() {
        var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG));
        var metadataA = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA");
        var metadataB = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyB");
        try (var kryptonite = new Kryptonite(keyVault)) {
            byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = kryptonite.cipherFieldRaw(plaintext, metadataA);
            assertThrows(Exception.class, () -> kryptonite.decipherFieldRaw(ciphertext, metadataB));
        }
    }

    static List<Arguments> provideEnvelopeKeysetParams() {
        var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG));
        return List.of(
            Arguments.of(
                keyVault,
                "alice".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA")
            ),
            Arguments.of(
                keyVault,
                "bob".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyB")
            ),
            Arguments.of(
                keyVault,
                "kryptonite envelope encryption".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA")
            )
        );
    }

}
