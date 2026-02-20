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

package com.github.hpgrahsl.kryptonite.kms.azure.kek;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KmsClient;
import java.security.GeneralSecurityException;

/**
 * A Tink {@link KmsClient} implementation for Azure Key Vault Keys.
 *
 * <p>Handles URIs of the form {@code azure-kv://&lt;vault-host&gt;/keys/&lt;key-name&gt;} and
 * optionally {@code azure-kv://&lt;vault-host&gt;/keys/&lt;key-name&gt;/&lt;version&gt;}.
 *
 * <p>The URI is converted to a standard Azure Key Vault key URL by replacing the
 * {@code azure-kv://} scheme prefix with {@code https://}.
 */
public class AzureKmsClient implements KmsClient {

    static final String URI_PREFIX = "azure-kv://";

    private final String keyUri;
    private TokenCredential credential;

    /**
     * Creates a client that supports any {@code azure-kv://} URI.
     */
    public AzureKmsClient() {
        this.keyUri = null;
    }

    /**
     * Creates a client bound to the given key URI.
     * {@link #doesSupport(String)} will only return {@code true} for this exact URI.
     */
    public AzureKmsClient(String keyUri) {
        if (!keyUri.toLowerCase().startsWith(URI_PREFIX)) {
            throw new IllegalArgumentException("key URI must start with " + URI_PREFIX);
        }
        this.keyUri = keyUri;
    }

    @Override
    public boolean doesSupport(String uri) {
        if (!uri.toLowerCase().startsWith(URI_PREFIX)) {
            return false;
        }
        return keyUri == null || keyUri.equals(uri);
    }

    @Override
    public KmsClient withCredentials(String credentialPath) throws GeneralSecurityException {
        throw new UnsupportedOperationException(
            "use withCredentials(TokenCredential) to provide Azure credentials directly");
    }

    @Override
    public KmsClient withDefaultCredentials() throws GeneralSecurityException {
        this.credential = new DefaultAzureCredentialBuilder().build();
        return this;
    }

    /**
     * Configures this client with an explicit {@link TokenCredential}.
     */
    public AzureKmsClient withCredentials(TokenCredential credential) {
        this.credential = credential;
        return this;
    }

    /**
     * Configures this client with service-principal credentials.
     */
    public AzureKmsClient withCredentials(String clientId, String clientSecret, String tenantId) {
        this.credential = new ClientSecretCredentialBuilder()
            .clientId(clientId)
            .clientSecret(clientSecret)
            .tenantId(tenantId)
            .build();
        return this;
    }

    @Override
    public Aead getAead(String uri) throws GeneralSecurityException {
        if (!doesSupport(uri)) {
            throw new GeneralSecurityException("this client does not support URI: " + uri);
        }
        if (credential == null) {
            throw new GeneralSecurityException("no credentials configured for AzureKmsClient");
        }
        String keyUrl = "https://" + uri.substring(URI_PREFIX.length());
        var cryptographyClient = new CryptographyClientBuilder()
            .keyIdentifier(keyUrl)
            .credential(credential)
            .buildClient();
        return new AzureKmsAead(cryptographyClient);
    }

}
