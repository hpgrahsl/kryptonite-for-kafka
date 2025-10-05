package com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe;

/**
 * Represents an FPE key with its parameters.
 */
public class FpeKey {
    private final byte[] keyValue;
    private final String alphabet;

    public FpeKey(byte[] keyValue, String alphabet) {
        this.keyValue = keyValue;
        this.alphabet = alphabet;
    }

    public byte[] getKeyValue() {
        return keyValue;
    }

    public String getAlphabet() {
        return alphabet;
    }
}
