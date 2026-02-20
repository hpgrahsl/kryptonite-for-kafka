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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.hpgrahsl.kryptonite.tink.test.EncryptedKeysetsWithGcpKek;
import com.github.hpgrahsl.kryptonite.TestFixturesCloudKms;
import com.github.hpgrahsl.kryptonite.keys.KeyNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;

@EnabledIfSystemProperty(named = "cloud.kms.tests", matches = "true")
public class GcpKeyVaultEncryptedTest {

    static final GcpSecretResolver SECRET_RESOLVER_ENCRYPTED_KEYS =
        new GcpSecretResolver(
            TestFixturesCloudKms.configureGcpSecretManagerClient("test.kms.config.encrypted"),
            TestFixturesCloudKms.readGcpProjectId("test.kms.config.encrypted"),
            GcpKeyVaultEncrypted.SECRET_NAME_PREFIX
        );

    @Test
    void gcpKeyVaultEncryptedWithoutPrefetchingLoadsFromValidConfigTest() {

        var gcpKeyVault = new GcpKeyVaultEncrypted(
            TestFixturesCloudKms.configureKmsKeyEncryption(),
            SECRET_RESOLVER_ENCRYPTED_KEYS,
            false
        );

        assertEquals(0, gcpKeyVault.numKeysetHandles(),
            "error: key vault expected to be initially empty"
        );
        assertAll(
            EncryptedKeysetsWithGcpKek.CIPHER_DATA_KEY_IDENTIFIERS_ENCRYPTED.stream().<Executable>map(
                id -> () -> assertNotNull(gcpKeyVault.readKeysetHandle(id),
                    "error: known keyset identifier " + id + " not found in key vault")
            )
        );
        assertEquals(EncryptedKeysetsWithGcpKek.CIPHER_DATA_KEY_IDENTIFIERS_ENCRYPTED.size(), gcpKeyVault.numKeysetHandles(),
            "error: key vault expected to contain all " + EncryptedKeysetsWithGcpKek.CIPHER_DATA_KEY_IDENTIFIERS_ENCRYPTED.size()
                + " key(s) after requesting each known identifier"
        );
        assertThrows(KeyNotFoundException.class,
            () -> gcpKeyVault.readKeysetHandle(EncryptedKeysetsWithGcpKek.UNKNOWN_KEYSET_IDENTIFIER_ENCRYPTED));
    }

    @Test
    void gcpKeyVaultEncryptedWithPrefetchingLoadsFromValidConfigTest() {

        var gcpKeyVault = new GcpKeyVaultEncrypted(
            TestFixturesCloudKms.configureKmsKeyEncryption(),
            SECRET_RESOLVER_ENCRYPTED_KEYS,
            true
        );

        assertEquals(EncryptedKeysetsWithGcpKek.CIPHER_DATA_KEY_IDENTIFIERS_ENCRYPTED.size(), gcpKeyVault.numKeysetHandles(),
            "error: key vault expected to initially contain all "
                + EncryptedKeysetsWithGcpKek.CIPHER_DATA_KEY_IDENTIFIERS_ENCRYPTED.size() + " known identifiers"
        );
        assertAll(
            EncryptedKeysetsWithGcpKek.CIPHER_DATA_KEY_IDENTIFIERS_ENCRYPTED.stream().<Executable>map(
                id -> () -> assertNotNull(gcpKeyVault.readKeysetHandle(id),
                    "error: known keyset identifier " + id + " not found in key vault")
            )
        );
        assertThrows(KeyNotFoundException.class,
            () -> gcpKeyVault.readKeysetHandle(EncryptedKeysetsWithGcpKek.UNKNOWN_KEYSET_IDENTIFIER_ENCRYPTED));
    }

}
