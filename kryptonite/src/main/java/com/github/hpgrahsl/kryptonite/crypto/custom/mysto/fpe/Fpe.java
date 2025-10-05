package com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe;

import java.security.GeneralSecurityException;

/**
 * Format-Preserving Encryption (FPE) primitive interface.
 *
 * FPE encrypts data while preserving the format of the original plaintext.
 * This is useful whenever applications require the structure of the data
 * like credit card numbers, social security numbers, etc. to be preserved.
 */
public interface Fpe {

    /**
     * Encrypts the plaintext using format-preserving encryption with a custom tweak.
     *
     * @param plaintext the data to encrypt
     * @param tweak the tweak value to use for this encryption operation
     * @return the encrypted ciphertext in the same format as plaintext
     * @throws GeneralSecurityException if encryption fails
     */
    byte[] encrypt(byte[] plaintext, byte[] tweak) throws GeneralSecurityException;

    /**
     * Decrypts the ciphertext using format-preserving encryption with a custom tweak.
     *
     * @param ciphertext the encrypted data to decrypt
     * @param tweak the tweak value that was used for encryption
     * @return the decrypted plaintext
     * @throws GeneralSecurityException if decryption fails
     */
    byte[] decrypt(byte[] ciphertext, byte[] tweak) throws GeneralSecurityException;

}
