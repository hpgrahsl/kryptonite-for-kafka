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

package com.github.hpgrahsl.kryptonite.kms.aws;

import java.nio.charset.StandardCharsets;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KmsClients;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.KmsAeadKeyManager;
import com.google.crypto.tink.integration.awskms.AwsKmsClient;

public class AwsKeyEncryption implements KmsKeyEncryption {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String kekUri;
    private final String credentialsConfig;

    public AwsKeyEncryption(String kekUri, String credentialsConfig) {
        this.kekUri = kekUri;
        this.credentialsConfig = credentialsConfig;
    }

    @Override
    public KeysetHandle getKeyEncryptionKeyHandle() {
        try {
            AeadConfig.register();
            JsonNode config = OBJECT_MAPPER.readTree(
                credentialsConfig.getBytes(StandardCharsets.UTF_8)
            );
            String accessKey = config.get("accessKey").asText();
            String secretKey = config.get("secretKey").asText();
            AwsKmsClient client = new AwsKmsClient(kekUri);
            client.withCredentialsProvider(
                new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(accessKey, secretKey)
                )
            );
            KmsClients.add(client);
            return KeysetHandle.generateNew(KmsAeadKeyManager.createKeyTemplate(kekUri));
        } catch (Exception exc) {
            throw new KryptoniteException(exc.getMessage(), exc);
        }
    }

}
