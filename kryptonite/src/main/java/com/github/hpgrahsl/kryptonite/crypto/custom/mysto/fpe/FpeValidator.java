package com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.github.hpgrahsl.kryptonite.KryptoniteException;

/**
 * Validator for FPE input to ensure all characters are within the defined
 * alphabet.
 *
 * This prevents silent data corruption that occurs when encrypting characters
 * outside the alphabet - such inputs will encrypt but decrypt to different
 * values.
 */
public class FpeValidator {

    private final Set<Character> alphabetChars;

    public FpeValidator(FpeParameters parameters) {
        this.alphabetChars = new HashSet<>();
        for (char c : parameters.getAlphabet().toCharArray()) {
            if (!alphabetChars.add(c)) {
                throw new IllegalArgumentException(
                        String.format("error: alphabet contains duplicate character '%c'. " +
                                "Each character must appear exactly once in the alphabet.", c));
            }
        }
    }

    public void validateCharactersInAlphabet(byte[] input) {
        validateCharactersInAlphabet(new String(input, StandardCharsets.UTF_8));
    }

    public void validateCharactersInAlphabet(String input) {
        if (input == null || input.isEmpty()) {
            return;
        }
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (!alphabetChars.contains(c)) {
                throw new KryptoniteException(
                        String.format("error: Invalid character '%c' at position %d. Character not in alphabet: %s",
                                c, i, alphabetChars));
            }
        }
    }

}
