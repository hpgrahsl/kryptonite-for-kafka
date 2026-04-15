package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.FieldEntryMetadata;

/**
 * Package-private utility methods shared across record processors.
 *
 * <p>All methods are stateless and take explicit parameters — no inheritance needed.
 */
final class FieldConfigUtils {

    private FieldConfigUtils() {}

    static boolean isFpe(FieldConfig fc, KryptoniteFilterConfig config) {
        String algorithm = fc.getAlgorithm().orElse(config.getCipherAlgorithm());
        return Kryptonite.CipherSpec.fromName(algorithm.toUpperCase()).isCipherFPE();
    }

    static FieldMetaData buildFieldMetaData(FieldConfig fc, KryptoniteFilterConfig config, String keyId) {
        String algorithm = fc.getAlgorithm().orElse(config.getCipherAlgorithm());
        String fpeTweak = fc.getFpeTweak().orElse(KryptoniteSettings.CIPHER_FPE_TWEAK_DEFAULT);
        String fpeAlphabet = determineAlphabet(fc);
        String encoding = fc.getEncoding().orElse(KryptoniteSettings.CIPHER_TEXT_ENCODING_DEFAULT);
        return FieldMetaData.builder()
                .algorithm(algorithm)
                .dataType(String.class.getName())
                .keyId(keyId)
                .fpeTweak(fpeTweak)
                .fpeAlphabet(fpeAlphabet)
                .encoding(encoding)
                .build();
    }

    static PayloadMetaData buildPayloadMetaData(FieldConfig fc, KryptoniteFilterConfig config, String keyId) {
        String algorithm = fc.getAlgorithm().orElse(config.getCipherAlgorithm());
        String algorithmId = Kryptonite.CIPHERSPEC_ID_LUT.get(Kryptonite.CipherSpec.fromName(algorithm));
        return new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, algorithmId, keyId);
    }

    static FieldConfig resolveEffective(FieldConfig fc, FieldEntryMetadata stored) {
        if (stored == null) return fc;
        return FieldConfig.builder()
                .name(fc.getName())
                .algorithm(stored.algorithm() != null ? stored.algorithm() : fc.getAlgorithm().orElse(null))
                .keyId(stored.keyId() != null ? stored.keyId() : fc.getKeyId().orElse(null))
                .fieldMode(stored.fieldMode() != null ? stored.fieldMode() : fc.getFieldMode().orElse(null))
                .fpeTweak(stored.fpeTweak() != null ? stored.fpeTweak() : fc.getFpeTweak().orElse(null))
                .fpeAlphabetType(stored.fpeAlphabetType() != null ? stored.fpeAlphabetType() : fc.getFpeAlphabetType().orElse(null))
                .fpeAlphabetCustom(stored.fpeAlphabetCustom() != null ? stored.fpeAlphabetCustom() : fc.getFpeAlphabetCustom().orElse(null))
                .build();
    }

    private static String determineAlphabet(FieldConfig fc) {
        AlphabetTypeFPE alphabetType = fc.getFpeAlphabetType()
                .orElse(AlphabetTypeFPE.valueOf(KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE_DEFAULT));
        return AlphabetTypeFPE.CUSTOM == alphabetType
                ? fc.getFpeAlphabetCustom().orElse(KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM_DEFAULT)
                : alphabetType.getAlphabet();
    }
}
