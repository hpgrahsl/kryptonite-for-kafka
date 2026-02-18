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

package com.github.hpgrahsl.kryptonite.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.cli.KeysetGeneratorCommand.Algorithm;
import com.github.hpgrahsl.kryptonite.cli.KeysetGeneratorCommand.KeySize;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfigEncrypted;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import com.google.crypto.tink.daead.DeterministicAeadConfig;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EncryptedKeysetGenerationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static Aead kekAead;

    @BeforeAll
    static void setup() throws Exception {
        AeadConfig.register();
        DeterministicAeadConfig.register();
        KeysetHandle kekHandle = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM);
        kekAead = kekHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);
    }

    @Test
    @DisplayName("encrypt AES_GCM keyset and decrypt it back produces valid keyset handle")
    void testEncryptDecryptRoundTripAesGcm() throws Exception {
        var generator = new TinkAeadKeysetGenerator(Algorithm.AES_GCM, KeySize.BITS_256, 1, 10000);
        KeysetHandle originalHandle = generator.generateHandle();

        String encryptedJson = TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            originalHandle, kekAead, new byte[0]);

        var encNode = OBJECT_MAPPER.readTree(encryptedJson);
        assertTrue(encNode.has("encryptedKeyset"), "encrypted JSON should have encryptedKeyset field");
        assertTrue(encNode.has("keysetInfo"), "encrypted JSON should have keysetInfo field");

        KeysetHandle decryptedHandle = TinkJsonProtoKeysetFormat.parseEncryptedKeyset(
            encryptedJson, kekAead, new byte[0]);
        assertNotNull(decryptedHandle);

        byte[] testData = "hello world".getBytes(StandardCharsets.UTF_8);
        Aead originalAead = originalHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);
        Aead decryptedAead = decryptedHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);
        byte[] ciphertext = originalAead.encrypt(testData, null);
        byte[] plaintext = decryptedAead.decrypt(ciphertext, null);
        assertArrayEquals(testData, plaintext);
    }

    @Test
    @DisplayName("encrypt AES_GCM_SIV keyset and decrypt it back produces valid keyset handle")
    void testEncryptDecryptRoundTripAesGcmSiv() throws Exception {
        var generator = new TinkAeadKeysetGenerator(Algorithm.AES_GCM_SIV, KeySize.BITS_256, 1, 20000);
        KeysetHandle originalHandle = generator.generateHandle();

        String encryptedJson = TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            originalHandle, kekAead, new byte[0]);

        KeysetHandle decryptedHandle = TinkJsonProtoKeysetFormat.parseEncryptedKeyset(
            encryptedJson, kekAead, new byte[0]);
        assertNotNull(decryptedHandle);
    }

    @Test
    @DisplayName("encrypted keyset JSON is compatible with TinkKeyConfigEncrypted data model")
    void testEncryptedJsonMatchesDataModel() throws Exception {
        var generator = new TinkAeadKeysetGenerator(Algorithm.AES_GCM, KeySize.BITS_256, 2, 30000);
        KeysetHandle handle = generator.generateHandle();

        String encryptedJson = TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            handle, kekAead, new byte[0]);

        TinkKeyConfigEncrypted config = OBJECT_MAPPER.readValue(encryptedJson, TinkKeyConfigEncrypted.class);
        assertNotNull(config.getEncryptedKeyset());
        assertNotNull(config.getKeysetInfo());
        assertNotNull(config.getKeysetInfo().getKeyInfo());
        assertTrue(config.getKeysetInfo().getKeyInfo().size() >= 1);
        assertTrue(config.getKeysetInfo().getPrimaryKeyId() > 0);
    }

    @Test
    @DisplayName("encrypted multi-key keyset preserves key metadata in keysetInfo")
    void testEncryptedMultiKeyKeysetPreservesMetadata() throws Exception {
        var generator = new TinkAeadKeysetGenerator(Algorithm.AES_GCM, KeySize.BITS_256, 3, 40000);
        KeysetHandle handle = generator.generateHandle();

        String encryptedJson = TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            handle, kekAead, new byte[0]);

        TinkKeyConfigEncrypted config = OBJECT_MAPPER.readValue(encryptedJson, TinkKeyConfigEncrypted.class);
        var keyInfos = config.getKeysetInfo().getKeyInfo();
        assertTrue(keyInfos.size() >= 3, "keysetInfo should reflect all keys");
    }

    @Test
    @DisplayName("encrypt FPE_FF31 keyset and decrypt it back produces valid keyset handle")
    void testEncryptDecryptRoundTripFpeFf31() throws Exception {
        var generator = new FpeKeysetGenerator(KeySize.BITS_256, 1, 50000);
        KeysetHandle originalHandle = generator.generateHandle();

        String encryptedJson = TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            originalHandle, kekAead, new byte[0]);

        var encNode = OBJECT_MAPPER.readTree(encryptedJson);
        assertTrue(encNode.has("encryptedKeyset"), "encrypted JSON should have encryptedKeyset field");
        assertTrue(encNode.has("keysetInfo"), "encrypted JSON should have keysetInfo field");

        KeysetHandle decryptedHandle = TinkJsonProtoKeysetFormat.parseEncryptedKeyset(
            encryptedJson, kekAead, new byte[0]);
        assertNotNull(decryptedHandle);

        TinkKeyConfigEncrypted config = OBJECT_MAPPER.readValue(encryptedJson, TinkKeyConfigEncrypted.class);
        var keyInfo = config.getKeysetInfo().getKeyInfo().iterator().next();
        assertTrue(keyInfo.getTypeUrl().contains("FpeKey"),
            "keysetInfo should reference FPE key type");
    }

    @Test
    @DisplayName("encrypted FPE_FF31 keyset JSON is compatible with TinkKeyConfigEncrypted data model")
    void testEncryptedFpeJsonMatchesDataModel() throws Exception {
        var generator = new FpeKeysetGenerator(KeySize.BITS_128, 2, 60000);
        KeysetHandle handle = generator.generateHandle();

        String encryptedJson = TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            handle, kekAead, new byte[0]);

        TinkKeyConfigEncrypted config = OBJECT_MAPPER.readValue(encryptedJson, TinkKeyConfigEncrypted.class);
        assertNotNull(config.getEncryptedKeyset());
        assertNotNull(config.getKeysetInfo());
        assertNotNull(config.getKeysetInfo().getKeyInfo());
        assertTrue(config.getKeysetInfo().getKeyInfo().size() >= 2);
        assertTrue(config.getKeysetInfo().getPrimaryKeyId() > 0);
    }
}
