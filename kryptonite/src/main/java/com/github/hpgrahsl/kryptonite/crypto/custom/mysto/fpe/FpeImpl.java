package com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe;

import com.privacylogistics.FF3Cipher;

import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Implementation of the FPE primitive using FF3-1 algorithm based on Mysto's FPE library.
 */
public class FpeImpl implements Fpe {

    private static final String DEFAULT_TWEAK = "0000000000000000";

    private final FF3Cipher fpe;
    private final String alphabet;

    public FpeImpl(FpeKey key) throws GeneralSecurityException {
        this.alphabet = key.getAlphabet();

        try {
            // Ensure key is the right size (256 bits = 32 bytes for FF3)
            byte[] keyBytes = key.getKeyValue();

            // FF3 requires 256-bit keys, pad or truncate if necessary
            byte[] adjustedKey = new byte[32];
            if (keyBytes.length >= 32) {
                System.arraycopy(keyBytes, 0, adjustedKey, 0, 32);
            } else {
                System.arraycopy(keyBytes, 0, adjustedKey, 0, keyBytes.length);
                // Pad with zeros
                Arrays.fill(adjustedKey, keyBytes.length, 32, (byte) 0);
            }

            // Convert key bytes to hex string
            String keyHex = bytesToHex(adjustedKey);

            this.fpe = new FF3Cipher(keyHex, DEFAULT_TWEAK, alphabet);

        } catch (Exception e) {
            throw new GeneralSecurityException("Failed to initialize FPE", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    @Override
    public byte[] encrypt(byte[] plaintext, byte[] tweak) throws GeneralSecurityException {
        try {
            String plaintextStr = new String(plaintext);
            String tweakStr = getTweakString(tweak);
            String ciphertext = fpe.encrypt(plaintextStr, tweakStr);
            return ciphertext.getBytes();
        } catch (Exception e) {
            throw new GeneralSecurityException("FPE encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, byte[] tweak) throws GeneralSecurityException {
        try {
            String ciphertextStr = new String(ciphertext);
            String tweakStr = getTweakString(tweak);
            String plaintext = fpe.decrypt(ciphertextStr, tweakStr);
            return plaintext.getBytes();
        } catch (Exception e) {
            throw new GeneralSecurityException("FPE decryption failed", e);
        }
    }

    private String getTweakString(byte[] tweak) {
        if (tweak == null || tweak.length == 0) {
            return DEFAULT_TWEAK;
        }
        return bytesToHex(tweak);
    }
}
