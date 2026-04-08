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
import com.github.hpgrahsl.kryptonite.kms.EnvelopeKekEncryption;
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
 * Envelope encryption using a cloud KMS key as the KEK.
 *
 * <p>The DEK is a raw 16-byte AES-128-GCM key. Wrapping is performed via
 * {@link EnvelopeKekEncryption#wrapDek} — a real KMS network call. The key never
 * leaves the KMS.
 *
 * <p>Wire format is identical to {@link TinkAesGcmEnvelopeKeyset}:
 * {@code [4-byte wrappedDekLen | wrappedDek | dekCiphertext]}
 *
 * <p>The DEK session cache ({@code EncryptDekSessionCache}) and wrapped DEK cache
 * ({@code WrappedDekCache}) are <strong>load-bearing</strong> for this mode — each
 * KMS wrap/unwrap costs ~tens-hundreds of ms. Without caching, every field encryption/decryption
 * triggers a KMS round-trip.
 *
 * <p>Encrypt path (session-based, called by {@code Kryptonite.cipherFieldRaw}):
 * <ol>
 *   <li>Session hit: {@code cipherWithDek(plaintext, session.dekAead(), session.wrappedDek(), encryptAad)}</li>
 *   <li>Session miss: {@link #createSession} → one KMS wrap call → store in cache → encrypt</li>
 * </ol>
 *
 * <p>Decrypt path (per record, called by {@code Kryptonite.decipherFieldRaw}):
 * <ol>
 *   <li>Cache hit: {@code decipherWithDek(ciphertext, cachedDekAead, encryptAad)} — no KMS call</li>
 *   <li>Cache miss: {@link #unwrapDek} → one KMS unwrap call → cache → decrypt</li>
 * </ol>
 */
public class TinkAesGcmEnvelopeKms implements AeadAlgorithm {

  private static final System.Logger LOG = System.getLogger(TinkAesGcmEnvelopeKms.class.getName());

  public static final String CIPHER_ALGORITHM = "TINK/AES_GCM_ENVELOPE_KMS";

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
        CIPHER_ALGORITHM + " uses a cloud KMS KEK — use createSession()/cipherWithDek() via Kryptonite");
  }

  @Override
  public byte[] decipher(byte[] ciphertext, KeysetHandle kekHandle, byte[] encryptAad) throws Exception {
    throw new UnsupportedOperationException(
        CIPHER_ALGORITHM + " uses a cloud KMS KEK — use unwrapDek()/decipherWithDek() via Kryptonite");
  }

  /**
   * Creates a new {@link EncryptDekSession} by generating a fresh DEK and wrapping it via KMS.
   * One KMS network call is made. Used by the encrypt path on session cache miss.
   */
  public EncryptDekSession createSession(EnvelopeKekEncryption kekEncryption, byte[] wrapAad) throws Exception {
    return createSession(kekEncryption, wrapAad, Clock.systemUTC());
  }

  public EncryptDekSession createSession(EnvelopeKekEncryption kekEncryption, byte[] wrapAad, Clock clock) throws Exception {
    LOG.log(DEBUG, "createSession: generating new DEK session via KMS wrap (wrapAad={0}B)", wrapAad.length);
    SecretBytes rawDek = SecretBytes.randomBytes(DEK_SIZE_BYTES);
    byte[] wrappedDek = kekEncryption.wrapDek(rawDek.toByteArray(InsecureSecretKeyAccess.get()), wrapAad);
    LOG.log(DEBUG, "createSession: DEK session created via KMS, wrappedDek={0}B", wrappedDek.length);
    Aead dekAead = dekAeadFromRawBytes(rawDek);
    return new EncryptDekSession(wrappedDek, dekAead, clock);
  }

  /**
   * Encrypts plaintext using an already-created DEK {@link Aead} and its wrapped bytes.
   * Used after a session cache hit — no KMS call.
   */
  public byte[] cipherWithDek(byte[] plaintext, Aead dekAead, byte[] wrappedDek, byte[] encryptAad) throws Exception {
    LOG.log(TRACE, "cipherWithDek: reusing DEK session, plaintext={0}B wrappedDek={1}B", plaintext.length, wrappedDek.length);
    byte[] dekCiphertext = dekAead.encrypt(plaintext, encryptAad);
    byte[] bundle = bundle(wrappedDek, dekCiphertext);
    LOG.log(TRACE, "cipherWithDek: bundle={0}B", bundle.length);
    return bundle;
  }

  /**
   * Unwraps a previously wrapped DEK via KMS, returning the DEK as a Tink {@link Aead}.
   * One KMS network call is made. Used by the decrypt path on wrapped DEK cache miss.
   */
  public Aead unwrapDek(byte[] wrappedDek, EnvelopeKekEncryption kekEncryption, byte[] wrapAad) throws Exception {
    LOG.log(TRACE, "unwrapDek: wrappedDek={0}B wrapAad={1}B (KMS call)", wrappedDek.length, wrapAad.length);
    byte[] rawDekBytes = kekEncryption.unwrapDek(wrappedDek, wrapAad);
    LOG.log(TRACE, "unwrapDek: DEK unwrapped via KMS ({0}B)", rawDekBytes.length);
    return dekAeadFromRawBytes(SecretBytes.copyFrom(rawDekBytes, InsecureSecretKeyAccess.get()));
  }

  /**
   * Decrypts ciphertext using an already-unwrapped DEK {@link Aead}.
   * Used after a wrapped DEK cache hit — no KMS call.
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
