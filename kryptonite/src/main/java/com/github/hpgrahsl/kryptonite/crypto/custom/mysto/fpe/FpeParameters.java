package com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Parameters for FPE encryption operations.
 *
 * Separates algorithm configuration (alphabet) from key material,
 * allowing the same key to be used with different alphabets.
 */
public class FpeParameters {

    public enum Alphabet {
        DIGITS,
        ALPHABET_ALPHANUMERIC,
        ALPHABET_ALPHANUMERIC_EXTENDED,
        UPPERCASE,
        LOWERCASE,
        HEXADECIMAL
    }

    public static final String ALPHABET_DIGITS = "0123456789";
    public static final String ALPHABET_ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final String ALPHABET_ALPHANUMERIC_EXTENDED = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz _,.!?@%$&§\"'°^-+*/;:#(){}[]<>=";
    public static final String ALPHABET_UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String ALPHABET_LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    public static final String ALPHABET_HEXADECIMAL = "0123456789ABCDEF";

    public static final byte[] DEFAULT_TWEAK = new byte[]{0x0,0x0,0x0,0x0,0x0,0x0,0x0};

    private final String alphabet;
    private final byte[] tweak;

    private FpeParameters(String alphabet) {
        this(alphabet, DEFAULT_TWEAK);
    }

    private FpeParameters(String alphabet, String tweak) {
        this(alphabet, tweak != null ? tweak.getBytes(StandardCharsets.UTF_8) : null);
    }
    
    private FpeParameters(String alphabet, byte[] tweak) {
        if (alphabet == null || alphabet.length() < 2) {
            throw new IllegalArgumentException("error: alphabet must contain at least 2 characters");
        }
        this.alphabet = alphabet;
        if (tweak == null || tweak.length != 7) {
            throw new IllegalArgumentException("error: tweak must be exactly 7 bytes (56 bits)");
        }
        this.tweak = tweak;
    }

    public String getAlphabet() {
        return alphabet;
    }

    public byte[] getTweak() {
        return tweak;
    }
    
    public static FpeParameters create(String alphabet) {
        return new FpeParameters(alphabet, DEFAULT_TWEAK);
    }

    public static FpeParameters create(String alphabet, String tweak) {
        return new FpeParameters(alphabet, tweak);
    }

    public static FpeParameters create(String alphabet, byte[] tweak) {
        return new FpeParameters(alphabet, tweak);
    }

    /**
     * Creates FPE parameters for digits (0-9).
     */
    public static FpeParameters digits() {
        return new FpeParameters(ALPHABET_DIGITS, DEFAULT_TWEAK);
    }

    /**
     * Creates FPE parameters for alphanumeric characters.
     */
    public static FpeParameters alphanumeric() {
        return new FpeParameters(ALPHABET_ALPHANUMERIC, DEFAULT_TWEAK);
    }

    /**
     * Creates FPE parameters for alphanumeric characters.
     */
    public static FpeParameters alphanumericExtended() {
        return new FpeParameters(ALPHABET_ALPHANUMERIC_EXTENDED, DEFAULT_TWEAK);
    }

    /**
     * Creates FPE parameters for uppercase letters.
     */
    public static FpeParameters uppercase() {
        return new FpeParameters(ALPHABET_UPPERCASE, DEFAULT_TWEAK);
    }

    /**
     * Creates FPE parameters for lowercase letters.
     */
    public static FpeParameters lowercase() {
        return new FpeParameters(ALPHABET_LOWERCASE, DEFAULT_TWEAK);
    }

    /**
     * Creates FPE parameters for hexadecimal characters.
     */
    public static FpeParameters hexadecimal() {
        return new FpeParameters(ALPHABET_HEXADECIMAL, DEFAULT_TWEAK);
    }

    @Override
    public String toString() {
        return "FpeParameters [alphabet=" + alphabet + ", tweak=" + Arrays.toString(tweak) + "]";
    }

}
