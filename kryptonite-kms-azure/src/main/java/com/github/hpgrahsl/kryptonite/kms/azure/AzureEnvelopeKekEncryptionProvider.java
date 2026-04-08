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
 * {@link EnvelopeKekEncryptionProvider} stub for Azure Key Vault.
 *
 * <p>Envelope KMS encryption ({@code TINK/AES_GCM_ENVELOPE_KMS}) is <strong>not supported</strong>
 * for Azure Key Vault Standard or Premium. Both tiers only expose RSA and EC keys; RSA-OAEP
 * wrapping produces 256–512 bytes per wrapped DEK, which is unsuitable for per-field per-record
 * embedding in the ciphertext bundle.
 *
 * <p>The viable implementation path would be Azure Managed HSM with AES Key Wrap ({@code A256KW}),
 * which would produce significantly more compact wrapped DEKs. This is not implemented because Azure Managed HSM
 * is a substantially more expensive and operationally heavier offering that cannot be assumed
 * to be available.
 *
 * <p>This provider is registered via {@link java.util.ServiceLoader} so that a clear
 * {@link UnsupportedOperationException} is thrown at startup rather than a generic
 * "provider not found" error.
 */
public class AzureEnvelopeKekEncryptionProvider implements EnvelopeKekEncryptionProvider {

    @Override
    public String kekType() {
        return "AZURE";
    }

    @Override
    public EnvelopeKekEncryption createEnvelopeKekEncryption(String kekUri, String kekConfig) {
        throw new UnsupportedOperationException(
            "envelope KMS encryption is not supported for kek_type 'AZURE': "
            + "Azure Key Vault Standard/Premium only support RSA/EC keys — RSA-OAEP wrapped DEK "
            + "size (256–512 bytes) is unsuitable for per-field per-record embedding. "
            + "Azure Managed HSM (AES Key Wrap / A256KW) would be the viable path but is not yet implemented.");
    }

}
