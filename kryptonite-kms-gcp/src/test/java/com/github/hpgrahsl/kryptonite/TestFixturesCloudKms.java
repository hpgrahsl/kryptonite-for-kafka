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

package com.github.hpgrahsl.kryptonite;

import com.github.hpgrahsl.kryptonite.config.ConfigurationException;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.KekType;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryptionProvider;
import com.github.hpgrahsl.kryptonite.kms.gcp.GcpSecretManagerConfig;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.ServiceLoader;

public class TestFixturesCloudKms {

    private static final String PATH = "src/test/resources/credentials.properties";
    private static final Properties CREDENTIALS = new Properties();

    public static synchronized Properties readCredentials() {
        if (!CREDENTIALS.isEmpty()) {
            return CREDENTIALS;
        }
        try (var reader = new FileReader(PATH, StandardCharsets.UTF_8)) {
            CREDENTIALS.load(reader);
            return CREDENTIALS;
        } catch (IOException exc) {
            throw new ConfigurationException(exc);
        }
    }

    public static KmsKeyEncryption configureKmsKeyEncryption() {
        try {
            Properties credentials = readCredentials();
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

    public static SecretManagerServiceClient configureGcpSecretManagerClient(String configPropertyKey) {
        try {
            Properties credentials = readCredentials();
            var config = new GcpSecretManagerConfig(credentials.getProperty(configPropertyKey));
            GoogleCredentials googleCredentials = GoogleCredentials.fromStream(
                new ByteArrayInputStream(config.getCredentials().getBytes(StandardCharsets.UTF_8))
            );
            SecretManagerServiceSettings settings = SecretManagerServiceSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(googleCredentials))
                .build();
            return SecretManagerServiceClient.create(settings);
        } catch (IOException exc) {
            throw new ConfigurationException(exc);
        }
    }

    public static String readGcpProjectId(String configPropertyKey) {
        var config = new GcpSecretManagerConfig(
            readCredentials().getProperty(configPropertyKey)
        );
        return config.getProjectId();
    }

}
