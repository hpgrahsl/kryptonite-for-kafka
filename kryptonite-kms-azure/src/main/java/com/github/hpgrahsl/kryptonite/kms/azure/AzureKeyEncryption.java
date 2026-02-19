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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.github.hpgrahsl.kryptonite.kms.azure.kek.AzureKmsAead;
import com.github.hpgrahsl.kryptonite.kms.azure.kek.AzureKmsClient;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KmsClients;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.KmsAeadKeyManager;

/**
 * {@link KmsKeyEncryption} implementation backed by Azure Key Vault Keys.
 *
 * <p>Registers an {@link AzureKmsClient} with Tink's global KMS client registry and returns a
 * {@link KeysetHandle} wrapping the Azure Key Vault key identified by {@code kekUri}. The handle's
 * AEAD primitive delegates to {@link AzureKmsAead}, which performs envelope encryption using
 * RSA-OAEP-256 key wrapping and local AES-256-GCM.
 *
 * <p>The {@code credentialsConfig} must be a JSON string compatible with
 * {@link AzureKeyVaultConfig} containing {@code clientId}, {@code tenantId}, and
 * {@code clientSecret}.
 *
 * <p>The {@code kekUri} must follow the scheme
 * {@code azure-kv://&lt;vault-host&gt;/keys/&lt;key-name&gt;[/&lt;version&gt;]}.
 */
public class AzureKeyEncryption implements KmsKeyEncryption {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String kekUri;
    private final String credentialsConfig;

    public AzureKeyEncryption(String kekUri, String credentialsConfig) {
        this.kekUri = kekUri;
        this.credentialsConfig = credentialsConfig;
    }

    @Override
    public KeysetHandle getKeyEncryptionKeyHandle() {
        try {
            AeadConfig.register();
            AzureKeyVaultConfig cfg = OBJECT_MAPPER.readValue(credentialsConfig, AzureKeyVaultConfig.class);
            KmsClients.add(
                new AzureKmsClient(kekUri).withCredentials(cfg.getClientId(), cfg.getClientSecret(), cfg.getTenantId())
            );
            return KeysetHandle.generateNew(KmsAeadKeyManager.createKeyTemplate(kekUri));
        } catch (Exception exc) {
            throw new KryptoniteException(exc.getMessage(), exc);
        }
    }

}
