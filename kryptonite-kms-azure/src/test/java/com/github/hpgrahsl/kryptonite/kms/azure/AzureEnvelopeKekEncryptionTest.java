/*
 * Copyright (c) 2026. Hans-Peter Grahsl (grahslhp@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.hpgrahsl.kryptonite.kms.azure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.TestFixturesCloudKms;
import com.github.hpgrahsl.kryptonite.keys.EdekStore;
import com.github.hpgrahsl.kryptonite.tink.test.EdekStoreFixtures;
import com.github.hpgrahsl.kryptonite.config.EnvelopeKekConfig;
import com.github.hpgrahsl.kryptonite.crypto.EncryptDekSessionCache;
import com.github.hpgrahsl.kryptonite.crypto.WrappedDekCache;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmEnvelopeKms;
import com.github.hpgrahsl.kryptonite.keys.EnvelopeKekRegistry;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.github.hpgrahsl.kryptonite.kms.EnvelopeKekEncryption;
import com.google.crypto.tink.aead.AeadConfig;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "cloud.kms.tests", matches = "true")
public class AzureEnvelopeKekEncryptionTest {

    static final String KEK_ID = "azure-kek-1";
    static final String ALGORITHM_ID = Kryptonite.CIPHERSPEC_ID_LUT.get(
            CipherSpec.fromName(TinkAesGcmEnvelopeKms.CIPHER_ALGORITHM));

    static EnvelopeKekEncryption KEK_ENCRYPTION;
    static EnvelopeKekRegistry KEK_REGISTRY;

    @BeforeAll
    static void setup() throws Exception {
        AeadConfig.register();
        var credentials = TestFixturesCloudKms.readCredentials();
        KEK_ENCRYPTION = TestFixturesCloudKms.configureEnvelopeKekEncryption();
        KEK_REGISTRY = new EnvelopeKekRegistry(List.of(
            new EnvelopeKekConfig(
                KEK_ID,
                credentials.getProperty("test.kek.type"),
                credentials.getProperty("test.kek.uri"),
                credentials.getProperty("test.kek.config")
            )
        ));
    }

    // -------------------------------------------------------------------------
    // EnvelopeKekEncryption — raw wrapDek / unwrapDek
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Azure: wrapDek → unwrapDek round-trip recovers original DEK bytes")
    void wrapDekUnwrapDekRoundTrip() throws Exception {
        byte[] rawDek = new byte[16];
        new java.security.SecureRandom().nextBytes(rawDek);
        byte[] wrapAad = KEK_ID.getBytes(StandardCharsets.UTF_8);

        byte[] wrappedDek = KEK_ENCRYPTION.wrapDek(rawDek, wrapAad);
        byte[] unwrapped = KEK_ENCRYPTION.unwrapDek(wrappedDek, wrapAad);

        assertArrayEquals(rawDek, unwrapped);
    }

    @Test
    @DisplayName("Azure: unwrapDek succeeds even with different AAD (Azure RSA-OAEP ignores AAD)")
    void unwrapDekWithDifferentAadSucceeds() throws Exception {
        byte[] rawDek = new byte[16];
        new java.security.SecureRandom().nextBytes(rawDek);
        byte[] wrapAad = KEK_ID.getBytes(StandardCharsets.UTF_8);

        byte[] wrappedDek = KEK_ENCRYPTION.wrapDek(rawDek, wrapAad);
        // Azure RSA-OAEP has no AAD — different AAD must not cause failure
        byte[] differentAad = "different-aad".getBytes(StandardCharsets.UTF_8);
        byte[] unwrapped = KEK_ENCRYPTION.unwrapDek(wrappedDek, differentAad);

        assertArrayEquals(rawDek, unwrapped,
            "Azure RSA-OAEP key wrap ignores AAD — unwrap must succeed regardless of AAD value");
    }

    @Test
    @DisplayName("Azure: two independent wrapDek calls produce distinct wrapped DEK bytes")
    void independentWrapDekCallsProduceDistinctOutput() throws Exception {
        byte[] rawDek = new byte[16];
        new java.security.SecureRandom().nextBytes(rawDek);
        byte[] wrapAad = KEK_ID.getBytes(StandardCharsets.UTF_8);

        byte[] wrapped1 = KEK_ENCRYPTION.wrapDek(rawDek, wrapAad);
        byte[] wrapped2 = KEK_ENCRYPTION.wrapDek(rawDek, wrapAad);

        assertFalse(Arrays.equals(wrapped1, wrapped2),
            "each wrapDek call must produce a distinct ciphertext (probabilistic RSA-OAEP)");
    }

    // -------------------------------------------------------------------------
    // TinkAesGcmEnvelopeKms — direct API
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Azure: TinkAesGcmEnvelopeKms createSession → cipherWithDek → unwrapDek → decipherWithDek round-trip")
    void envelopeKmsSessionRoundTrip() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKms();
        byte[] wrapAad = KEK_ID.getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "envelope kms round-trip".getBytes(StandardCharsets.UTF_8);
        byte[] encryptAad = "meta".getBytes(StandardCharsets.UTF_8);

        var session = algorithm.createSession(KEK_ENCRYPTION, wrapAad);
        byte[] fingerprint = EdekStore.fingerprint(session.wrappedDek());
        byte[] ciphertext = algorithm.cipherWithDek(plaintext, session.dekAead(), fingerprint, encryptAad);

        var dekAead = algorithm.unwrapDek(session.wrappedDek(), KEK_ENCRYPTION, wrapAad);
        byte[] decrypted = algorithm.decipherWithDek(ciphertext, dekAead, encryptAad);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Azure: two independent sessions produce distinct wrappedDek bytes")
    void independentSessionsProduceDistinctWrappedDeks() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKms();
        byte[] wrapAad = KEK_ID.getBytes(StandardCharsets.UTF_8);

        var session1 = algorithm.createSession(KEK_ENCRYPTION, wrapAad);
        var session2 = algorithm.createSession(KEK_ENCRYPTION, wrapAad);

        assertFalse(Arrays.equals(session1.wrappedDek(), session2.wrappedDek()),
            "each session must wrap a fresh DEK — wrappedDek bytes must differ");
    }

    // -------------------------------------------------------------------------
    // Kryptonite integration — cipherFieldRaw / decipherFieldRaw
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Azure: Kryptonite envelope KMS cipherFieldRaw → decipherFieldRaw round-trip")
    void kryptoniteEnvelopeKmsRoundTrip() {
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, KEK_ID);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        var dekCache = new WrappedDekCache(1024);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), dekCache, sessionCache, KEK_REGISTRY, EdekStoreFixtures.inMemoryEdekStore(), 16)) {
            byte[] plaintext = "azure envelope-kms field".getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = kryptonite.cipherFieldRaw(plaintext, metadata);
            byte[] decrypted = kryptonite.decipherFieldRaw(ciphertext, metadata);
            assertArrayEquals(plaintext, decrypted);
        }
    }

    @Test
    @DisplayName("Azure: envelope KMS session cache reuses wrappedDek within maxEncryptions limit")
    void kryptoniteSessionCacheReusesWrappedDek() {
        var algorithm = new TinkAesGcmEnvelopeKms();
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, KEK_ID);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY, EdekStoreFixtures.inMemoryEdekStore(), 16)) {
            byte[] ct1 = kryptonite.cipherFieldRaw("a".getBytes(StandardCharsets.UTF_8), metadata);
            byte[] ct2 = kryptonite.cipherFieldRaw("b".getBytes(StandardCharsets.UTF_8), metadata);
            assertArrayEquals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct2),
                "both encryptions within the same session must share the same wrappedDek");
        }
    }

    @Test
    @DisplayName("Azure: envelope KMS session cache rotates wrappedDek after maxEncryptions exhausted")
    void kryptoniteSessionCacheRotatesAfterMaxEncryptions() {
        var algorithm = new TinkAesGcmEnvelopeKms();
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, KEK_ID);
        var sessionCache = new EncryptDekSessionCache(2L, 720L); // rotate after 2 uses
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, KEK_REGISTRY, EdekStoreFixtures.inMemoryEdekStore(), 16)) {
            byte[] ct1 = kryptonite.cipherFieldRaw("a".getBytes(StandardCharsets.UTF_8), metadata);
            byte[] ct2 = kryptonite.cipherFieldRaw("b".getBytes(StandardCharsets.UTF_8), metadata);
            // session exhausted after 2 uses — next call must wrap a fresh DEK via KMS
            byte[] ct3 = kryptonite.cipherFieldRaw("c".getBytes(StandardCharsets.UTF_8), metadata);
            assertArrayEquals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct2),
                "first two calls must share the same wrappedDek");
            assertFalse(Arrays.equals(algorithm.extractWrappedDek(ct1), algorithm.extractWrappedDek(ct3)),
                "third call must produce a new wrappedDek after session rotation");
        }
    }

    @Test
    @DisplayName("Azure: envelope KMS decrypt cache loader called once on miss, not called on hit")
    void kryptoniteDecryptCacheLoaderCalledOnlyOnMiss() throws Exception {
        var algorithm = new TinkAesGcmEnvelopeKms();
        byte[] wrapAad = KEK_ID.getBytes(StandardCharsets.UTF_8);

        var session = algorithm.createSession(KEK_ENCRYPTION, wrapAad);

        var loadCount = new AtomicInteger(0);
        var dekCache = new WrappedDekCache(1024);

        dekCache.get(session.wrappedDek(), wdk -> {
            loadCount.incrementAndGet();
            try { return algorithm.unwrapDek(wdk, KEK_ENCRYPTION, wrapAad); }
            catch (Exception e) { throw new KryptoniteException(e); }
        });
        dekCache.get(session.wrappedDek(), wdk -> {
            loadCount.incrementAndGet();
            try { return algorithm.unwrapDek(wdk, KEK_ENCRYPTION, wrapAad); }
            catch (Exception e) { throw new KryptoniteException(e); }
        });

        assertEquals(1, loadCount.get(),
            "KMS unwrap loader must be called exactly once — second lookup must hit cache");
    }

    @Test
    @DisplayName("Azure: envelope KMS ciphertext from rotated session still decrypts correctly")
    void kryptoniteRotatedSessionDecryptsCorrectly() {
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, KEK_ID);
        var sessionCache = new EncryptDekSessionCache(1L, 720L); // rotate after every use
        var dekCache = new WrappedDekCache(1024);
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), dekCache, sessionCache, KEK_REGISTRY, EdekStoreFixtures.inMemoryEdekStore(), 16)) {
            byte[] plaintext = "rotate test".getBytes(StandardCharsets.UTF_8);
            byte[] ct1 = kryptonite.cipherFieldRaw(plaintext, metadata);
            byte[] ct2 = kryptonite.cipherFieldRaw(plaintext, metadata); // triggers rotation
            assertArrayEquals(plaintext, kryptonite.decipherFieldRaw(ct1, metadata));
            assertArrayEquals(plaintext, kryptonite.decipherFieldRaw(ct2, metadata));
        }
    }

    @Test
    @DisplayName("Azure: envelope KMS without envelope_kek_configs configured throws KryptoniteException")
    void kryptoniteEnvelopeKmsWithoutRegistryThrows() {
        var metadata = new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, ALGORITHM_ID, KEK_ID);
        var sessionCache = new EncryptDekSessionCache(100_000L, 720L);
        // null registry — envelope KMS not configured
        try (var kryptonite = new Kryptonite(new TinkKeyVault(Map.of()), null, sessionCache, null)) {
            assertThrows(KryptoniteException.class,
                () -> kryptonite.cipherFieldRaw("x".getBytes(StandardCharsets.UTF_8), metadata));
        }
    }

}
