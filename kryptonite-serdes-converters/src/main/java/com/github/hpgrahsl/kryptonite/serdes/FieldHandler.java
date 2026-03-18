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
import java.util.Base64;

/**
 * Complete encrypt/decrypt pipeline facade for Kryptonite field-level encryption.
 *
 * <p>Owns the full wire format contract in both directions — serde resolution,
 * serialization, encryption, envelope assembly, Base64 encoding — so that no
 * module needs to interact with {@link SerdeProcessor}, {@link SerdeRegistry},
 * or the envelope binary layout directly.
 *
 * <h2>Envelope formats</h2>
 * <ul>
 * <li><b>k1</b> (legacy) — Kryo {@code writeObject(EncryptedField)}. No version
 * prefix in the binary; the version string "k1" is buried inside the Kryo
 * stream. Transparently decoded as a permanent backwards-compatible
 * fallback.</li>
 * <li><b>k2</b> (current) — hand-rolled binary with a fixed header readable
 * without any serde involvement:
 *
 * <pre>
 *   [2 bytes : "k2" ASCII magic]
 *   [2 bytes : serde code      ]  e.g. "00" = KRYO, "01" = AVRO
 *   [2 bytes : algorithm ID    ]  e.g. "02" = AES_GCM
 *   [1 byte  : keyId length    ]  unsigned, max 255 bytes
 *   [N bytes : keyId UTF-8     ]
 *   [M bytes : ciphertext      ]  to end of buffer, no length prefix
 * </pre>
 * </li>
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // encrypt — one call covers serde, cipher, envelope, Base64:
 * String encoded = FieldHandler.encryptField(value, metadata, kryptonite, "KRYO");
 *
 * // decrypt — version sniffing, serde dispatch, and Base64 decoding are internal:
 * Object value = FieldHandler.decryptField(encoded, kryptonite);
 * }</pre>
 */
public final class FieldHandler {

  static final String WIRE_FORMAT_VERSION = Kryptonite.KRYPTONITE_VERSION;
  private static final byte[] MAGIC_BYTES_VERSION = WIRE_FORMAT_VERSION.getBytes(StandardCharsets.UTF_8);
  private static final KryoSerdeProcessor LEGACY_SERDE = new KryoSerdeProcessor();

  private FieldHandler() {
  }

  /**
   * Full encrypt pipeline: serialize value → cipher → wrap envelope → Base64
   * encode.
   *
   * <p>
   * The serde is resolved from {@link SerdeRegistry} by {@code serdeName} (e.g.
   * {@code "KRYO"}).
   * Its wire code is embedded in the new binary format "k2" envelope header so the decrypt side can
   * reconstruct the value without any caller-supplied hint.
   *
   * @param value      plaintext field value
   * @param metadata   payload metadata (version, algorithmId, keyId)
   * @param kryptonite cipher engine
   * @param serdeName  supported config-facing serde name (e.g. {@code "KRYO"})
   *
   * @return Base64-encoded envelope string for Kryptonite version "k2" (new binary format)
   */
  public static String encryptField(Object value, PayloadMetaData metadata,
      Kryptonite kryptonite, String serdeName) {
    SerdeProcessor serde = SerdeRegistry.getProcessorByName(serdeName);
    byte[] valueBytes = serde.objectToBytes(value);
    byte[] ciphertext = kryptonite.cipherFieldRaw(valueBytes, metadata);
    byte[] envelopeBytes = serialize(new EncryptedField(metadata, ciphertext), serde.serdeCode());
    return Base64.getEncoder().encodeToString(envelopeBytes);
  }

  /**
   * Full decrypt pipeline: Base64 decode → sniff version → decipher → deserialize
   * value.
   *
   * @param encoded    Base64-encoded envelope string produced by
   *                   {@link #encryptField}
   * @param kryptonite cipher engine
   * @return deserialized plaintext field value
   */
  public static Object decryptField(String encoded, Kryptonite kryptonite) {
    FieldEnvelope fieldEnvelope = deserialize(Base64.getDecoder().decode(encoded));
    byte[] plaintext = kryptonite.decipherFieldRaw(
        fieldEnvelope.encryptedField().ciphertext(), fieldEnvelope.encryptedField().getMetaData());
    return SerdeRegistry.getProcessorByCode(fieldEnvelope.serdeCode()).bytesToObject(plaintext);
  }

  // --- private helpers ---

