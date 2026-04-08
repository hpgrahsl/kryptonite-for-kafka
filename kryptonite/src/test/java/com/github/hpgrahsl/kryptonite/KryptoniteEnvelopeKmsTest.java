package com.github.hpgrahsl.kryptonite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.config.ConfigReader;
import com.github.hpgrahsl.kryptonite.crypto.EncryptDekSessionCache;
import com.github.hpgrahsl.kryptonite.crypto.WrappedDekCache;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmEnvelopeKeyset;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmEnvelopeKms;
import com.github.hpgrahsl.kryptonite.keys.EnvelopeKekRegistry;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.github.hpgrahsl.kryptonite.kms.EnvelopeKekEncryption;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for envelope KMS encryption ({@code TINK/AES_GCM_ENVELOPE_KMS}) in
 * {@link Kryptonite}. Uses a local in-memory {@link EnvelopeKekEncryption} implementation
 * (backed by a fresh Tink AES-128-GCM keyset) so no cloud credentials are required.
 */
public class KryptoniteEnvelopeKmsTest {

    static final String KEK_ID_A = "kek-a";
    static final String KEK_ID_B = "kek-b";

    static final String ALGORITHM_ID_KMS = Kryptonite.CIPHERSPEC_ID_LUT.get(
            CipherSpec.fromName(TinkAesGcmEnvelopeKms.CIPHER_ALGORITHM));
    static final String ALGORITHM_ID_KEYSET = Kryptonite.CIPHERSPEC_ID_LUT.get(
            CipherSpec.fromName(TinkAesGcmEnvelopeKeyset.CIPHER_ALGORITHM));

    // Local fake KEKs — backed by Tink AES-128-GCM, no KMS network calls
    static EnvelopeKekEncryption FAKE_KEK_A;
    static EnvelopeKekEncryption FAKE_KEK_B;
    static EnvelopeKekRegistry KEK_REGISTRY;

    @BeforeAll
    static void setup() throws Exception {
        AeadConfig.register();
        FAKE_KEK_A = localKekEncryption();
        FAKE_KEK_B = localKekEncryption();
        KEK_REGISTRY = new EnvelopeKekRegistry(Map.of(KEK_ID_A, FAKE_KEK_A, KEK_ID_B, FAKE_KEK_B));
    }

    /** Creates a local in-memory EnvelopeKekEncryption using a fresh Tink AES-128-GCM key. */
    private static EnvelopeKekEncryption localKekEncryption() throws GeneralSecurityException {
        Aead aead = KeysetHandle.generateNew(PredefinedAeadParameters.AES128_GCM)
            .getPrimitive(RegistryConfiguration.get(), Aead.class);
        return new EnvelopeKekEncryption() {
            public byte[] wrapDek(byte[] rawDek, byte[] wrapAad) throws Exception {
                return aead.encrypt(rawDek, wrapAad);
            }
            public byte[] unwrapDek(byte[] wrappedDek, byte[] wrapAad) throws Exception {
                return aead.decrypt(wrappedDek, wrapAad);
            }
        };
    }

