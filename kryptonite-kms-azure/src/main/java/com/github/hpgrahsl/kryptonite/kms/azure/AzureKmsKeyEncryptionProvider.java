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

import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryptionProvider;

/**
 * {@link KmsKeyEncryptionProvider} for Azure Key Vault Keys.
 *
 * <p>Discovered via {@link java.util.ServiceLoader}. Returns {@code "AZURE"} as the KEK type
 * identifier, which corresponds to {@link com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.KekType#AZURE}.
 */
public class AzureKmsKeyEncryptionProvider implements KmsKeyEncryptionProvider {

    @Override
    public String kekType() {
        return "AZURE";
    }

    @Override
    public KmsKeyEncryption createKeyEncryption(String kekUri, String kekConfig) {
        return new AzureKeyEncryption(kekUri, kekConfig);
    }

}
