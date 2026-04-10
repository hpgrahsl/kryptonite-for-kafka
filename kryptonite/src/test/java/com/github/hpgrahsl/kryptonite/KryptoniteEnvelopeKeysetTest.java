package com.github.hpgrahsl.kryptonite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.security.GeneralSecurityException;

import java.util.concurrent.atomic.AtomicInteger;
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.config.ConfigReader;
import com.github.hpgrahsl.kryptonite.crypto.EncryptDekSessionCache;
import java.time.Duration;
import com.github.hpgrahsl.kryptonite.crypto.WrappedDekCache;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmEnvelopeKeyset;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.google.crypto.tink.aead.AeadConfig;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class KryptoniteEnvelopeKeysetTest {

    @BeforeAll
    static void registerTinkCiphers() throws Exception {
        AeadConfig.register();
    }

    static final String ALGORITHM_ID = Kryptonite.CIPHERSPEC_ID_LUT.get(
            CipherSpec.fromName(TinkAesGcmEnvelopeKeyset.CIPHER_ALGORITHM));

    // -------------------------------------------------------------------------
    // Round-trip correctness
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // AAD and key binding
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("envelope keyset: wrong wrapAad on decrypt throws")
    void testEnvelopeKeysetWrongWrapAadFails() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKeyset();
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var kekHandle = keyVault.readKeysetHandle("keyA");
            byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
            byte[] encryptAad = "aad".getBytes(StandardCharsets.UTF_8);
            byte[] wrapAad = "keyA".getBytes(StandardCharsets.UTF_8);
            var session = algorithm.createSession(kekHandle, wrapAad);
            byte[] ciphertext = algorithm.cipherWithDek(plaintext, session.dekAead(), session.wrappedDek(), encryptAad);
            byte[] wrongWrapAad = "wrong".getBytes(StandardCharsets.UTF_8);
            byte[] wrappedDek = algorithm.extractWrappedDek(ciphertext);
            assertThrows(GeneralSecurityException.class, () -> algorithm.unwrapDek(wrappedDek, kekHandle, wrongWrapAad));
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
            var session = algorithm.createSession(kekHandle, wrapAad);
            byte[] ciphertext = algorithm.cipherWithDek(plaintext, session.dekAead(), session.wrappedDek(), encryptAad);
            byte[] wrongEncryptAad = "wrong".getBytes(StandardCharsets.UTF_8);
            byte[] wrappedDek = algorithm.extractWrappedDek(ciphertext);
            var dekAead = algorithm.unwrapDek(wrappedDek, kekHandle, wrapAad);
            assertThrows(GeneralSecurityException.class, () -> algorithm.decipherWithDek(ciphertext, dekAead, wrongEncryptAad));
        }
    }

    @Test
    @DisplayName("envelope keyset: decrypting with wrong key throws")
    void testEnvelopeKeysetCrossKeyDecryptFails() {
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var metadataA = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA");
            var metadataB = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyB");
            try (var kryptonite = new Kryptonite(keyVault)) {
                byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
                byte[] ciphertext = kryptonite.cipherFieldRaw(plaintext, metadataA);
                assertThrows(KryptoniteException.class, () -> kryptonite.decipherFieldRaw(ciphertext, metadataB));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fresh DEK per call (no session cache)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("envelope keyset: no session cache — each encrypt produces a different wrapped DEK")
    void testFreshDekPerCallWithoutSessionCache() {
        var algorithm = new TinkAesGcmEnvelopeKeyset();
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA");
            try (var kryptonite = new Kryptonite(keyVault)) {
                byte[] ct1 = kryptonite.cipherFieldRaw("hello".getBytes(StandardCharsets.UTF_8), metadata);
                byte[] ct2 = kryptonite.cipherFieldRaw("hello".getBytes(StandardCharsets.UTF_8), metadata);
                assertFalse(Arrays.equals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct2)),
                        "each call without session cache must produce a fresh wrapped DEK");
            }
        }
    }

    // -------------------------------------------------------------------------
    // EncryptDekSession.tryAcquire unit tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("EncryptDekSession: exactly maxEncryptions calls succeed, next returns false")
    void testTryAcquireExactMaxRecords() throws Exception {
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var algorithm = new TinkAesGcmEnvelopeKeyset();
            var kekHandle = keyVault.readKeysetHandle("keyA");
            var wrapAad = "keyA".getBytes(StandardCharsets.UTF_8);
            var session = algorithm.createSession(kekHandle, wrapAad);
            long maxEncryptions = 3;
            long ttlMs = 60_000L;
            assertTrue(session.tryAcquire(maxEncryptions, ttlMs));
            assertTrue(session.tryAcquire(maxEncryptions, ttlMs));
            assertTrue(session.tryAcquire(maxEncryptions, ttlMs));
            assertFalse(session.tryAcquire(maxEncryptions, ttlMs), "4th call must fail after maxEncryptions=3 exhausted");
        }
    }

    @Test
    @DisplayName("EncryptDekSession: tryAcquire returns false after TTL expiry")
    void testTryAcquireFailsAfterTtlExpiry() throws Exception {
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var algorithm = new TinkAesGcmEnvelopeKeyset();
            var kekHandle = keyVault.readKeysetHandle("keyA");
            var wrapAad = "keyA".getBytes(StandardCharsets.UTF_8);
            var session = algorithm.createSession(kekHandle, wrapAad);
            long maxEncryptions = 1_000_000L;
            long ttlMs = 1L; // 1ms TTL
            Thread.sleep(10);  // ensure TTL is exceeded
            assertFalse(session.tryAcquire(maxEncryptions, ttlMs), "tryAcquire must return false after TTL expiry");
        }
    }

    // -------------------------------------------------------------------------
    // EncryptDekSessionCache — DEK reuse and rotation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("encrypt session cache: same wrapped DEK reused within session")
    void testSessionCacheReusesWrappedDek() {
        var algorithm = new TinkAesGcmEnvelopeKeyset();
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA");
            var sessionCache = new EncryptDekSessionCache(1_000_000L, 720L);
            try (var kryptonite = new Kryptonite(keyVault, null, sessionCache)) {
                byte[] ct1 = kryptonite.cipherFieldRaw("hello".getBytes(StandardCharsets.UTF_8), metadata);
                byte[] ct2 = kryptonite.cipherFieldRaw("hello".getBytes(StandardCharsets.UTF_8), metadata);
                assertArrayEquals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct2),
                        "both encryptions within same session must share the same wrapped DEK");
            }
        }
    }

    @Test
    @DisplayName("encrypt session cache: new wrapped DEK after TTL expiry (synthetic clock)")
    void testSessionCacheRotatesAfterTtlExpiry() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKeyset();
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA");
            var clock = new MutableClock();
            var sessionCache = new EncryptDekSessionCache(1_000_000L, 720L, clock);
            try (var kryptonite = new Kryptonite(keyVault, null, sessionCache)) {
                byte[] ct1 = kryptonite.cipherFieldRaw("a".getBytes(StandardCharsets.UTF_8), metadata);
                // advance clock past TTL (720 minutes)
                clock.advance(Duration.ofMinutes(721));
                byte[] ct2 = kryptonite.cipherFieldRaw("b".getBytes(StandardCharsets.UTF_8), metadata);
                assertFalse(Arrays.equals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct2)),
                        "after TTL expiry a new wrapped DEK must be produced");
            }
        }
    }

    @Test
    @DisplayName("encrypt session cache: new wrapped DEK after maxEncryptions exhausted")
    void testSessionCacheRotatesAfterMaxRecords() {
        var algorithm = new TinkAesGcmEnvelopeKeyset();
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA");
            var sessionCache = new EncryptDekSessionCache(2L, 720L); // rotate after 2 uses
            try (var kryptonite = new Kryptonite(keyVault, null, sessionCache)) {
                byte[] ct1 = kryptonite.cipherFieldRaw("a".getBytes(StandardCharsets.UTF_8), metadata);
                byte[] ct2 = kryptonite.cipherFieldRaw("b".getBytes(StandardCharsets.UTF_8), metadata);
                // session exhausted after 2 uses — next call must rotate
                byte[] ct3 = kryptonite.cipherFieldRaw("c".getBytes(StandardCharsets.UTF_8), metadata);
                assertArrayEquals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct2),
                        "first two calls must share the same wrapped DEK");
                assertFalse(Arrays.equals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct3)),
                        "third call must produce a new wrapped DEK after session rotation");
                assertFalse(Arrays.equals(algorithm.extractWrappedDek(ct2), algorithm.extractWrappedDek(ct3)),
                        "third call must produce a new wrapped DEK after session rotation");
            }
        }
    }

    // -------------------------------------------------------------------------
    // WrappedDekCache — decrypt side
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("decrypt DEK cache: unwrapDek loader called once on miss, not called on cache hit")
    void testDecryptCacheLoaderCalledOnlyOnMiss() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKeyset();
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var kekHandle = keyVault.readKeysetHandle("keyA");
            var wrapAad = "keyA".getBytes(StandardCharsets.UTF_8);
            var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA");
            var session = algorithm.createSession(kekHandle, wrapAad);
            byte[] ciphertext = algorithm.cipherWithDek("hello cache".getBytes(StandardCharsets.UTF_8), session.dekAead(), session.wrappedDek(), metadata.asBytes());
            byte[] wrappedDek = algorithm.extractWrappedDek(ciphertext);
            var loadCount = new AtomicInteger(0);
            var dekCache = new WrappedDekCache(1024);
            // first call — cache miss, loader invoked
            dekCache.get(wrappedDek, wdk -> {
                loadCount.incrementAndGet();
                try { return algorithm.unwrapDek(wdk, kekHandle, wrapAad); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            // second call — cache hit, loader must NOT be invoked again
            dekCache.get(wrappedDek, wdk -> {
                loadCount.incrementAndGet();
                try { return algorithm.unwrapDek(wdk, kekHandle, wrapAad); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            assertEquals(1, loadCount.get(), "loader must be called exactly once — second call must hit cache");
        }
    }

    @Test
    @DisplayName("decrypt DEK cache: ciphertext from rotated session still decrypts correctly")
    void testDecryptCacheIndependentOfEncryptSessionRotation() {
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA");
            var sessionCache = new EncryptDekSessionCache(1L, 720L); // rotate after every use
            var dekCache = new WrappedDekCache(1024);
            try (var kryptonite = new Kryptonite(keyVault, dekCache, sessionCache)) {
                byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);
                // encrypt with session A
                byte[] ct1 = kryptonite.cipherFieldRaw(plaintext, metadata);
                // encrypt triggers rotation — session B used
                byte[] ct2 = kryptonite.cipherFieldRaw(plaintext, metadata);
                // both ciphertexts must still decrypt correctly regardless of session rotation
                assertArrayEquals(plaintext, kryptonite.decipherFieldRaw(ct1, metadata));
                assertArrayEquals(plaintext, kryptonite.decipherFieldRaw(ct2, metadata));
            }
        }
    }

    // -------------------------------------------------------------------------
    // EncryptDekSessionCache — independent sessions per keyId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("encrypt session cache: keyA and keyB sessions are independent")
    void testSessionCacheIndependentPerKeyId() {
        var algorithm = new TinkAesGcmEnvelopeKeyset();
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var metadataA = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA");
            var metadataB = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyB");
            var sessionCache = new EncryptDekSessionCache(1_000_000L, 720L);
            try (var kryptonite = new Kryptonite(keyVault, null, sessionCache)) {
                byte[] ctA1 = kryptonite.cipherFieldRaw("a".getBytes(StandardCharsets.UTF_8), metadataA);
                byte[] ctB1 = kryptonite.cipherFieldRaw("b".getBytes(StandardCharsets.UTF_8), metadataB);
                byte[] ctA2 = kryptonite.cipherFieldRaw("c".getBytes(StandardCharsets.UTF_8), metadataA);
                byte[] ctB2 = kryptonite.cipherFieldRaw("d".getBytes(StandardCharsets.UTF_8), metadataB);
                // within same keyId sessions: same wrappedDek
                assertArrayEquals(algorithm.extractWrappedDek(ctA1), algorithm.extractWrappedDek(ctA2),
                        "keyA session must be reused across calls");
                assertArrayEquals(algorithm.extractWrappedDek(ctB1), algorithm.extractWrappedDek(ctB2),
                        "keyB session must be reused across calls");
                // across keyIds: different wrappedDek (different KEKs produce different wrapped bytes)
                assertFalse(Arrays.equals(algorithm.extractWrappedDek(ctA1), algorithm.extractWrappedDek(ctB1)),
                        "keyA and keyB sessions must be independent");
            }
        }
    }

    // -------------------------------------------------------------------------
    // WrappedDekCache — eviction
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("decrypt DEK cache: evicted entry causes loader to be called again")
    void testDecryptCacheEviction() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKeyset();
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var kekHandleA = keyVault.readKeysetHandle("keyA");
            var kekHandleB = keyVault.readKeysetHandle("keyB");
            var wrapAadA = "keyA".getBytes(StandardCharsets.UTF_8);
            var wrapAadB = "keyB".getBytes(StandardCharsets.UTF_8);
            var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA");
            // cache of size 1 — second entry evicts first
            var dekCache = new WrappedDekCache(1);
            var sessionA = algorithm.createSession(kekHandleA, wrapAadA);
            byte[] ct1 = algorithm.cipherWithDek("p1".getBytes(StandardCharsets.UTF_8), sessionA.dekAead(), sessionA.wrappedDek(), metadata.asBytes());
            var sessionB = algorithm.createSession(kekHandleB, wrapAadB);
            byte[] ct2 = algorithm.cipherWithDek("p2".getBytes(StandardCharsets.UTF_8), sessionB.dekAead(), sessionB.wrappedDek(), metadata.asBytes());
            byte[] wrappedDek1 = algorithm.extractWrappedDek(ct1);
            byte[] wrappedDek2 = algorithm.extractWrappedDek(ct2);
            var loadCount = new AtomicInteger(0);
            // first lookup — miss, loads wrappedDek1
            dekCache.get(wrappedDek1, wdk -> { loadCount.incrementAndGet(); try { return algorithm.unwrapDek(wdk, kekHandleA, wrapAadA); } catch (Exception e) { throw new RuntimeException(e); } });
            assertEquals(1, loadCount.get());
            // second lookup — miss, loads wrappedDek2, evicts wrappedDek1
            dekCache.get(wrappedDek2, wdk -> { loadCount.incrementAndGet(); try { return algorithm.unwrapDek(wdk, kekHandleB, wrapAadB); } catch (Exception e) { throw new RuntimeException(e); } });
            assertEquals(2, loadCount.get());
            // Caffeine eviction is asynchronous — force it synchronously before re-checking
            dekCache.cleanUp();
            // third lookup of wrappedDek1 — must be a miss again (evicted)
            dekCache.get(wrappedDek1, wdk -> { loadCount.incrementAndGet(); try { return algorithm.unwrapDek(wdk, kekHandleA, wrapAadA); } catch (Exception e) { throw new RuntimeException(e); } });
            assertEquals(3, loadCount.get(), "evicted entry must trigger loader again on re-lookup");
        }
    }

    // -------------------------------------------------------------------------
    // WrappedDekCache — multiple keyIds coexist
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("decrypt DEK cache: keyA and keyB wrappedDeks coexist without interference")
    void testDecryptCacheMultipleKeyIds() {
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var metadataA = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA");
            var metadataB = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyB");
            var sessionCache = new EncryptDekSessionCache(1_000_000L, 720L);
            var dekCache = new WrappedDekCache(1024);
            try (var kryptonite = new Kryptonite(keyVault, dekCache, sessionCache)) {
                byte[] plaintextA = "secretA".getBytes(StandardCharsets.UTF_8);
                byte[] plaintextB = "secretB".getBytes(StandardCharsets.UTF_8);
                byte[] ctA = kryptonite.cipherFieldRaw(plaintextA, metadataA);
                byte[] ctB = kryptonite.cipherFieldRaw(plaintextB, metadataB);
                // decrypt both — populates cache with two distinct entries
                assertArrayEquals(plaintextA, kryptonite.decipherFieldRaw(ctA, metadataA));
                assertArrayEquals(plaintextB, kryptonite.decipherFieldRaw(ctB, metadataB));
                // decrypt again — both must hit cache and still return correct plaintext
                assertArrayEquals(plaintextA, kryptonite.decipherFieldRaw(ctA, metadataA));
                assertArrayEquals(plaintextB, kryptonite.decipherFieldRaw(ctB, metadataB));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Both caches active — full round-trip
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("both caches active: encrypt with session cache, decrypt with DEK cache")
    void testBothCachesActiveRoundTrip() {
        try (var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG))) {
            var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, "keyA");
            var sessionCache = new EncryptDekSessionCache(1_000_000L, 720L);
            var dekCache = new WrappedDekCache(1024);
            try (var kryptonite = new Kryptonite(keyVault, dekCache, sessionCache)) {
                byte[] plaintext = "full cache round-trip".getBytes(StandardCharsets.UTF_8);
                byte[] ct1 = kryptonite.cipherFieldRaw(plaintext, metadata);
                byte[] ct2 = kryptonite.cipherFieldRaw(plaintext, metadata);
                assertArrayEquals(plaintext, kryptonite.decipherFieldRaw(ct1, metadata));
                assertArrayEquals(plaintext, kryptonite.decipherFieldRaw(ct2, metadata));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Parameterized helpers
    // -------------------------------------------------------------------------

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
