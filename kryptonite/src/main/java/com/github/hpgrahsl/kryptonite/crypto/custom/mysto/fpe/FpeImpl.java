package com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe;

import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.privacylogistics.FF3Cipher;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Implementation of the FPE primitive using FF3-1 algorithm based on Mysto's FPE library.
 */
public class FpeImpl implements Fpe {

    private final String alphabet;
    private final byte[] tweak;
    private final FpeValidator validator;
    private final FF3Cipher ff3;

    public FpeImpl(FpeKey fpeKey, FpeParameters parameters) {
        try {
            Objects.requireNonNull(parameters, () -> "FpeParameters must not be null");
            Objects.requireNonNull(fpeKey, () -> "FpeKey must not be null");
            validator = new FpeValidator(parameters);
            alphabet = parameters.getAlphabet();
            tweak = parameters.getTweak();
            byte[] keyBytes = fpeKey.getKeyMaterial();
            if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
                throw new IllegalArgumentException(
                    String.format("Invalid key size: %d bytes. FF3 requires 128-bit (16 bytes), " +
                        "192-bit (24 bytes), or 256-bit (32 bytes) keys.", keyBytes.length));
            }
            this.ff3 = new FF3Cipher(keyBytes, tweak, alphabet);
        } catch (Exception e) {
            throw new KryptoniteException("failed to initialize FPE cipher FF3", e);
        }
    }

    @Override
    public byte[] encrypt(byte[] plaintext, byte[] tweak) throws Exception {
        try {
            String plaintextStr = new String(plaintext,StandardCharsets.UTF_8);
            validator.validateCharactersInAlphabet(plaintextStr);
            String ciphertext = ff3.encrypt(plaintextStr, tweak != null ? tweak : this.tweak);
            return ciphertext.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new KryptoniteException("FPE encryption using FF3 failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, byte[] tweak) throws Exception {
        try {
            String ciphertextStr = new String(ciphertext,StandardCharsets.UTF_8);
            validator.validateCharactersInAlphabet(ciphertext);
            String plaintext = ff3.decrypt(ciphertextStr, tweak != null ? tweak : this.tweak);
            return plaintext.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new KryptoniteException("FPE decryption using FF3 failed", e);
        }
    }

}
