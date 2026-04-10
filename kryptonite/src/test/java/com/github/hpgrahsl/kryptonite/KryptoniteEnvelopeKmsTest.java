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
import com.github.hpgrahsl.kryptonite.keys.EdekStore;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
 * (backed by a fresh Tink AES-128-GCM keyset) and an in-memory {@link EdekStore} so no
 * cloud credentials or Kafka broker are required.
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

    /**
     * In-memory EdekStore for tests — no Kafka required.
     * Key is the hex string of the fingerprint to avoid byte[] map key issues.
     */
    private static EdekStore inMemoryEdekStore() {
        return new EdekStore() {
            private final ConcurrentHashMap<String, byte[]> store = new ConcurrentHashMap<>();
            public void init(String configJson) {}
            public void put(byte[] fingerprint, byte[] wrappedDek) {
                store.put(hex(fingerprint), wrappedDek);
            }
            public Optional<byte[]> get(byte[] fingerprint) {
                return Optional.ofNullable(store.get(hex(fingerprint)));
            }
            public void close() {}
            private String hex(byte[] b) {
                var sb = new StringBuilder(b.length * 2);
                for (byte v : b) sb.append(String.format("%02x", v));
                return sb.toString();
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
        var edekStore = inMemoryEdekStore();
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), dekCache, sessionCache, KEK_REGISTRY, edekStore, 16)) {
            byte[] ciphertext = kryptonite.cipherFieldRaw(plaintext, metadata);
            byte[] decrypted = kryptonite.decipherFieldRaw(ciphertext, metadata);
            assertArrayEquals(plaintext, decrypted);
        }
    }

    @Test
    @DisplayName("envelope KMS: wire format starts with 16-byte fingerprint")
    void testWireFormatFingerprintPrefix() {
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        var edekStore = inMemoryEdekStore();
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY, edekStore, 16)) {
            byte[] ciphertext = kryptonite.cipherFieldRaw("test".getBytes(StandardCharsets.UTF_8), metadata);
            byte[] fingerprint = new TinkAesGcmEnvelopeKms().extractWrappedDek(ciphertext);
            assertEquals(TinkAesGcmEnvelopeKms.FINGERPRINT_SIZE_BYTES, fingerprint.length,
                "extracted fingerprint must be exactly 16 bytes");
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
        byte[] fingerprint = EdekStore.fingerprint(session.wrappedDek());
        byte[] ciphertext = algorithm.cipherWithDek(plaintext, session.dekAead(), fingerprint, encryptAad);

        // Fingerprint extracted correctly — but unwrap with wrong wrapAad must fail
        byte[] extractedFingerprint = algorithm.extractWrappedDek(ciphertext);
        byte[] wrongWrapAad = "wrong".getBytes(StandardCharsets.UTF_8);
        assertThrows(GeneralSecurityException.class,
            () -> algorithm.unwrapDek(session.wrappedDek(), FAKE_KEK_A, wrongWrapAad));
        // Fingerprint is intact — it's the unwrap that fails, not the extraction
        assertArrayEquals(fingerprint, extractedFingerprint);
    }

    @Test
    @DisplayName("envelope KMS: wrong encryptAad on decrypt throws")
    void testEnvelopeKmsWrongEncryptAadFails() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKms();
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
        byte[] encryptAad = "aad".getBytes(StandardCharsets.UTF_8);
        byte[] wrapAad = KEK_ID_A.getBytes(StandardCharsets.UTF_8);

        var session = algorithm.createSession(FAKE_KEK_A, wrapAad);
        byte[] fingerprint = EdekStore.fingerprint(session.wrappedDek());
        byte[] ciphertext = algorithm.cipherWithDek(plaintext, session.dekAead(), fingerprint, encryptAad);
        var dekAead = algorithm.unwrapDek(session.wrappedDek(), FAKE_KEK_A, wrapAad);

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
        var edekStore = inMemoryEdekStore();
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY, edekStore, 16)) {
            byte[] ciphertext = kryptonite.cipherFieldRaw("test".getBytes(StandardCharsets.UTF_8), metadataA);
            // decrypt with KEK_ID_B — wrong KEK, must fail
            assertThrows(KryptoniteException.class, () -> kryptonite.decipherFieldRaw(ciphertext, metadataB));
        }
    }

    // -------------------------------------------------------------------------
    // Session cache — DEK reuse and rotation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("envelope KMS: session cache reuses fingerprint within maxEncryptions limit")
    void testSessionCacheReusesFingerprint() {
        var algorithm = new TinkAesGcmEnvelopeKms();
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        var edekStore = inMemoryEdekStore();
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY, edekStore, 16)) {
            byte[] ct1 = kryptonite.cipherFieldRaw("a".getBytes(StandardCharsets.UTF_8), metadata);
            byte[] ct2 = kryptonite.cipherFieldRaw("b".getBytes(StandardCharsets.UTF_8), metadata);
            assertArrayEquals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct2),
                "both encryptions within same session must share the same fingerprint");
        }
    }

    @Test
    @DisplayName("envelope KMS: session cache rotates fingerprint after TTL expiry (synthetic clock)")
    void testSessionCacheRotatesAfterTtlExpiry() {
        var algorithm = new TinkAesGcmEnvelopeKms();
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var clock = new MutableClock();
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L, clock);
        var edekStore = inMemoryEdekStore();
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY, edekStore, 16)) {
            byte[] ct1 = kryptonite.cipherFieldRaw("a".getBytes(StandardCharsets.UTF_8), metadata);
            clock.advance(Duration.ofMinutes(721));
            byte[] ct2 = kryptonite.cipherFieldRaw("b".getBytes(StandardCharsets.UTF_8), metadata);
            assertFalse(Arrays.equals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct2)),
                "after TTL expiry a new fingerprint must be produced");
        }
    }

    @Test
    @DisplayName("envelope KMS: session cache rotates fingerprint after maxEncryptions exhausted")
    void testSessionCacheRotatesAfterMaxEncryptions() {
        var algorithm = new TinkAesGcmEnvelopeKms();
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(2L, 720L);
        var edekStore = inMemoryEdekStore();
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY, edekStore, 16)) {
            byte[] ct1 = kryptonite.cipherFieldRaw("a".getBytes(StandardCharsets.UTF_8), metadata);
            byte[] ct2 = kryptonite.cipherFieldRaw("b".getBytes(StandardCharsets.UTF_8), metadata);
            byte[] ct3 = kryptonite.cipherFieldRaw("c".getBytes(StandardCharsets.UTF_8), metadata);
            assertArrayEquals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct2),
                "first two calls must share the same fingerprint");
            assertFalse(Arrays.equals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct3)),
                "third call must produce a new fingerprint after session rotation");
        }
    }

    @Test
    @DisplayName("envelope KMS: kekA and kekB sessions are independent")
    void testSessionCacheIndependentPerKekId() {
        var algorithm = new TinkAesGcmEnvelopeKms();
        var metadataA = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var metadataB = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_B);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        var edekStore = inMemoryEdekStore();
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY, edekStore, 16)) {
            byte[] ctA1 = kryptonite.cipherFieldRaw("a".getBytes(StandardCharsets.UTF_8), metadataA);
            byte[] ctB1 = kryptonite.cipherFieldRaw("b".getBytes(StandardCharsets.UTF_8), metadataB);
            byte[] ctA2 = kryptonite.cipherFieldRaw("c".getBytes(StandardCharsets.UTF_8), metadataA);
            byte[] ctB2 = kryptonite.cipherFieldRaw("d".getBytes(StandardCharsets.UTF_8), metadataB);
            assertArrayEquals(algorithm.extractWrappedDek(ctA1), algorithm.extractWrappedDek(ctA2),
                "kek-a session must be reused across calls");
            assertArrayEquals(algorithm.extractWrappedDek(ctB1), algorithm.extractWrappedDek(ctB2),
                "kek-b session must be reused across calls");
            assertFalse(Arrays.equals(algorithm.extractWrappedDek(ctA1), algorithm.extractWrappedDek(ctB1)),
                "kek-a and kek-b sessions must produce distinct fingerprints");
        }
    }

    // -------------------------------------------------------------------------
    // WrappedDekCache — decrypt side (fingerprint-keyed, L1 Caffeine)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("envelope KMS: KMS unwrap called once on cache miss, not called on cache hit")
    void testDecryptCacheLoaderCalledOnlyOnMiss() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKms();
        byte[] wrapAad = KEK_ID_A.getBytes(StandardCharsets.UTF_8);
        byte[] encryptAad = "meta".getBytes(StandardCharsets.UTF_8);
        var unwrapCount = new AtomicInteger(0);

        // Wrap a DEK and record the fingerprint
        var session = algorithm.createSession(FAKE_KEK_A, wrapAad);
        byte[] fingerprint = EdekStore.fingerprint(session.wrappedDek());
        byte[] ciphertext = algorithm.cipherWithDek(
            "hello".getBytes(StandardCharsets.UTF_8), session.dekAead(), fingerprint, encryptAad);

        // Populate EdekStore so the cache loader can find the wrappedDek
        var edekStore = inMemoryEdekStore();
        edekStore.put(fingerprint, session.wrappedDek());

        var dekCache = new WrappedDekCache(1024);
        // Simulate the cache loader: EdekStore lookup + KMS unwrap
        byte[] extractedFingerprint = algorithm.extractWrappedDek(ciphertext);
        for (int i = 0; i < 2; i++) {
            dekCache.get(extractedFingerprint, fp -> {
                unwrapCount.incrementAndGet();
                byte[] wrappedDek = edekStore.get(fp)
                    .orElseThrow(() -> new KryptoniteException("EDEK not found for fingerprint — the wrapped DEK may not yet have been replicated to this instance's EdekStore, or the EDEK topic may have been corrupted"));
                try {
                    return algorithm.unwrapDek(wrappedDek, FAKE_KEK_A, wrapAad);
                } catch (Exception e) {
                    throw new KryptoniteException(e);
                }
            });
        }
        assertEquals(1, unwrapCount.get(), "KMS unwrap must be called exactly once — second call must hit L1 cache");
    }

    @Test
    @DisplayName("envelope KMS: ciphertext from rotated session still decrypts correctly")
    void testRotatedSessionDecryptsCorrectly() {
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(1L, 720L); // rotate after every use
        var dekCache = new WrappedDekCache(1024);
        var edekStore = inMemoryEdekStore();
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), dekCache, sessionCache, KEK_REGISTRY, edekStore, 16)) {
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
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, null, inMemoryEdekStore(), 16)) {
            assertThrows(KryptoniteException.class,
                () -> kryptonite.cipherFieldRaw("x".getBytes(StandardCharsets.UTF_8), metadata));
        }
    }

    @Test
    @DisplayName("envelope KMS: null registry throws KryptoniteException on decipher")
    void testNullRegistryThrowsOnDecipher() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKms();
        byte[] wrapAad = KEK_ID_A.getBytes(StandardCharsets.UTF_8);
        var session = algorithm.createSession(FAKE_KEK_A, wrapAad);
        byte[] fingerprint = EdekStore.fingerprint(session.wrappedDek());
        byte[] ciphertext = algorithm.cipherWithDek(
            "test".getBytes(StandardCharsets.UTF_8), session.dekAead(), fingerprint, "meta".getBytes());

        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, null, inMemoryEdekStore(), 16)) {
            assertThrows(KryptoniteException.class,
                () -> kryptonite.decipherFieldRaw(ciphertext, metadata));
        }
    }

    @Test
    @DisplayName("envelope KMS: null EdekStore throws KryptoniteException on cipher")
    void testNullEdekStoreThrowsOnCipher() {
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY, null, 16)) {
            assertThrows(KryptoniteException.class,
                () -> kryptonite.cipherFieldRaw("x".getBytes(StandardCharsets.UTF_8), metadata));
        }
    }

    @Test
    @DisplayName("envelope KMS: null EdekStore throws KryptoniteException on decipher")
    void testNullEdekStoreThrowsOnDecipher() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKms();
        byte[] wrapAad = KEK_ID_A.getBytes(StandardCharsets.UTF_8);
        var session = algorithm.createSession(FAKE_KEK_A, wrapAad);
        byte[] fingerprint = EdekStore.fingerprint(session.wrappedDek());
        byte[] ciphertext = algorithm.cipherWithDek(
            "test".getBytes(StandardCharsets.UTF_8), session.dekAead(), fingerprint, "meta".getBytes());

        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY, null, 16)) {
            assertThrows(KryptoniteException.class,
                () -> kryptonite.decipherFieldRaw(ciphertext, metadata));
        }
    }

    @Test
    @DisplayName("envelope KMS: fingerprint not in EdekStore throws KryptoniteException on decipher")
    void testMissingEdekThrowsOnDecipher() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKms();
        byte[] wrapAad = KEK_ID_A.getBytes(StandardCharsets.UTF_8);
        var session = algorithm.createSession(FAKE_KEK_A, wrapAad);
        byte[] fingerprint = EdekStore.fingerprint(session.wrappedDek());
        byte[] ciphertext = algorithm.cipherWithDek(
            "test".getBytes(StandardCharsets.UTF_8), session.dekAead(), fingerprint, "meta".getBytes());

        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, KEK_ID_A);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        // EdekStore is present but empty — fingerprint lookup will miss
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY, inMemoryEdekStore(), 16)) {
            assertThrows(KryptoniteException.class,
                () -> kryptonite.decipherFieldRaw(ciphertext, metadata));
        }
    }

    @Test
    @DisplayName("envelope KMS: unknown KEK identifier throws KryptoniteException")
    void testUnknownKekIdThrows() {
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID_KMS, "unknown-kek");
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY, inMemoryEdekStore(), 16)) {
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
        var edekStore = inMemoryEdekStore();
        try (var kryptonite = new Kryptonite(keyVault, dekCache, sessionCache, KEK_REGISTRY, edekStore, 16)) {
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
