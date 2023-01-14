/*
 * Copyright (c) 2022. Hans-Peter Grahsl (grahslhp@gmail.com)
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KmsClients;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.KmsAeadKeyManager;
import com.google.crypto.tink.integration.gcpkms.GcpKmsClient;

public class GcpKeyEncryption implements KmsKeyEncryption {

    private final String kekUri;
    private final String credentialsConfig;

    public GcpKeyEncryption(String kekUri, String credentialsConfig) {
        this.kekUri = kekUri;
        this.credentialsConfig = credentialsConfig;
    }

    @Override
    public KeysetHandle getKeyEnryptionKeyHandle() {
        try {
            AeadConfig.register();
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                new ByteArrayInputStream(credentialsConfig.getBytes(StandardCharsets.UTF_8))
            );
            KmsClients.add(new GcpKmsClient(kekUri).withCredentials(credentials));
            return KeysetHandle.generateNew(KmsAeadKeyManager.createKeyTemplate(kekUri));
        } catch (Exception exc) {
            throw new KryptoniteException(exc.getMessage(), exc);   
        }
    }

}
