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

import com.github.hpgrahsl.kryptonite.crypto.AeadEnvelopeAlgorithm;
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
import java.time.Clock;
import java.util.Arrays;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.TRACE;

/**
 * Envelope encryption using a cloud KMS key as the KEK.
 *
 * <p>The DEK is a raw AES-GCM key whose size is passed at call time (16 or 32 bytes).
 * Wrapping is performed via {@link EnvelopeKekEncryption#wrapDek} — a real KMS network call.
 * The KEK never leaves the KMS.
 *
 * <p>Wire format: {@code [16-byte fingerprint | dekCiphertext]}
 *
 * <p>The fingerprint is the first 16 bytes of the SHA-256 hash of the wrapped DEK, computed
 * via {@link com.github.hpgrahsl.kryptonite.keys.EdekStore#fingerprint(byte[])}. The actual
 * wrapped DEK is stored externally in an {@code EdekStore} (e.g. a KCache-backed compacted
 * Kafka topic) and looked up by fingerprint on the decrypt path. This keeps the field payload
 * compact — the wrapped DEK (~60 bytes for AES-128-GCM) is not repeated in every record.
 *
 * <p>Contrast with {@link TinkAesGcmEnvelopeKeyset}, which inlines the wrapped DEK
 * in the bundle as {@code [4-byte wrappedDekLen | wrappedDek | dekCiphertext]}.
 *
 * <p><strong>Note on {@link #cipherWithDek}</strong>: the {@code wrappedDek} parameter
 * in the {@link AeadEnvelopeAlgorithm} interface is repurposed here to carry the pre-computed
 * 16-byte fingerprint. The caller ({@code Kryptonite.cipherEnvelopeKms}) computes
 * {@code EdekStore.fingerprint(session.wrappedDek())} and publishes to the EdekStore before
 * invoking this method.
 *
 * <p>The DEK session cache ({@code EncryptDekSessionCache}) and EdekStore are
 * <strong>load-bearing</strong> for this mode — each KMS wrap/unwrap costs
 * tens-to-hundreds of ms.
 */
public class TinkAesGcmEnvelopeKms implements AeadEnvelopeAlgorithm<EnvelopeKekEncryption> {

  private static final System.Logger LOG = System.getLogger(TinkAesGcmEnvelopeKms.class.getName());

  public static final String CIPHER_ALGORITHM = "TINK/AES_GCM_ENVELOPE_KMS";

  private static final int DEK_SIZE_BYTES_DEFAULT = 16;
  private static final int AES_GCM_IV_SIZE_BYTES = 12;
  private static final int AES_GCM_TAG_SIZE_BYTES = 16;
  public static final int FINGERPRINT_SIZE_BYTES = 16;

  @Override
  public EncryptDekSession createSession(EnvelopeKekEncryption keyMaterial, byte[] wrapAad, int dekSizeBytes, Clock clock) throws Exception {
    LOG.log(DEBUG, "createSession: generating new DEK session via KMS wrap (wrapAad={0}B dekSizeBytes={1})", wrapAad.length, dekSizeBytes);
    SecretBytes rawDek = SecretBytes.randomBytes(dekSizeBytes);
    byte[] wrappedDek = keyMaterial.wrapDek(rawDek.toByteArray(InsecureSecretKeyAccess.get()), wrapAad);
    LOG.log(DEBUG, "createSession: DEK session created via KMS, wrappedDek={0}B", wrappedDek.length);
    Aead dekAead = dekAeadFromRawBytes(rawDek);
    return new EncryptDekSession(wrappedDek, dekAead, clock);
  }

  public EncryptDekSession createSession(EnvelopeKekEncryption keyMaterial, byte[] wrapAad) throws Exception {
    return createSession(keyMaterial, wrapAad, DEK_SIZE_BYTES_DEFAULT, Clock.systemUTC());
  }

  public EncryptDekSession createSession(EnvelopeKekEncryption keyMaterial, byte[] wrapAad, int dekSizeBytes) throws Exception {
    return createSession(keyMaterial, wrapAad, dekSizeBytes, Clock.systemUTC());
  }

