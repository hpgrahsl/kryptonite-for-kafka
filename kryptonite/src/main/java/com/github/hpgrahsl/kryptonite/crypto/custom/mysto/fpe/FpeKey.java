package com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe;

/**
 * Represents an FPE key containing only the key material itself.
 *
 * Other settings such as the alphabet and the tweak to use are specified separately via FpeParameters.
 */
public class FpeKey {
    
    private final byte[] keyMaterial;

    public FpeKey(byte[] keyMaterial) {
        if (keyMaterial == null || (
                keyMaterial.length != 16
                && keyMaterial.length != 24
                && keyMaterial.length != 32)) {
            throw new IllegalArgumentException("key must be either 128 bits, 192 bits, or 256 bits!");
        }
        this.keyMaterial = keyMaterial;
    }

    public byte[] getKeyMaterial() {
        return keyMaterial;
    }
}
