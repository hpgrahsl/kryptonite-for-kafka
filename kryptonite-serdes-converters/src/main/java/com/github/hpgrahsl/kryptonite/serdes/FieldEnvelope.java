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

package com.github.hpgrahsl.kryptonite.serdes;

import com.github.hpgrahsl.kryptonite.EncryptedField;

/**
 * Internal result type used by {@link FieldHandler}: carries a decoded {@link EncryptedField}
 * together with the serde code that identifies which {@link SerdeProcessor} was used to
 * serialize the plaintext field value at encrypt time.
 *
 * <p>For k1 (legacy Kryo) envelopes the serde code is always
 * {@link KryoSerdeProcessorProvider#SERDE_CODE} ({@code "00"}).
 * For k2 envelopes it is read directly from the binary header.
 *
 * <p>Callers should use {@link FieldHandler#encryptField} and {@link FieldHandler#decryptField}
 * rather than working with this type directly.
 */
record FieldEnvelope(EncryptedField encryptedField, String serdeCode) {

}
