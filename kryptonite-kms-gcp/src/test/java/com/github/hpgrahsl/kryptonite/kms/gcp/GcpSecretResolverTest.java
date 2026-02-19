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

package com.github.hpgrahsl.kryptonite.kms.gcp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.TestFixtures;
import com.github.hpgrahsl.kryptonite.TestFixturesCloudKms;
import com.github.hpgrahsl.kryptonite.config.ConfigReader;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig;
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

@EnabledIfSystemProperty(named = "cloud.kms.tests", matches = "true")
public class GcpSecretResolverTest {

    static final GcpSecretResolver SECRET_RESOLVER_PLAIN_KEYS =
        new GcpSecretResolver(
            TestFixturesCloudKms.configureGcpSecretManagerClient("test.kms.config"),
            TestFixturesCloudKms.readGcpProjectId("test.kms.config"),
            GcpKeyVault.SECRET_NAME_PREFIX
        );

    static final Map<String, TinkKeyConfig> TINK_KEY_CONFIGS =
        ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG);

    @Test
    @DisplayName("resolve key identifiers (plain keysets) from GCP Secret Manager")
    void testResolveKeyIdentifiersForPlainKeySetsFromGcpSecretManager() {
        var keysetIds = SECRET_RESOLVER_PLAIN_KEYS.resolveIdentifiers().stream().collect(Collectors.toSet());
        assertAll(
            TestFixtures.CIPHER_DATA_KEY_IDENTIFIERS_PLAIN.stream().map(
                id -> () -> assertTrue(keysetIds.contains(id),
                    "expected identifier '" + id + "' not found in resolved identifiers: " + keysetIds)
            )
        );
    }

    @ParameterizedTest
    @MethodSource("provideValidPlainKeySetIdentifiers")
    @DisplayName("create plain tink keyset for valid secrets resolved from GCP Secret Manager by identifier")
    void testCreatePlainTinkKeySetFromResolvedIdentifier(String identifier) throws IOException {
        var rawKeySet = SECRET_RESOLVER_PLAIN_KEYS.resolveKeyset(identifier);
        var tinkKeySetPlain = new ObjectMapper().readValue(rawKeySet, TinkKeyConfig.class);
        assertEquals(TINK_KEY_CONFIGS.get(identifier), tinkKeySetPlain);
    }

    static Stream<Arguments> provideValidPlainKeySetIdentifiers() {
        return TINK_KEY_CONFIGS.keySet().stream().map(Arguments::of);
    }

}
