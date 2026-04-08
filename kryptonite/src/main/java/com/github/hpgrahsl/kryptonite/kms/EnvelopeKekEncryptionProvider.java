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

package com.github.hpgrahsl.kryptonite.kms;

/**
 * ServiceLoader interface for creating {@link EnvelopeKekEncryption} instances.
 *
 * <p>One implementation per cloud KMS module (GCP, AWS). Each module registers its
 * implementation in {@code META-INF/services/com.github.hpgrahsl.kryptonite.kms.EnvelopeKekEncryptionProvider}.
 *
 * <p>Parallel to {@link KmsKeyEncryptionProvider} which handles keyset-at-rest protection.
 */
public interface EnvelopeKekEncryptionProvider {

    /**
     * Returns the KMS type string this provider handles (e.g. "GCP", "AWS").
     * Matched against the {@code kek_type} field in {@code envelope_kek_configs} entries.
     */
    String kekType();

    /**
     * Creates an {@link EnvelopeKekEncryption} for the given KMS key URI and credentials config.
     *
     * @param kekUri    KMS key URI (e.g. {@code gcp-kms://...}, {@code aws-kms://...})
     * @param kekConfig credentials/connection config JSON string for this KMS provider
     */
    EnvelopeKekEncryption createEnvelopeKekEncryption(String kekUri, String kekConfig);

}