  @Override
  public Aead unwrapDek(byte[] wrappedDek, EnvelopeKekEncryption keyMaterial, byte[] wrapAad) throws Exception {
    LOG.log(TRACE, "unwrapDek: wrappedDek={0}B wrapAad={1}B (KMS call)", wrappedDek.length, wrapAad.length);
    byte[] rawDekBytes = keyMaterial.unwrapDek(wrappedDek, wrapAad);
    LOG.log(TRACE, "unwrapDek: DEK unwrapped via KMS ({0}B)", rawDekBytes.length);
    return dekAeadFromRawBytes(SecretBytes.copyFrom(rawDekBytes, InsecureSecretKeyAccess.get()));
  }

  /**
   * Encrypts {@code plaintext} with the DEK and bundles the result with the fingerprint.
   *
   * <p>The {@code wrappedDek} parameter carries the 16-byte fingerprint (not the full wrapped
   * DEK bytes). The caller must have already published {@code fingerprint → wrappedDek} to
   * the {@code EdekStore} before invoking this method.
   *
   * <p>Wire format: {@code [16-byte fingerprint | dekCiphertext]}
   */
  @Override
  public byte[] cipherWithDek(byte[] plaintext, Aead dekAead, byte[] wrappedDek, byte[] encryptAad) throws Exception {
    LOG.log(TRACE, "cipherWithDek: reusing DEK session, plaintext={0}B fingerprint={1}B", plaintext.length, wrappedDek.length);
    byte[] dekCiphertext = dekAead.encrypt(plaintext, encryptAad);
    byte[] bundle = bundle(wrappedDek, dekCiphertext);
    LOG.log(TRACE, "cipherWithDek: bundle={0}B", bundle.length);
    return bundle;
  }

  @Override
  public byte[] decipherWithDek(byte[] ciphertext, Aead dekAead, byte[] encryptAad) throws Exception {
    LOG.log(TRACE, "decipherWithDek: ciphertext={0}B (reusing cached DEK Aead)", ciphertext.length);
    byte[][] parts = unbundle(ciphertext);
    byte[] plaintext = dekAead.decrypt(parts[1], encryptAad);
    LOG.log(TRACE, "decipherWithDek: plaintext={0}B", plaintext.length);
    return plaintext;
  }

  /**
   * Extracts the 16-byte fingerprint from the bundle.
   *
   * <p>For this implementation the returned bytes are a fingerprint, not the full wrapped DEK.
   * The caller must look up the actual wrapped DEK from the {@code EdekStore} using this value.
   */
  @Override
  public byte[] extractWrappedDek(byte[] bundle) {
    return unbundle(bundle)[0];
  }

  private static Aead dekAeadFromRawBytes(SecretBytes rawDek) throws Exception {
    AesGcmParameters params = AesGcmParameters.builder()
        .setKeySizeBytes(rawDek.size())
        .setIvSizeBytes(AES_GCM_IV_SIZE_BYTES)
        .setTagSizeBytes(AES_GCM_TAG_SIZE_BYTES)
        .setVariant(AesGcmParameters.Variant.NO_PREFIX)
        .build();
    AesGcmKey dekKey = AesGcmKey.builder()
        .setParameters(params)
        .setKeyBytes(rawDek)
        .setIdRequirement(null)
        .build();
    KeysetHandle dekHandle = KeysetHandle.newBuilder()
        .addEntry(KeysetHandle.importKey(dekKey).withRandomId().makePrimary())
        .build();
    return dekHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);
  }

  /**
   * Bundles fingerprint and DEK ciphertext.
   *
   * <p>Wire format: {@code [FINGERPRINT_SIZE_BYTES bytes | dekCiphertext]}
   * No length prefix is needed since the fingerprint size is fixed.
   */
  private static byte[] bundle(byte[] fingerprint, byte[] dekCiphertext) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(fingerprint);
    out.write(dekCiphertext);
    return out.toByteArray();
  }

  private static byte[][] unbundle(byte[] bundle) {
    byte[] fingerprint = Arrays.copyOf(bundle, FINGERPRINT_SIZE_BYTES);
    byte[] dekCiphertext = Arrays.copyOfRange(bundle, FINGERPRINT_SIZE_BYTES, bundle.length);
    return new byte[][]{fingerprint, dekCiphertext};
  }

}
