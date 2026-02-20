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

package com.github.hpgrahsl.kryptonite.kms.aws;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.hpgrahsl.kryptonite.TestFixturesCloudKms;
import com.github.hpgrahsl.kryptonite.tink.test.PlaintextKeysets;
import com.github.hpgrahsl.kryptonite.keys.KeyNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;

@EnabledIfSystemProperty(named = "cloud.kms.tests", matches = "true")
public class AwsKeyVaultTest {

    static final AwsSecretResolver SECRET_RESOLVER_PLAIN_KEYS =
        new AwsSecretResolver(
            TestFixturesCloudKms.configureAwsSecretsManagerClient("test.kms.config"),
            AwsKeyVault.SECRET_NAME_PREFIX
        );

    @Test
    void awsKeyVaultWithoutPrefetchingLoadsFromValidConfigTest() {

        var awsKeyVault = new AwsKeyVault(SECRET_RESOLVER_PLAIN_KEYS, false);

        assertEquals(0, awsKeyVault.numKeysetHandles(),
            "error: key vault expected to be initially empty"
        );
        assertAll(
            PlaintextKeysets.CIPHER_DATA_KEY_IDENTIFIERS_PLAIN.stream().<Executable>map(
                id -> () -> assertNotNull(awsKeyVault.readKeysetHandle(id),
                    "error: known keyset identifier " + id + " not found in key vault")
            )
        );
        assertEquals(PlaintextKeysets.CIPHER_DATA_KEYS_COUNT_PLAIN, awsKeyVault.numKeysetHandles(),
            "error: key vault expected to contain all " + PlaintextKeysets.CIPHER_DATA_KEYS_COUNT_PLAIN
                + " key(s) after requesting each known identifier"
        );
        assertThrows(KeyNotFoundException.class,
            () -> awsKeyVault.readKeysetHandle(PlaintextKeysets.UNKNOWN_KEYSET_IDENTIFIER_PLAIN));
    }

    @Test
    void awsKeyVaultWithPrefetchingLoadsFromValidConfigTest() {

        var awsKeyVault = new AwsKeyVault(SECRET_RESOLVER_PLAIN_KEYS, true);

        assertEquals(PlaintextKeysets.CIPHER_DATA_KEYS_COUNT_PLAIN, awsKeyVault.numKeysetHandles(),
            "error: key vault expected to initially contain all " + PlaintextKeysets.CIPHER_DATA_KEYS_COUNT_PLAIN + " known identifiers"
        );
        assertAll(
            PlaintextKeysets.CIPHER_DATA_KEY_IDENTIFIERS_PLAIN.stream().<Executable>map(
                id -> () -> assertNotNull(awsKeyVault.readKeysetHandle(id),
                    "error: known keyset identifier " + id + " not found in key vault")
            )
        );
        assertThrows(KeyNotFoundException.class,
            () -> awsKeyVault.readKeysetHandle(PlaintextKeysets.UNKNOWN_KEYSET_IDENTIFIER_PLAIN));
    }

}