  /**
   * Serializes an {@link EncryptedField} to bytes.
   *
   * <p>
   * If {@code ef.getMetaData().getVersion()} equals {@code "k2"} the result uses
   * the k2 binary
   * format with {@code serdeCode} embedded in the header. Otherwise the legacy k1
   * Kryo format is
   * written and {@code serdeCode} is ignored.
   *
   * @param encryptedField the encrypted field to serialize
   * @param serdeCode      2-character wire code for k2 envelopes (ignored for k1)
   * @return serialized envelope bytes
   */
  private static byte[] serialize(EncryptedField encryptedField, String serdeCode) {
    if (WIRE_FORMAT_VERSION.equals(encryptedField.getMetaData().getVersion())) {
      return serializeNew(encryptedField, serdeCode);
    }
    // legacy format implicitly KRYO only
    return LEGACY_SERDE.objectToBytes(encryptedField, EncryptedField.class);
  }

  /**
   * Deserializes an envelope byte array produced by {@link #serialize}.
   *
   * <p>
   * Version is sniffed from the first 2 bytes:
   * <ul>
   * <li>If the bytes start with {@code "k2"} → parse k2 binary format.</li>
   * <li>Otherwise → assume k1 Kryo; delegate to {@link KryoSerdeProcessor} as
   * permanent
   * fallback. The serde code in the returned {@link FieldEnvelope} is implicitly
   * {@link KryoSerdeProcessorProvider#SERDE_CODE} ({@code "00"}).</li>
   * </ul>
   *
   * @param bytes raw envelope bytes
   * @return {@link FieldEnvelope} with the decoded {@link EncryptedField} and
   *         serde code
   */
  private static FieldEnvelope deserialize(byte[] bytes) {
    if (bytes.length >= 2 && bytes[0] == MAGIC_BYTES_VERSION[0] && bytes[1] == MAGIC_BYTES_VERSION[1]) {
      return deserializeNew(bytes);
    }
    // legacy format implicitly KRYO only
    return new FieldEnvelope(
        (EncryptedField) LEGACY_SERDE.bytesToObject(bytes, EncryptedField.class),
        KryoSerdeProcessorProvider.SERDE_CODE);
  }

  private static byte[] serializeNew(EncryptedField encryptedField, String serdeCode) {
    if (serdeCode == null || serdeCode.length() != 2) {
      throw new IllegalArgumentException(
          "serdeCode must be exactly 2 characters for k2 envelopes, got: " + serdeCode);
    }
    var metaData = encryptedField.getMetaData();
    if (metaData.getAlgorithmId() == null || metaData.getAlgorithmId().length() != 2) {
      throw new IllegalArgumentException(
          "algorithmId must be exactly 2 characters for k2 envelopes, got: " + metaData.getAlgorithmId());
    }
    byte[] keyIdBytes = metaData.getKeyId().getBytes(StandardCharsets.UTF_8);
    if (keyIdBytes.length > 255) {
      throw new IllegalArgumentException(
          "keyId UTF-8 encoding exceeds max 255 bytes for k2 envelopes: " + keyIdBytes.length);
    }
    byte[] serdeCodeBytes = serdeCode.getBytes(StandardCharsets.UTF_8);
    byte[] algorithmIdBytes = metaData.getAlgorithmId().getBytes(StandardCharsets.UTF_8);
    byte[] ciphertextBytes = encryptedField.ciphertext();
    ByteBuffer buffer = ByteBuffer.allocate(2 + 2 + 2 + 1 + keyIdBytes.length + ciphertextBytes.length);
    buffer.put(MAGIC_BYTES_VERSION);
    buffer.put(serdeCodeBytes);
    buffer.put(algorithmIdBytes);
    buffer.put((byte) keyIdBytes.length);
    buffer.put(keyIdBytes);
    buffer.put(ciphertextBytes);
    return buffer.array();
  }

  private static FieldEnvelope deserializeNew(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    buffer.position(2); // skip K2 magic
    byte[] serdeCodeBytes = new byte[2];
    buffer.get(serdeCodeBytes);
    String serdeCode = new String(serdeCodeBytes, StandardCharsets.UTF_8);
    byte[] algorithmIdBytes = new byte[2];
    buffer.get(algorithmIdBytes);
    String algorithmId = new String(algorithmIdBytes, StandardCharsets.UTF_8);
    int keyIdLength = buffer.get() & 0xFF; // unsigned byte
    byte[] keyIdBytes = new byte[keyIdLength];
    buffer.get(keyIdBytes);
    String keyId = new String(keyIdBytes, StandardCharsets.UTF_8);
    byte[] ciphertextBytes = new byte[buffer.remaining()];
    buffer.get(ciphertextBytes);
    var metaData = new PayloadMetaData(WIRE_FORMAT_VERSION, algorithmId, keyId);
    return new FieldEnvelope(new EncryptedField(metaData, ciphertextBytes), serdeCode);
  }

}