    // -------------------------------------------------------------------------
    // Round-trip correctness
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("provideEnvelopeKmsParams")
    @DisplayName("envelope KMS: decrypt(encrypt(plaintext)) == plaintext")
    void testEnvelopeKmsRoundTrip(byte[] plaintext, PayloadMetaData metadata) {
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        var dekCache = new WrappedDekCache(1024);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), dekCache, sessionCache, KEK_REGISTRY)) {
            byte[] ciphertext = kryptonite.cipherFieldRaw(plaintext, metadata);
            byte[] decrypted = kryptonite.decipherFieldRaw(ciphertext, metadata);
            assertArrayEquals(plaintext, decrypted);
        }
    }

    // -------------------------------------------------------------------------
    // AAD and key binding
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("envelope KMS: wrong wrapAad on decrypt throws")
    void testEnvelopeKmsWrongWrapAadFails() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKms();
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
        byte[] encryptAad = "aad".getBytes(StandardCharsets.UTF_8);
        byte[] wrapAad = KEK_ID_A.getBytes(StandardCharsets.UTF_8);

        var session = algorithm.createSession(FAKE_KEK_A, wrapAad);
        byte[] ciphertext = algorithm.cipherWithDek(plaintext, session.dekAead(), session.wrappedDek(), encryptAad);

        byte[] wrongWrapAad = "wrong".getBytes(StandardCharsets.UTF_8);
        byte[] wrappedDek = algorithm.extractWrappedDek(ciphertext);
        assertThrows(GeneralSecurityException.class,
            () -> algorithm.unwrapDek(wrappedDek, FAKE_KEK_A, wrongWrapAad));
    }

    @Test
    @DisplayName("envelope KMS: wrong encryptAad on decrypt throws")
    void testEnvelopeKmsWrongEncryptAadFails() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKms();
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
        byte[] encryptAad = "aad".getBytes(StandardCharsets.UTF_8);
        byte[] wrapAad = KEK_ID_A.getBytes(StandardCharsets.UTF_8);

        var session = algorithm.createSession(FAKE_KEK_A, wrapAad);
        byte[] ciphertext = algorithm.cipherWithDek(plaintext, session.dekAead(), session.wrappedDek(), encryptAad);
        var dekAead = algorithm.unwrapDek(algorithm.extractWrappedDek(ciphertext), FAKE_KEK_A, wrapAad);

        byte[] wrongEncryptAad = "wrong".getBytes(StandardCharsets.UTF_8);
        assertThrows(GeneralSecurityException.class,
            () -> algorithm.decipherWithDek(ciphertext, dekAead, wrongEncryptAad));
    }

    @Test
    @DisplayName("envelope KMS: decrypting with wrong KEK throws")
    void testEnvelopeKmsCrossKekDecryptFails() {
        var metadataA = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var metadataB = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_B);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY)) {
            byte[] ciphertext = kryptonite.cipherFieldRaw("test".getBytes(StandardCharsets.UTF_8), metadataA);
            // decrypt with KEK_ID_B — wrong KEK, must fail
            assertThrows(KryptoniteException.class, () -> kryptonite.decipherFieldRaw(ciphertext, metadataB));
        }
    }

    // -------------------------------------------------------------------------
    // Session cache — DEK reuse and rotation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("envelope KMS: session cache reuses wrappedDek within maxEncryptions limit")
    void testSessionCacheReusesWrappedDek() {
        var algorithm = new TinkAesGcmEnvelopeKms();
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY)) {
            byte[] ct1 = kryptonite.cipherFieldRaw("a".getBytes(StandardCharsets.UTF_8), metadata);
            byte[] ct2 = kryptonite.cipherFieldRaw("b".getBytes(StandardCharsets.UTF_8), metadata);
            assertArrayEquals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct2),
                "both encryptions within same session must share the same wrappedDek");
        }
    }

    @Test
    @DisplayName("envelope KMS: session cache rotates wrappedDek after TTL expiry (synthetic clock)")
    void testSessionCacheRotatesAfterTtlExpiry() {
        var algorithm = new TinkAesGcmEnvelopeKms();
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var clock = new MutableClock();
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L, clock);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY)) {
            byte[] ct1 = kryptonite.cipherFieldRaw("a".getBytes(StandardCharsets.UTF_8), metadata);
            clock.advance(Duration.ofMinutes(721));
            byte[] ct2 = kryptonite.cipherFieldRaw("b".getBytes(StandardCharsets.UTF_8), metadata);
            assertFalse(Arrays.equals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct2)),
                "after TTL expiry a new wrappedDek must be produced");
        }
    }

    @Test
    @DisplayName("envelope KMS: session cache rotates wrappedDek after maxEncryptions exhausted")
    void testSessionCacheRotatesAfterMaxEncryptions() {
        var algorithm = new TinkAesGcmEnvelopeKms();
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(2L, 720L);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY)) {
            byte[] ct1 = kryptonite.cipherFieldRaw("a".getBytes(StandardCharsets.UTF_8), metadata);
            byte[] ct2 = kryptonite.cipherFieldRaw("b".getBytes(StandardCharsets.UTF_8), metadata);
            byte[] ct3 = kryptonite.cipherFieldRaw("c".getBytes(StandardCharsets.UTF_8), metadata);
            assertArrayEquals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct2),
                "first two calls must share the same wrappedDek");
            assertFalse(Arrays.equals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct3)),
                "third call must produce a new wrappedDek after session rotation");
        }
    }

    @Test
    @DisplayName("envelope KMS: kekA and kekB sessions are independent")
    void testSessionCacheIndependentPerKekId() {
        var algorithm = new TinkAesGcmEnvelopeKms();
        var metadataA = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var metadataB = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_B);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY)) {
            byte[] ctA1 = kryptonite.cipherFieldRaw("a".getBytes(StandardCharsets.UTF_8), metadataA);
            byte[] ctB1 = kryptonite.cipherFieldRaw("b".getBytes(StandardCharsets.UTF_8), metadataB);
            byte[] ctA2 = kryptonite.cipherFieldRaw("c".getBytes(StandardCharsets.UTF_8), metadataA);
            byte[] ctB2 = kryptonite.cipherFieldRaw("d".getBytes(StandardCharsets.UTF_8), metadataB);
            assertArrayEquals(algorithm.extractWrappedDek(ctA1), algorithm.extractWrappedDek(ctA2),
                "kek-a session must be reused across calls");
            assertArrayEquals(algorithm.extractWrappedDek(ctB1), algorithm.extractWrappedDek(ctB2),
                "kek-b session must be reused across calls");
            assertFalse(Arrays.equals(algorithm.extractWrappedDek(ctA1), algorithm.extractWrappedDek(ctB1)),
                "kek-a and kek-b sessions must produce distinct wrappedDeks");
        }
    }

    // -------------------------------------------------------------------------
    // WrappedDekCache — decrypt side
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("envelope KMS: decrypt cache loader called once on miss, not called on hit")
    void testDecryptCacheLoaderCalledOnlyOnMiss() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKms();
        byte[] wrapAad = KEK_ID_A.getBytes(StandardCharsets.UTF_8);
        byte[] encryptAad = "meta".getBytes(StandardCharsets.UTF_8);

        var session = algorithm.createSession(FAKE_KEK_A, wrapAad);
        byte[] ciphertext = algorithm.cipherWithDek(
            "hello".getBytes(StandardCharsets.UTF_8), session.dekAead(), session.wrappedDek(), encryptAad);
        byte[] wrappedDek = algorithm.extractWrappedDek(ciphertext);

        var loadCount = new AtomicInteger(0);
        var dekCache = new WrappedDekCache(1024);
        dekCache.get(wrappedDek, wdk -> {
            loadCount.incrementAndGet();
            try { return algorithm.unwrapDek(wdk, FAKE_KEK_A, wrapAad); }
            catch (Exception e) { throw new KryptoniteException(e); }
        });
        dekCache.get(wrappedDek, wdk -> {
            loadCount.incrementAndGet();
            try { return algorithm.unwrapDek(wdk, FAKE_KEK_A, wrapAad); }
            catch (Exception e) { throw new KryptoniteException(e); }
        });
        assertEquals(1, loadCount.get(), "loader must be called exactly once — second call must hit cache");
    }

    @Test
    @DisplayName("envelope KMS: ciphertext from rotated session still decrypts correctly")
    void testRotatedSessionDecryptsCorrectly() {
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(1L, 720L); // rotate after every use
        var dekCache = new WrappedDekCache(1024);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), dekCache, sessionCache, KEK_REGISTRY)) {
            byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
            byte[] ct1 = kryptonite.cipherFieldRaw(plaintext, metadata);
            byte[] ct2 = kryptonite.cipherFieldRaw(plaintext, metadata); // triggers rotation
            assertArrayEquals(plaintext, kryptonite.decipherFieldRaw(ct1, metadata));
            assertArrayEquals(plaintext, kryptonite.decipherFieldRaw(ct2, metadata));
        }
    }

    // -------------------------------------------------------------------------
    // Error paths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("envelope KMS: null registry throws KryptoniteException on cipher")
    void testNullRegistryThrowsOnCipher() {
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, null)) {
            assertThrows(KryptoniteException.class,
                () -> kryptonite.cipherFieldRaw("x".getBytes(StandardCharsets.UTF_8), metadata));
        }
    }

    @Test
    @DisplayName("envelope KMS: null registry throws KryptoniteException on decipher")
    void testNullRegistryThrowsOnDecipher() throws Exception {
        // encrypt normally first, then try to decrypt without registry
        var algorithm = new TinkAesGcmEnvelopeKms();
        byte[] wrapAad = KEK_ID_A.getBytes(StandardCharsets.UTF_8);
        var session = algorithm.createSession(FAKE_KEK_A, wrapAad);
        byte[] ciphertext = algorithm.cipherWithDek(
            "test".getBytes(StandardCharsets.UTF_8), session.dekAead(), session.wrappedDek(), "meta".getBytes());

        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, null)) {
            assertThrows(KryptoniteException.class,
                () -> kryptonite.decipherFieldRaw(ciphertext, metadata));
        }
    }

    @Test
    @DisplayName("envelope KMS: unknown KEK identifier throws KryptoniteException")
    void testUnknownKekIdThrows() {
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, "unknown-kek");
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY)) {
            assertThrows(KryptoniteException.class,
                () -> kryptonite.cipherFieldRaw("x".getBytes(StandardCharsets.UTF_8), metadata));
        }
    }

    // -------------------------------------------------------------------------
    // Mix-and-match: envelope keyset (ENVELOPE_KEYSET) and envelope KMS (ENVELOPE_KMS) in same instance
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("mix-and-match: envelope keyset and envelope KMS fields in the same Kryptonite instance")
    void testMixedEnvelopeKeysetAndEnvelopeKmsRoundTrip() {
        var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG));
        var metadataA = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KEYSET, "keyA");
        var metadataB = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        var dekCache = new WrappedDekCache(1024);
        try (var kryptonite = new Kryptonite(keyVault, dekCache, sessionCache, KEK_REGISTRY)) {
            byte[] plaintextA = "envelope-keyset field".getBytes(StandardCharsets.UTF_8);
            byte[] plaintextB = "envelope-kms field".getBytes(StandardCharsets.UTF_8);

            byte[] ctA = kryptonite.cipherFieldRaw(plaintextA, metadataA);
            byte[] ctB = kryptonite.cipherFieldRaw(plaintextB, metadataB);

            assertArrayEquals(plaintextA, kryptonite.decipherFieldRaw(ctA, metadataA));
            assertArrayEquals(plaintextB, kryptonite.decipherFieldRaw(ctB, metadataB));
        }
    }

    // -------------------------------------------------------------------------
    // Parameterized helpers
    // -------------------------------------------------------------------------

    static List<Arguments> provideEnvelopeKmsParams() {
        return List.of(
            Arguments.of(
                "alice".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A)
            ),
            Arguments.of(
                "bob".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_B)
            ),
            Arguments.of(
                "kryptonite envelope KMS encryption".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A)
            )
        );
    }

}
