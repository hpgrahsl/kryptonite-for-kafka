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

package com.github.hpgrahsl.ksqldb.functions.kryptonite;

import org.apache.kafka.common.Configurable;
import org.apache.kafka.common.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;

import io.confluent.ksql.function.udf.Udf;
import io.confluent.ksql.function.udf.UdfDescription;
import io.confluent.ksql.function.udf.UdfParameter;

@UdfDescription(
    name = "k4kdecryptfpe",
    description = "🔓 decrypt field data using Format Preserving Encryption (FPE)",
    version = "0.6.0",
    author = "H.P. Grahsl (@hpgrahsl)",
    category = "cryptography"
)
public class CipherFieldDecryptFpeUdf extends AbstractCipherFieldFpeUdf implements Configurable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CipherFieldDecryptFpeUdf.class);

    private String defaultCipherDataKeyIdentifier;

    @Override
    public void configure(java.util.Map<String, ?> map) {
        try {
            super.configure(map, this.getClass().getAnnotation(UdfDescription.class));
            var cipherDataKeyIdentifier = getConfigurationSetting(CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER);
            if (cipherDataKeyIdentifier == null || KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER_DEFAULT.equals(cipherDataKeyIdentifier)) {
                throw new ConfigException(
                        "missing required setting for " + CustomUdfConfig.CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER
                                + " for UDF function " + this.getClass().getAnnotation(UdfDescription.class).name());
            }
            defaultCipherDataKeyIdentifier = cipherDataKeyIdentifier;
            LOGGER.info("initialized UDF with default key identifier: {}", defaultCipherDataKeyIdentifier);
        } catch (Exception exc) {
            LOGGER.error("failed to initialize UDF", exc);
            throw exc;
        }
    }

    @Udf(description = "🔓 decrypt field data using FPE with configured defaults")
    public String decryptField(
            @UdfParameter(value = "data", description = "the encrypted data to decrypt") final String data
    ) {
        if (data == null) {
            return null;
        }
        var fmd = createFieldMetaData(null, defaultCipherDataKeyIdentifier, null, null, null);
        return decryptData(data, fmd);
    }

    @Udf(description = "🔓 decrypt field data using FPE with specified key identifier and cipher algorithm")
    public String decryptField(
            @UdfParameter(value = "data", description = "the encrypted data to decrypt")
            final String data,
            @UdfParameter(value = "keyIdentifier", description = "the key identifier")
            final String keyIdentifier,
            @UdfParameter(value = "cipherAlgorithm", description = "the cipher algorithm")
            final String cipherAlgorithm
    ) {
        if (data == null) {
            return null;
        }
        if (keyIdentifier == null || cipherAlgorithm == null) {
            throw new IllegalArgumentException(
                    "error: cipher data key identifier and/or cipher algorithm must not be null");
        }
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, null, null, null);
        return decryptData(data, fmd);
    }

    @Udf(description = "🔓 decrypt field data using FPE with specified key identifier, cipher algorithm, and tweak")
    public String decryptField(
            @UdfParameter(value = "data", description = "the encrypted data to decrypt")
            final String data,
            @UdfParameter(value = "keyIdentifier", description = "the key identifier")
            final String keyIdentifier,
            @UdfParameter(value = "cipherAlgorithm", description = "the cipher algorithm")
            final String cipherAlgorithm,
            @UdfParameter(value = "fpeTweak", description = "the FPE tweak value")
            final String fpeTweak
    ) {
        if (data == null) {
            return null;
        }
        if (keyIdentifier == null || cipherAlgorithm == null || fpeTweak == null) {
            throw new IllegalArgumentException(
                    "error: cipher data key identifier and/or cipher algorithm and/or fpeTweak must not be null");
        }
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, fpeTweak, null, null);
        return decryptData(data, fmd);
    }

    @Udf(description = "🔓 decrypt field data using FPE with specified key identifier, cipher algorithm, tweak, and alphabet type")
    public String decryptField(
            @UdfParameter(value = "data", description = "the encrypted data to decrypt")
            final String data,
            @UdfParameter(value = "keyIdentifier", description = "the key identifier")
            final String keyIdentifier,
            @UdfParameter(value = "cipherAlgorithm", description = "the cipher algorithm")
            final String cipherAlgorithm,
            @UdfParameter(value = "fpeTweak", description = "the FPE tweak value")
            final String fpeTweak,
            @UdfParameter(value = "fpeAlphabetType", description = "the FPE alphabet type")
            final String fpeAlphabetType
    ) {
        if (data == null) {
            return null;
        }
        if (keyIdentifier == null || cipherAlgorithm == null || fpeTweak == null || fpeAlphabetType == null) {
            throw new IllegalArgumentException(
                    "error: cipher data key identifier and/or cipher algorithm and/or fpeTweak and/or fpeAlphabetType must not be null");
        }
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, fpeTweak, fpeAlphabetType, null);
        return decryptData(data, fmd);
    }

    @Udf(description = "🔓 decrypt field data using FPE with all parameters including custom alphabet")
    public String decryptField(
            @UdfParameter(value = "data", description = "the encrypted data to decrypt")
            final String data,
            @UdfParameter(value = "keyIdentifier", description = "the key identifier")
            final String keyIdentifier,
            @UdfParameter(value = "cipherAlgorithm", description = "the cipher algorithm")
            final String cipherAlgorithm,
            @UdfParameter(value = "fpeTweak", description = "the FPE tweak value")
            final String fpeTweak,
            @UdfParameter(value = "fpeAlphabetType", description = "the FPE alphabet type")
            final String fpeAlphabetType,
            @UdfParameter(value = "fpeAlphabetCustom", description = "the custom FPE alphabet")
            final String fpeAlphabetCustom
    ) {
        if (data == null) {
            return null;
        }
        if (keyIdentifier == null || cipherAlgorithm == null || fpeTweak == null
                || fpeAlphabetType == null || fpeAlphabetCustom == null) {
            throw new IllegalArgumentException(
                    "error: cipher data key identifier and/or cipher algorithm and/or fpeTweak "
                            + "and/or fpeAlphabetType and/or fpeAlphabetCustom must not be null");
        }
        if (!AlphabetTypeFPE.CUSTOM.name().equalsIgnoreCase(fpeAlphabetType)) {
            throw new IllegalArgumentException(
                    "error: fpeAlphabetCustom can only be set if fpeAlphabetType is set to "
                            + AlphabetTypeFPE.CUSTOM.name());
        }
        if (fpeAlphabetCustom.isEmpty()) {
            throw new IllegalArgumentException(
                    "error: fpeAlphabetCustom must not be empty when fpeAlphabetType is set to "
                            + AlphabetTypeFPE.CUSTOM.name());
        }
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, fpeTweak, fpeAlphabetType, fpeAlphabetCustom);
        return decryptData(data, fmd);
    }

}
