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
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Facade for serializing and deserializing the {@link EncryptedField} envelope.
 *
 * <p>Replaces the previous inline {@code serde.objectToBytes(ef, EncryptedField.class)} /
 * {@code serde.bytesToObject(bytes, EncryptedField.class)} calls across all modules. Version
 * sniffing and serde dispatch are fully transparent to callers.
 *
 * <h2>Envelope formats</h2>
 * <ul>
 *   <li><b>k1</b> (legacy) — Kryo {@code writeObject(EncryptedField)}. No version prefix in the
 *       binary; the version string "k1" is buried inside the Kryo stream. Always decoded via
 *       {@link KryoSerdeProcessor} as a permanent fallback.</li>
 *   <li><b>k2</b> (new) — hand-rolled binary with a fixed header readable without any
 *       serde involvement:
 *       <pre>
 *       [2 bytes : "k2" ASCII magic]
 *       [2 bytes : serde code      ]  e.g. "00" = KRYO, "01" = AVRO
 *       [2 bytes : algorithm ID    ]  e.g. "02" = AES_GCM
 *       [1 byte  : keyId length    ]  unsigned, max 255 chars
 *       [N bytes : keyId UTF-8     ]
 *       [M bytes : ciphertext      ]  to end of buffer, no length prefix
 *       </pre>
 *   </li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // encrypt path — caller creates k2 PayloadMetaData, runs Kryptonite.cipherField, then:
 * byte[] envelope = FieldHandler.serialize(encryptedField, KryoSerdeProcessorProvider.SERDE_CODE);
 *
 * // decrypt path — version sniffed automatically:
 * FieldEnvelope fe = FieldHandler.deserialize(envelope);
 * byte[] plaintext = kryptonite.decipherField(fe.encryptedField());
 * Object value = SerdeRegistry.getProcessorByCode(fe.serdeCode()).bytesToObject(plaintext);
 * }</pre>
 */
public final class FieldHandler {

  static final String K2_VERSION = Kryptonite.KRYPTONITE_VERSION_K2;
  private static final byte[] K2_MAGIC = K2_VERSION.getBytes(StandardCharsets.UTF_8);
  private static final KryoSerdeProcessor K1_SERDE = new KryoSerdeProcessor();

  private FieldHandler() {}

  /**
   * Serializes an {@link EncryptedField} to bytes.
   *
   * <p>If {@code ef.getMetaData().getVersion()} equals {@code "k2"} the result uses the k2 binary
   * format with {@code serdeCode} embedded in the header. Otherwise the legacy k1 Kryo format is
   * written and {@code serdeCode} is ignored.
   *
   * @param ef        the encrypted field to serialize
   * @param serdeCode 2-character wire code for k2 envelopes (ignored for k1)
   * @return serialized envelope bytes
   */
  public static byte[] serialize(EncryptedField ef, String serdeCode) {
    if (K2_VERSION.equals(ef.getMetaData().getVersion())) {
      return serializeK2(ef, serdeCode);
    }
    return K1_SERDE.objectToBytes(ef, EncryptedField.class);
  }

  /**
   * Deserializes an envelope byte array produced by {@link #serialize}.
   *
   * <p>Version is sniffed from the first 2 bytes:
   * <ul>
   *   <li>If the bytes start with {@code "k2"} → parse k2 binary format.</li>
   *   <li>Otherwise → assume k1 Kryo; delegate to {@link KryoSerdeProcessor} as permanent
   *       fallback. The serde code in the returned {@link FieldEnvelope} is implicitly
   *       {@link KryoSerdeProcessorProvider#SERDE_CODE} ({@code "00"}).</li>
   * </ul>
   *
   * @param bytes raw envelope bytes
   * @return {@link FieldEnvelope} with the decoded {@link EncryptedField} and serde code
   */
  public static FieldEnvelope deserialize(byte[] bytes) {
    if (bytes.length >= 2 && bytes[0] == K2_MAGIC[0] && bytes[1] == K2_MAGIC[1]) {
      return deserializeK2(bytes);
    }
    return FieldEnvelope.k1((EncryptedField) K1_SERDE.bytesToObject(bytes, EncryptedField.class));
  }

  // --- private helpers ---

  private static byte[] serializeK2(EncryptedField ef, String serdeCode) {
    if (serdeCode == null || serdeCode.length() != 2) {
      throw new IllegalArgumentException(
          "serdeCode must be exactly 2 characters for k2 envelopes, got: " + serdeCode);
    }
    var meta = ef.getMetaData();
    if (meta.getAlgorithmId() == null || meta.getAlgorithmId().length() != 2) {
      throw new IllegalArgumentException(
          "algorithmId must be exactly 2 characters for k2 envelopes, got: " + meta.getAlgorithmId());
    }
    byte[] kiBytes = meta.getKeyId().getBytes(StandardCharsets.UTF_8);
    if (kiBytes.length > 255) {
      throw new IllegalArgumentException(
          "keyId UTF-8 encoding exceeds max 255 bytes for k2 envelopes: " + kiBytes.length);
    }
    byte[] scBytes = serdeCode.getBytes(StandardCharsets.UTF_8);
    byte[] aiBytes = meta.getAlgorithmId().getBytes(StandardCharsets.UTF_8);
    byte[] ct = ef.ciphertext();
    ByteBuffer buf = ByteBuffer.allocate(2 + 2 + 2 + 1 + kiBytes.length + ct.length);
    buf.put(K2_MAGIC);
    buf.put(scBytes);
    buf.put(aiBytes);
    buf.put((byte) kiBytes.length);
    buf.put(kiBytes);
    buf.put(ct);
    return buf.array();
  }

  private static FieldEnvelope deserializeK2(byte[] bytes) {
    ByteBuffer buf = ByteBuffer.wrap(bytes);
    buf.position(2); // skip K2 magic
    byte[] sc = new byte[2];
    buf.get(sc);
    String serdeCode = new String(sc, StandardCharsets.UTF_8);
    byte[] ai = new byte[2];
    buf.get(ai);
    String algorithmId = new String(ai, StandardCharsets.UTF_8);
    int kiLen = buf.get() & 0xFF; // unsigned byte
    byte[] ki = new byte[kiLen];
    buf.get(ki);
    String keyId = new String(ki, StandardCharsets.UTF_8);
    byte[] ciphertext = new byte[buf.remaining()];
    buf.get(ciphertext);
    var meta = new PayloadMetaData(K2_VERSION, algorithmId, keyId);
    return new FieldEnvelope(new EncryptedField(meta, ciphertext), serdeCode);
  }

}
