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

import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.kms.EnvelopeKekEncryption;

/**
 * Azure Key Vault-backed {@link EnvelopeKekEncryption} for envelope KMS encryption.
 *
 * <p>Wraps and unwraps DEK bytes using RSA-OAEP-256 via Azure Key Vault's
 * {@code CryptographyClient.wrapKey} / {@code unwrapKey} APIs. The DEK (16–32 bytes) is well
 * within the RSA plaintext limit, so no local AES-GCM layer is needed here (unlike
 * {@link com.github.hpgrahsl.kryptonite.kms.azure.kek.AzureKmsAead}, which must handle full Tink
 * keyset encryption where RSA plaintext limits would be exceeded).
 *
 * <p><strong>AAD is not supported:</strong> Azure Key Vault's RSA-OAEP-256 key wrap/unwrap
 * operations have no associated-data parameter. The {@code wrapAad} argument passed to
 * {@link #wrapDek} and {@link #unwrapDek} is silently ignored. This differs from the GCP and AWS
 * providers where AAD is conveyed to the KMS. The security guarantee here relies on the EdekStore
 * fingerprint lookup chain rather than cryptographic binding of AAD to the wrapped DEK.
 *
 * <p>The {@code kekUri} must follow the scheme
 * {@code azure-kv://&lt;vault-host&gt;/keys/&lt;key-name&gt;[/&lt;version&gt;]}.
 *
 * <p>The {@code kekConfig} must be a JSON string compatible with {@link AzureKeyVaultConfig}
 * containing at minimum {@code clientId}, {@code tenantId}, and {@code clientSecret}.
 */
public class AzureEnvelopeKekEncryption implements EnvelopeKekEncryption {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CryptographyClient cryptographyClient;

    public AzureEnvelopeKekEncryption(String kekUri, String kekConfig) {
        try {
            AzureKeyVaultConfig cfg = OBJECT_MAPPER.readValue(kekConfig, AzureKeyVaultConfig.class);
            var credential = new ClientSecretCredentialBuilder()
                .clientId(cfg.getClientId())
                .clientSecret(cfg.getClientSecret())
                .tenantId(cfg.getTenantId())
                .build();
            String keyUrl = "https://" + kekUri.substring("azure-kv://".length());
            this.cryptographyClient = new CryptographyClientBuilder()
                .keyIdentifier(keyUrl)
                .credential(credential)
                .buildClient();
        } catch (Exception e) {
            throw new KryptoniteException("failed to initialise Azure envelope KEK: " + e.getMessage(), e);
        }
    }

    /**
     * Wraps {@code rawDek} using RSA-OAEP-256 via Azure Key Vault.
     *
     * @param rawDek  the raw DEK bytes to wrap
     * @param wrapAad ignored — Azure RSA-OAEP key wrap has no AAD parameter
     */
    @Override
    public byte[] wrapDek(byte[] rawDek, byte[] wrapAad) throws Exception {
        return cryptographyClient.wrapKey(KeyWrapAlgorithm.RSA_OAEP_256, rawDek).getEncryptedKey();
    }

    /**
     * Unwraps previously wrapped DEK bytes using RSA-OAEP-256 via Azure Key Vault.
     *
     * @param wrappedDek the wrapped DEK bytes as returned by {@link #wrapDek}
     * @param wrapAad    ignored — Azure RSA-OAEP key unwrap has no AAD parameter
     */
    @Override
    public byte[] unwrapDek(byte[] wrappedDek, byte[] wrapAad) throws Exception {
        return cryptographyClient.unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, wrappedDek).getKey();
    }

}
