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

package com.github.hpgrahsl.kryptonite.crypto.tink;

import com.github.hpgrahsl.kryptonite.crypto.AeadAlgorithm;
import com.github.hpgrahsl.kryptonite.crypto.EncryptDekSession;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.aead.AesGcmKey;
import com.google.crypto.tink.aead.AesGcmParameters;
import com.google.crypto.tink.util.SecretBytes;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Clock;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * Envelope encryption using a Tink keyset as the KEK (Key Encryption Key).
 *
 * <p>The DEK is a raw 16-byte AES-128-GCM key. Only the raw key bytes are wrapped by the KEK.
 *
 * <p>Encrypt path:
 * <ol>
 *   <li>Generate 16 fresh random DEK bytes</li>
 *   <li>Wrap the raw DEK bytes with the KEK using {@code wrapAad} as AAD</li>
 *   <li>Reconstruct a Tink {@link Aead} from the raw DEK bytes</li>
 *   <li>Encrypt the plaintext with the DEK using {@code encryptAad} as AAD</li>
 *   <li>Bundle: {@code [4-byte wrappedDekLen | wrappedDek | dekCiphertext]}</li>
 * </ol>
 *
 * <p>Decrypt path:
 * <ol>
 *   <li>Split the bundle to extract {@code wrappedDek} and {@code dekCiphertext}</li>
 *   <li>Unwrap raw DEK bytes using {@code wrapAad} — must match encrypt time</li>
 *   <li>Reconstruct Tink {@link Aead} from the raw DEK bytes</li>
 *   <li>Decrypt the ciphertext using the DEK with {@code encryptAad} as AAD</li>
 * </ol>
 *
 * <p>{@code wrapAad} is {@code keyId.getBytes(UTF_8)}, sourced from the parsed envelope header.
 * {@code encryptAad} is the full payload metadata bytes (version + algorithmId + keyId).
 */
public class TinkAesGcmEnvelopeKeyset implements AeadAlgorithm {

  private static final System.Logger LOG = System.getLogger(TinkAesGcmEnvelopeKeyset.class.getName());

  public static final String CIPHER_ALGORITHM = "TINK/AES_GCM_ENVELOPE_KEYSET";

  private static final int DEK_SIZE_BYTES = 16;
  private static final int AES_GCM_IV_SIZE_BYTES = 12;
  private static final int AES_GCM_TAG_SIZE_BYTES = 16;
  private static final AesGcmParameters DEK_PARAMETERS;

  static {
    try {
      DEK_PARAMETERS = AesGcmParameters.builder()
          .setKeySizeBytes(DEK_SIZE_BYTES)
          .setIvSizeBytes(AES_GCM_IV_SIZE_BYTES)
          .setTagSizeBytes(AES_GCM_TAG_SIZE_BYTES)
          .setVariant(AesGcmParameters.Variant.NO_PREFIX)
          .build();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Override
  public byte[] cipher(byte[] plaintext, KeysetHandle kekHandle, byte[] encryptAad) throws Exception {
    throw new UnsupportedOperationException(
        CIPHER_ALGORITHM + " requires wrapAad — use the 4-param cipher() overload");
  }

  @Override
  public byte[] cipher(byte[] plaintext, KeysetHandle kekHandle, byte[] encryptAad, byte[] wrapAad) throws Exception {
    LOG.log(TRACE, "cipher: plaintext={0}B wrapAad={1}B", plaintext.length, wrapAad.length);
    SecretBytes rawDek = SecretBytes.randomBytes(DEK_SIZE_BYTES);
    LOG.log(TRACE, "cipher: generated fresh DEK ({0}B)", DEK_SIZE_BYTES);
    Aead kekAead = kekHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);
    byte[] wrappedDek = kekAead.encrypt(rawDek.toByteArray(InsecureSecretKeyAccess.get()), wrapAad);
    LOG.log(TRACE, "cipher: DEK wrapped by KEK, wrappedDek={0}B", wrappedDek.length);
    Aead dekAead = dekAeadFromRawBytes(rawDek);
    byte[] dekCiphertext = dekAead.encrypt(plaintext, encryptAad);
    byte[] bundle = bundle(wrappedDek, dekCiphertext);
    LOG.log(TRACE, "cipher: bundle={0}B (wrappedDek={1}B + dekCiphertext={2}B)", bundle.length, wrappedDek.length, dekCiphertext.length);
    return bundle;
  }

  @Override
  public byte[] decipher(byte[] ciphertext, KeysetHandle kekHandle, byte[] encryptAad) throws Exception {
    throw new UnsupportedOperationException(
        CIPHER_ALGORITHM + " requires wrapAad — use the 4-param decipher() overload");
  }

  @Override
  public byte[] decipher(byte[] ciphertext, KeysetHandle kekHandle, byte[] encryptAad, byte[] wrapAad) throws Exception {
    LOG.log(TRACE, "decipher: ciphertext={0}B wrapAad={1}B", ciphertext.length, wrapAad.length);
    byte[][] parts = unbundle(ciphertext);
    byte[] wrappedDek = parts[0];
    byte[] dekCiphertext = parts[1];
    LOG.log(TRACE, "decipher: unbundled wrappedDek={0}B dekCiphertext={1}B", wrappedDek.length, dekCiphertext.length);
    Aead kekAead = kekHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);
    byte[] rawDekBytes = kekAead.decrypt(wrappedDek, wrapAad);
    LOG.log(TRACE, "decipher: DEK unwrapped ({0}B)", rawDekBytes.length);
    Aead dekAead = dekAeadFromRawBytes(SecretBytes.copyFrom(rawDekBytes, InsecureSecretKeyAccess.get()));
    byte[] plaintext = dekAead.decrypt(dekCiphertext, encryptAad);
    LOG.log(TRACE, "decipher: plaintext={0}B", plaintext.length);
    return plaintext;
  }

