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

import com.github.hpgrahsl.kryptonite.kms.EnvelopeKekEncryption;
import com.github.hpgrahsl.kryptonite.kms.EnvelopeKekEncryptionProvider;

/**
 * {@link EnvelopeKekEncryptionProvider} for Azure Key Vault.
 *
 * <p>Envelope KMS encryption ({@code TINK/AES_GCM_ENVELOPE_KMS}) with Azure Key Vault uses
 * RSA-OAEP-256 key wrapping via {@link AzureEnvelopeKekEncryption}. The wrapped DEK (~256 bytes)
 * is stored in the {@code EdekStore} (external Kafka topic); only a compact 16-byte fingerprint
 * travels with each ciphertext, so the RSA-OAEP output size is not an issue.
 *
 * <p>Note: Azure RSA-OAEP key wrap has no associated-data parameter, so the {@code wrapAad}
 * argument is ignored. See {@link AzureEnvelopeKekEncryption} for details.
 */
public class AzureEnvelopeKekEncryptionProvider implements EnvelopeKekEncryptionProvider {

    @Override
    public String kekType() {
        return "AZURE";
    }

    @Override
    public EnvelopeKekEncryption createEnvelopeKekEncryption(String kekUri, String kekConfig) {
        return new AzureEnvelopeKekEncryption(kekUri, kekConfig);
    }

}
