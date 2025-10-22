package com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe;

/**
 * Format-Preserving Encryption (FPE) primitive interface.
 *
 * FPE encrypts data while preserving the format of the original plaintext.
 * This is useful whenever applications require the structure of the data
 * like credit card numbers, social security numbers, etc. to be preserved.
 */
public interface Fpe {

    /**
     * Encrypts the plaintext using format-preserving encryption.
     *
     * @param plaintext the data to encrypt
     * @param tweak the tweak value to use for this encryption operation
     * @return the resulting ciphertext
     */
    byte[] encrypt(byte[] plaintext, byte[] tweak) throws Exception;

    /**
     * Decrypts the ciphertext using format-preserving encryption.
     *
     * @param ciphertext the encrypted data to decrypt
     * @param tweak the tweak value to use for this decryption operation (MUST match the tweak used during encryption!)
     * @return the resulting plaintext
     */
    byte[] decrypt(byte[] ciphertext, byte[] tweak) throws Exception;

}