  /**
   * Creates a new {@link EncryptDekSession} — a fresh DEK wrapped with the KEK.
   * Used by the encrypt path when session-based DEK reuse is enabled.
   */
  public EncryptDekSession createSession(KeysetHandle kekHandle, byte[] wrapAad) throws Exception {
    return createSession(kekHandle, wrapAad, Clock.systemUTC());
  }

  public EncryptDekSession createSession(KeysetHandle kekHandle, byte[] wrapAad, Clock clock) throws Exception {
    LOG.log(DEBUG, "createSession: generating new DEK session (wrapAad={0}B)", wrapAad.length);
    SecretBytes rawDek = SecretBytes.randomBytes(DEK_SIZE_BYTES);
    Aead kekAead = kekHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);
    byte[] wrappedDek = kekAead.encrypt(rawDek.toByteArray(InsecureSecretKeyAccess.get()), wrapAad);
    LOG.log(DEBUG, "createSession: DEK session created, wrappedDek={0}B", wrappedDek.length);
    Aead dekAead = dekAeadFromRawBytes(rawDek);
    return new EncryptDekSession(wrappedDek, dekAead, clock);
  }

  /**
   * Encrypts plaintext using an already-created DEK {@link Aead} and its wrapped bytes.
   * Used by the encrypt path after a session cache hit to avoid generating a fresh DEK.
   */
  public byte[] cipherWithDek(byte[] plaintext, Aead dekAead, byte[] wrappedDek, byte[] encryptAad) throws Exception {
    LOG.log(TRACE, "cipherWithDek: reusing DEK session, plaintext={0}B wrappedDek={1}B", plaintext.length, wrappedDek.length);
    byte[] dekCiphertext = dekAead.encrypt(plaintext, encryptAad);
    byte[] bundle = bundle(wrappedDek, dekCiphertext);
    LOG.log(TRACE, "cipherWithDek: bundle={0}B", bundle.length);
    return bundle;
  }

  /**
   * Unwraps a previously wrapped DEK using the KEK, returning the DEK as an {@link Aead}.
   * Used by the decrypt path to enable DEK caching in {@code WrappedDekCache}.
   */
  public Aead unwrapDek(byte[] wrappedDek, KeysetHandle kekHandle, byte[] wrapAad) throws Exception {
    LOG.log(TRACE, "unwrapDek: wrappedDek={0}B wrapAad={1}B", wrappedDek.length, wrapAad.length);
    Aead kekAead = kekHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);
    byte[] rawDekBytes = kekAead.decrypt(wrappedDek, wrapAad);
    LOG.log(TRACE, "unwrapDek: DEK unwrapped ({0}B)", rawDekBytes.length);
    return dekAeadFromRawBytes(SecretBytes.copyFrom(rawDekBytes, InsecureSecretKeyAccess.get()));
  }

  private static Aead dekAeadFromRawBytes(SecretBytes rawDek) throws Exception {
    AesGcmKey dekKey = AesGcmKey.builder()
        .setParameters(DEK_PARAMETERS)
        .setKeyBytes(rawDek)
        .setIdRequirement(null)
        .build();
    KeysetHandle dekHandle = KeysetHandle.newBuilder()
        .addEntry(KeysetHandle.importKey(dekKey).withRandomId().makePrimary())
        .build();
    return dekHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);
  }

  /**
   * Decrypts ciphertext using an already-unwrapped DEK {@link Aead}.
   * Used by the decrypt path after a cache hit to avoid re-unwrapping the DEK.
   */
  public byte[] decipherWithDek(byte[] ciphertext, Aead dekAead, byte[] encryptAad) throws Exception {
    LOG.log(TRACE, "decipherWithDek: ciphertext={0}B (reusing cached DEK Aead)", ciphertext.length);
    byte[][] parts = unbundle(ciphertext);
    byte[] plaintext = dekAead.decrypt(parts[1], encryptAad);
    LOG.log(TRACE, "decipherWithDek: plaintext={0}B", plaintext.length);
    return plaintext;
  }

  public byte[] extractWrappedDek(byte[] bundle) {
    return unbundle(bundle)[0];
  }

  private static byte[] bundle(byte[] wrappedDek, byte[] dekCiphertext) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(ByteBuffer.allocate(4).putInt(wrappedDek.length).array());
    out.write(wrappedDek);
    out.write(dekCiphertext);
    return out.toByteArray();
  }

  private static byte[][] unbundle(byte[] bundle) {
    ByteBuffer buf = ByteBuffer.wrap(bundle);
    int wrappedDekLen = buf.getInt();
    byte[] wrappedDek = new byte[wrappedDekLen];
    buf.get(wrappedDek);
    byte[] dekCiphertext = new byte[buf.remaining()];
    buf.get(dekCiphertext);
    return new byte[][]{wrappedDek, dekCiphertext};
  }

}
