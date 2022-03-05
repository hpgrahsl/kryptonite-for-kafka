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

package com.github.hpgrahsl.kryptonite;

import com.github.hpgrahsl.kryptonite.crypto.CryptoAlgorithm;
import com.github.hpgrahsl.kryptonite.crypto.jce.AesGcmNoPadding;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;
import com.github.hpgrahsl.kryptonite.keys.AbstractKeyVault;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.daead.DeterministicAeadConfig;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Objects;

public class Kryptonite {

  public static final class CipherSpec {

    public static final String TYPE_JCE = "JCE";
    public static final String TYPE_TINK = "TINK";

    private final String type;
    private final String name;
    private final CryptoAlgorithm algorithm;

    public CipherSpec(String type, String name, CryptoAlgorithm algorithm) {
      this.type = Objects.requireNonNull(type,"cipher spec type must not be null");
      this.name = Objects.requireNonNull(name, "cipher spec name must not be null");
      this.algorithm = Objects.requireNonNull(algorithm, "cipher spec algorithm must not be null");
    }

    public static CipherSpec fromName(String name) {
      Objects.requireNonNull(name,"name must not be null");
      switch(name) {
        case AesGcmNoPadding.CIPHER_ALGORITHM:
          return new CipherSpec(CipherSpec.TYPE_JCE, AesGcmNoPadding.CIPHER_ALGORITHM, new AesGcmNoPadding());
        case TinkAesGcm.CIPHER_ALGORITHM:
          return new CipherSpec(CipherSpec.TYPE_TINK, TinkAesGcm.CIPHER_ALGORITHM, new TinkAesGcm());
        case TinkAesGcmSiv.CIPHER_ALGORITHM:
          return new CipherSpec(CipherSpec.TYPE_TINK, TinkAesGcmSiv.CIPHER_ALGORITHM, new TinkAesGcmSiv());
        default:
          throw new IllegalArgumentException("invalid name "+name+" to create CipherSpec");
      }
    }

    public String getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    public CryptoAlgorithm getAlgorithm() {
      return algorithm;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof CipherSpec)) {
        return false;
      }
      CipherSpec that = (CipherSpec) o;
      return type.equals(that.type) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, name);
    }

    @Override
    public String toString() {
      return "CipherSpec{" +
          "type='" + type + '\'' +
          ", name='" + name + '\'' +
          '}';
    }

  }

  public static final String KRYPTONITE_VERSION = "k1";

  public static final Map<CipherSpec,String> CIPHERSPEC_ID_LUT = Map.of(
      CipherSpec.fromName(AesGcmNoPadding.CIPHER_ALGORITHM),"01",
      CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),"02",
      CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),"03"
  );

  public static final Map<String,CipherSpec> ID_CIPHERSPEC_LUT = Map.of(
      "01", CipherSpec.fromName(AesGcmNoPadding.CIPHER_ALGORITHM),
      "02", CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),
      "03", CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM)
  );

  private final AbstractKeyVault keyVault;

  public Kryptonite(AbstractKeyVault keyVault) {
    this.keyVault = keyVault;
    try {
      AeadConfig.register();
      DeterministicAeadConfig.register();
    } catch (GeneralSecurityException e) {
      throw new KryptoniteException(e);
    }
  }

  public EncryptedField cipherField(byte[] plaintext, PayloadMetaData metadata) {
    try {
      var cipherSpec = ID_CIPHERSPEC_LUT.get(metadata.getAlgorithmId());
      if (cipherSpec.getType().equals(CipherSpec.TYPE_JCE)) {
        return new EncryptedField(
            metadata,
            cipherSpec.getAlgorithm().cipher(plaintext, keyVault.readKey(metadata.getKeyId()), metadata.asBytes())
        );
      }
      return new EncryptedField(
          metadata,
          cipherSpec.getAlgorithm().cipher(plaintext, keyVault.readKeysetHandle(metadata.getKeyId()), metadata.asBytes())
      );
    } catch (Exception e) {
      throw new KryptoniteException(e.getMessage(),e);
    }
  }

  public byte[] decipherField(EncryptedField encryptedField) {
    try {
      var cipherSpec = ID_CIPHERSPEC_LUT.get(encryptedField.getMetaData().getAlgorithmId());
      if (cipherSpec.getType().equals(CipherSpec.TYPE_JCE)) {
        return cipherSpec.getAlgorithm().decipher(
            encryptedField.ciphertext(),
            keyVault.readKey(encryptedField.getMetaData().getKeyId()),
            encryptedField.associatedData()
        );
      }
      return cipherSpec.getAlgorithm().decipher(
          encryptedField.ciphertext(),
          keyVault.readKeysetHandle(encryptedField.getMetaData().getKeyId()),
          encryptedField.associatedData()
      );
    } catch (Exception e) {
      throw new KryptoniteException(e.getMessage(),e);
    }
  }

}
