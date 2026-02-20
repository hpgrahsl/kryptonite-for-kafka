/*
 * Copyright (c) 2026. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.kryptonite.kms.azure.kek;

import com.azure.security.keyvault.keys.cryptography.CryptographyClient;
import com.azure.security.keyvault.keys.cryptography.models.KeyWrapAlgorithm;
import com.google.crypto.tink.Aead;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Implements Tink's {@link Aead} interface backed by Azure Key Vault Keys using envelope
 * encryption. Azure Key Vault standard tier only supports RSA keys for encrypt/decrypt operations,
 * which have a plaintext size limit well below that of a typical Tink keyset. This class therefore
 * uses two-level encryption:
 *
 * <ol>
 *   <li>A fresh ephemeral 256-bit AES DEK and 12-byte IV are generated per {@code encrypt} call.
 *   <li>The plaintext is encrypted locally with AES-256-GCM using the DEK; {@code associatedData}
 *       is passed as GCM AAD, providing full cryptographic binding.
 *   <li>The DEK is wrapped via {@code CryptographyClient.wrapKey(RSA_OAEP_256, dek)}, which sends
 *       only the 32-byte DEK to Azure Key Vault — well within RSA size limits.
 * </ol>
 *
 * <p>Ciphertext wire format (all lengths in bytes):
 * <pre>
 *   [ 4 bytes: wrappedKeyLen (big-endian int) ]
 *   [ wrappedKeyLen bytes: RSA-OAEP-256 wrapped DEK ]
 *   [ 12 bytes: GCM IV ]
 *   [ remaining bytes: AES-256-GCM ciphertext + 16-byte authentication tag ]
 * </pre>
 */
public class AzureKmsAead implements Aead {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int DEK_LENGTH_BYTES = 32;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final CryptographyClient cryptographyClient;
    private final SecureRandom secureRandom;

    public AzureKmsAead(CryptographyClient cryptographyClient) {
        this.cryptographyClient = cryptographyClient;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public byte[] encrypt(byte[] plaintext, byte[] associatedData) throws GeneralSecurityException {
        byte[] dek = new byte[DEK_LENGTH_BYTES];
        secureRandom.nextBytes(dek);
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dek, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }
        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] wrappedKey = cryptographyClient.wrapKey(KeyWrapAlgorithm.RSA_OAEP_256, dek).getEncryptedKey();

        ByteBuffer result = ByteBuffer.allocate(Integer.BYTES + wrappedKey.length + GCM_IV_LENGTH_BYTES + ciphertext.length);
        result.putInt(wrappedKey.length);
        result.put(wrappedKey);
        result.put(iv);
        result.put(ciphertext);
        return result.array();
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, byte[] associatedData) throws GeneralSecurityException {
        ByteBuffer buffer = ByteBuffer.wrap(ciphertext);

        if (buffer.remaining() < Integer.BYTES) {
            throw new GeneralSecurityException("ciphertext too short");
        }
        int wrappedKeyLen = buffer.getInt();

        if (buffer.remaining() < wrappedKeyLen + GCM_IV_LENGTH_BYTES) {
            throw new GeneralSecurityException("ciphertext too short");
        }
        byte[] wrappedKey = new byte[wrappedKeyLen];
        buffer.get(wrappedKey);

        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        buffer.get(iv);

        byte[] encryptedData = new byte[buffer.remaining()];
        buffer.get(encryptedData);

        byte[] dek = cryptographyClient.unwrapKey(KeyWrapAlgorithm.RSA_OAEP_256, wrappedKey).getKey();

        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dek, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }
        return cipher.doFinal(encryptedData);
    }

}
