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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;

import com.github.hpgrahsl.kryptonite.keys.KeyNotFoundException;
import com.github.hpgrahsl.kryptonite.TestFixtures;
import com.github.hpgrahsl.kryptonite.TestFixturesCloudKms;

@EnabledIfSystemProperty(named = "cloud.kms.tests", matches = "true")
public class AzureKeyVaultTest {

    static final AzureSecretResolver SECRET_RESOLVER_PLAIN_KEYS =
        new AzureSecretResolver(TestFixturesCloudKms.readCredentials().getProperty("test.kms.az_kv_secrets.config"));    

    @Test
    void azureKeyVaultWithoutPrefetchingLoadsFromValidConfigTest() {
        
        var azureKeyVault = new AzureKeyVault(SECRET_RESOLVER_PLAIN_KEYS,false);

        assertEquals(0, azureKeyVault.numKeysetHandles(),
            "error: key vault expected to be initially empty"
        );
        assertAll(
            TestFixtures.CIPHER_DATA_KEY_IDENTIFIERS_PLAIN.stream().<Executable>map(
                    id -> () -> assertNotNull(azureKeyVault.readKeysetHandle(id),
                        "error: known keyset identifier "+id+" not found in key vault")
            )
        );
        assertEquals(TestFixtures.CIPHER_DATA_KEYS_COUNT, azureKeyVault.numKeysetHandles(),
            "error: key vault expected to contain all "+TestFixtures.CIPHER_DATA_KEYS_COUNT
                + " key(s) after requesting each known identifier"
        );
        assertThrows(KeyNotFoundException.class,() -> azureKeyVault.readKeysetHandle(TestFixtures.UNKNOWN_KEYSET_IDENTIFIER_PLAIN));
    }

    @Test
    void azureKeyVaultWithPrefetchingLoadsFromValidConfigTest() {
        
        var azureKeyVault = new AzureKeyVault(SECRET_RESOLVER_PLAIN_KEYS,true);
        
        //FIXME: azure test keyvault contains more entries -> remove the irrelevant ones
        // assertEquals(TestFixtures.CIPHER_DATA_KEY_IDENTIFIERS_PLAIN.size(), azureKeyVault.numKeysetHandles(),
        //     "error: key vault expected to initially contain all known identifiers"
        // );
        assertAll(
            TestFixtures.CIPHER_DATA_KEY_IDENTIFIERS_PLAIN.stream().<Executable>map(
                    id -> () -> assertNotNull(azureKeyVault.readKeysetHandle(id),
                        "error: known keyset identifier "+id+" not found in key vault")
            )
        );
        assertThrows(KeyNotFoundException.class,() -> azureKeyVault.readKeysetHandle(TestFixtures.UNKNOWN_KEYSET_IDENTIFIER_PLAIN));

    }

}
