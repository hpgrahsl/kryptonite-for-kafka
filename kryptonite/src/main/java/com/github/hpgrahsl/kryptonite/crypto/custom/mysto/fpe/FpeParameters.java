/*
 * Copyright (c) 2025. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;

/**
 * Parameters for FPE encryption operations.
 *
 * Separates algorithm configuration (alphabet) from key material,
 * allowing the same key to be used with different alphabets and tweaks.
 */
public class FpeParameters {

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
        if (tweak == null || (tweak.length != 7 && tweak.length != 8)) {
            throw new IllegalArgumentException("error: tweak must be either 7 bytes (56 bits) or 8 bytes (64 bits) long");
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
        return new FpeParameters(AlphabetTypeFPE.DIGITS.getAlphabet(), DEFAULT_TWEAK);
    }

    /**
     * Creates FPE parameters for alphanumeric characters.
     */
    public static FpeParameters alphanumeric() {
        return new FpeParameters(AlphabetTypeFPE.ALPHANUMERIC.getAlphabet(), DEFAULT_TWEAK);
    }

    /**
     * Creates FPE parameters for alphanumeric characters.
     */
    public static FpeParameters alphanumericExtended() {
        return new FpeParameters(AlphabetTypeFPE.ALPHANUMERIC_EXTENDED.getAlphabet(), DEFAULT_TWEAK);
    }

    /**
     * Creates FPE parameters for uppercase letters.
     */
    public static FpeParameters uppercase() {
        return new FpeParameters(AlphabetTypeFPE.UPPERCASE.getAlphabet(), DEFAULT_TWEAK);
    }

    /**
     * Creates FPE parameters for lowercase letters.
     */
    public static FpeParameters lowercase() {
        return new FpeParameters(AlphabetTypeFPE.LOWERCASE.getAlphabet(), DEFAULT_TWEAK);
    }

    /**
     * Creates FPE parameters for hexadecimal characters.
     */
    public static FpeParameters hexadecimal() {
        return new FpeParameters(AlphabetTypeFPE.HEXADECIMAL.getAlphabet(), DEFAULT_TWEAK);
    }

    @Override
    public String toString() {
        return "FpeParameters [alphabet=" + alphabet + ", tweak=" + Arrays.toString(tweak) + "]";
    }

}
