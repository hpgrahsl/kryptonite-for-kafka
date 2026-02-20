/*
 * Copyright (c) 2024. Hans-Peter Grahsl (grahslhp@gmail.com)
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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.config.ConfigReader;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfigEncrypted;
import com.github.hpgrahsl.kryptonite.TestFixturesCloudKms;
import com.github.hpgrahsl.kryptonite.tink.test.EncryptedKeysetsWithAzureKek;
import com.github.hpgrahsl.kryptonite.tink.test.PlaintextKeysets;

@EnabledIfSystemProperty(named = "cloud.kms.tests", matches = "true")
public class AzureSecretResolverTest {

    static final AzureSecretResolver SECRET_RESOLVER_PLAIN_KEYS =
        new AzureSecretResolver(TestFixturesCloudKms.configureAzureSecretClient("test.kms.config"));

    static final AzureSecretResolver SECRET_RESOLVER_ENCRYPTED_KEYS =
        new AzureSecretResolver(TestFixturesCloudKms.configureAzureSecretClient("test.kms.config.encrypted"));
    
    static final Map<String,TinkKeyConfig> TINK_KEY_CONFIGS =
        ConfigReader.tinkKeyConfigFromJsonString(PlaintextKeysets.CIPHER_DATA_KEYS_CONFIG);
    static final Map<String,TinkKeyConfigEncrypted> TINK_KEY_CONFIGS_ENCRYPTED =
        ConfigReader.tinkKeyConfigEncryptedFromJsonString(EncryptedKeysetsWithAzureKek.CIPHER_DATA_KEYS_CONFIG_ENCRYPTED);
    
    @Test
    @DisplayName("resolve key identifiers (plain keysets) from azure key vault")
    void testResolveKeyIdentifiersForPlainKeySetsFromAzureKeyVault() {
        var keysetIds = SECRET_RESOLVER_PLAIN_KEYS.resolveIdentifiers().stream().collect(Collectors.toSet());
        assertAll(
            PlaintextKeysets.CIPHER_DATA_KEY_IDENTIFIERS_PLAIN.stream().map(
                id -> () -> assertTrue(keysetIds.contains(id))
            )
        );
    }

    @Test
    @DisplayName("resolve key identifiers (encrypted keysets) from azure key vault")
    void testResolveKeyIdentifiersForEncryptedKeySetsFromAzureKeyVault() {
        var keysetIds = SECRET_RESOLVER_ENCRYPTED_KEYS.resolveIdentifiers().stream().collect(Collectors.toSet());
        assertAll(
            EncryptedKeysetsWithAzureKek.CIPHER_DATA_KEY_IDENTIFIERS_ENCRYPTED.stream().map(
                id -> () -> assertTrue(keysetIds.contains(id))
            )
        );
    }

    @ParameterizedTest
    @MethodSource("com.github.hpgrahsl.kryptonite.kms.azure.AzureSecretResolverTest#provideValidPlainKeySetIdentifiers")
    @DisplayName("create plain tink keyset for valid secrets resolved from azure key vault by identifier")  
    void testCreatePlainTinkKeySetFromResolvedIdentifier(String identifier) throws IOException {
        var rawKeySet = SECRET_RESOLVER_PLAIN_KEYS.resolveKeyset(identifier);
        var tinkKeySetPlain = new ObjectMapper().readValue(rawKeySet,TinkKeyConfig.class);
        assertEquals(TINK_KEY_CONFIGS.get(identifier),tinkKeySetPlain);
    }

    @ParameterizedTest
    @MethodSource("com.github.hpgrahsl.kryptonite.kms.azure.AzureSecretResolverTest#provideValidEncryptedKeySetIdentifiers")
    @DisplayName("create encrypted tink keyset for valid secrets resolved from azure key vault by identifier")  
    void testCreateEncryptedTinkKeySetFromResolvedIdentifier(String identifier) throws IOException {
        var rawKeySet = SECRET_RESOLVER_ENCRYPTED_KEYS.resolveKeyset(identifier);
        var tinkKeySetEncrypted = new ObjectMapper().readValue(rawKeySet,TinkKeyConfigEncrypted.class);
        assertEquals(TINK_KEY_CONFIGS_ENCRYPTED.get(identifier),tinkKeySetEncrypted);
    }

    static Stream<Arguments> provideValidPlainKeySetIdentifiers() {
        return TINK_KEY_CONFIGS.keySet().stream().map(k -> Arguments.of(k));
    }

    static Stream<Arguments> provideValidEncryptedKeySetIdentifiers() {
        return TINK_KEY_CONFIGS_ENCRYPTED.keySet().stream().map(k -> Arguments.of(k));
    }
    
}
