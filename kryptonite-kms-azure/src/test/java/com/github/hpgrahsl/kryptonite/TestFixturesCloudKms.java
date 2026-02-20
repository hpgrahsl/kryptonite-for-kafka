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

package com.github.hpgrahsl.kryptonite;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.ServiceLoader;

import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.config.ConfigurationException;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.KekType;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryptionProvider;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureKeyVaultConfig;

public class TestFixturesCloudKms {

    private static final String PATH = "src/test/resources/credentials.properties";
    private static final Properties CREDENTIALS = new Properties();

    public static synchronized Properties readCredentials() {
        if (!CREDENTIALS.isEmpty()) {
            return CREDENTIALS;
        }
        try (InputStreamReader isr = new FileReader(new File(PATH), StandardCharsets.UTF_8)) {
            CREDENTIALS.load(isr);
            return CREDENTIALS;
        } catch (IOException exc) {
            throw new ConfigurationException(exc);
        }
    }

    public static KmsKeyEncryption configureKmsKeyEncryption() {
        try {
            Properties credentials = TestFixturesCloudKms.readCredentials();
            var kekType = KekType.valueOf(credentials.getProperty("test.kek.type"));
            var kekConfig = credentials.getProperty("test.kek.config");
            var kekUri = credentials.getProperty("test.kek.uri");
            var provider = ServiceLoader.load(KmsKeyEncryptionProvider.class).stream()
                    .map(ServiceLoader.Provider::get)
                    .filter(p -> p.kekType().equals(kekType.name()))
                    .findFirst()
                    .orElseThrow(() -> new ConfigurationException(
                            "no KMS key encryption provider found for type '" + kekType + "'"));
            return provider.createKeyEncryption(kekUri, kekConfig);

        } catch (Exception exc) {
            throw new ConfigurationException(exc);
        }
    }

    public static SecretClient configureAzureSecretClient(String configPropertyKey) {
        Properties cloudKmsCredentials;
        try {
            cloudKmsCredentials = TestFixturesCloudKms.readCredentials();
            var keyVaultConfig = new ObjectMapper().readValue(cloudKmsCredentials.getProperty(configPropertyKey),
                    AzureKeyVaultConfig.class);
            return new SecretClientBuilder()
                    .vaultUrl(keyVaultConfig.getKeyVaultUrl())
                    .credential(new ClientSecretCredentialBuilder()
                            .clientId(keyVaultConfig.getClientId())
                            .clientSecret(keyVaultConfig.getClientSecret())
                            .tenantId(keyVaultConfig.getTenantId())
                            .build())
                    .buildClient();
        } catch (IOException exc) {
            throw new ConfigurationException(exc);
        }

    }

}
