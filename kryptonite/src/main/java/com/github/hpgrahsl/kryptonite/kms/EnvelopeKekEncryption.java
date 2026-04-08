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
 * Abstraction for envelope KMS encryption: the KEK exclusively lives in a cloud KMS.
 * DEK wrap/unwrap is a real KMS network call.
 *
 * <p>Distinct from {@link KmsKeyEncryption} which is used for keyset-at-rest protection
 * (CONFIG_ENCRYPTED / KMS_ENCRYPTED) and returns a local Tink {@code KeysetHandle}.
 * Here we perform direct byte-level wrap/unwrap without involving a Tink keyset.
 */
public interface EnvelopeKekEncryption {

    /**
     * Wraps raw DEK bytes using the cloud KMS KEK.
     *
     * @param rawDek the raw DEK bytes to wrap (e.g. 16 bytes for AES-128)
     * @param wrapAad     associated data bound to the wrap operation (e.g. keyId bytes);
     *                    may be ignored by providers whose KMS algorithm does not support AAD.
     * @return the wrapped DEK bytes
     * @throws Exception on KMS error or credential failure
     */
    byte[] wrapDek(byte[] rawDek, byte[] wrapAad) throws Exception;

    /**
     * Unwraps previously wrapped DEK bytes using the cloud KMS KEK.
     *
     * @param wrappedDek the wrapped DEK bytes as returned by {@link #wrapDek}
     * @param wrapAad    associated data — must match the value used during {@link #wrapDek}
     * @return the raw DEK bytes
     * @throws Exception on KMS error, authentication failure, or AAD mismatch
     */
    byte[] unwrapDek(byte[] wrappedDek, byte[] wrapAad) throws Exception;

}
