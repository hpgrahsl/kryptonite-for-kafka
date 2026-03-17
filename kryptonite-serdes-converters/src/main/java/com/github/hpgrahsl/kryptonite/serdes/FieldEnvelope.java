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
 * Result of {@link FieldHandler#deserialize}: carries the decoded {@link EncryptedField} together
 * with the serde code that identifies which {@link SerdeProcessor} was used to serialize the
 * plaintext field value at encrypt time.
 *
 * <p>For k1 (legacy Kryo) envelopes the serde code is always
 * {@link KryoSerdeProcessorProvider#SERDE_CODE} ({@code "00"}).
 * For k2 envelopes it is read directly from the binary header.
 *
 * <p>Usage on the decrypt path:
 * <pre>{@code
 * FieldEnvelope envelope = FieldHandler.deserialize(rawBytes);
 * byte[] plaintext = kryptonite.decipherField(envelope.encryptedField());
 * SerdeProcessor serde = SerdeRegistry.getProcessorByCode(envelope.serdeCode());
 * Object value = serde.bytesToObject(plaintext);
 * }</pre>
 */
public record FieldEnvelope(EncryptedField encryptedField, String serdeCode) {

  /**
   * Convenience factory for k1 (Kryo) envelopes where the serde code is implicit.
   */
  public static FieldEnvelope k1(EncryptedField encryptedField) {
    return new FieldEnvelope(encryptedField, KryoSerdeProcessorProvider.SERDE_CODE);
  }

}
