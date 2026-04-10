/*
 * Copyright (c) 2021. Hans-Peter Grahsl (grahslhp@gmail.com)
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

import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.kms.EnvelopeKekEncryption;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.RegistryConfiguration;

/**
 * AWS KMS-backed {@link EnvelopeKekEncryption} for envelope KMS encryption.
 *
 * <p>Delegates to the existing {@link AwsKeyEncryption} infrastructure:
 * {@code getKeyEncryptionKeyHandle().getPrimitive(Aead.class)} produces a Tink {@code Aead}
 * backed by an AWS KMS key — every {@code encrypt}/{@code decrypt} call is a real KMS network
 * round-trip. The raw key material never leaves the AWS KMS.
 *
 * <p>AWS KMS encrypt/decrypt APIs support an {@code EncryptionContext} map which Tink passes
 * the AAD through, so {@code wrapAad} is conveyed to the KMS call.
 */
public class AwsEnvelopeKekEncryption implements EnvelopeKekEncryption {

    private final Aead kekAead;

    public AwsEnvelopeKekEncryption(String kekUri, String kekConfig) {
        try {
            this.kekAead = new AwsKeyEncryption(kekUri, kekConfig)
                .getKeyEncryptionKeyHandle()
                .getPrimitive(RegistryConfiguration.get(), Aead.class);
        } catch (Exception e) {
            throw new KryptoniteException("failed to initialise AWS envelope KEK: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] wrapDek(byte[] rawDekBytes, byte[] wrapAad) throws Exception {
        return kekAead.encrypt(rawDekBytes, wrapAad);
    }

    @Override
    public byte[] unwrapDek(byte[] wrappedDek, byte[] wrapAad) throws Exception {
        return kekAead.decrypt(wrappedDek, wrapAad);
    }

}
