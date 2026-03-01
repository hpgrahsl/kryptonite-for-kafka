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

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.kafka.common.Configurable;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;

import io.confluent.ksql.function.udf.Udf;
import io.confluent.ksql.function.udf.UdfDescription;
import io.confluent.ksql.function.udf.UdfParameter;

@UdfDescription(
    name = "k4k_decrypt_fpe",
    description = "🔓 decrypt field data using Format Preserving Encryption (FPE)",
    version = "0.4.0",
    author = "H.P. Grahsl (@hpgrahsl)",
    category = "cryptography"
)
public class CipherFieldDecryptFpeUdf extends AbstractCipherFieldFpeUdf implements Configurable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CipherFieldDecryptFpeUdf.class);

    private String defaultCipherDataKeyIdentifier;

    @Override
    public void configure(Map<String, ?> map) {
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
        } catch (KryptoniteException exc) {
            LOGGER.error("failed to initialize UDF", exc);
            throw exc;
        } catch (Exception exc) {
            LOGGER.error("failed to initialize UDF", exc);
            throw new KryptoniteException("failed to initialize UDF", exc);
        }
    }

    // ========== STRING FIELD DECRYPTION ==========

    @Udf(description = "🔓 decrypt string field using FPE with configured defaults")
    public String decryptField(
            @UdfParameter(value = "data", description = "the encrypted string to decrypt") final String data
    ) {
        if (data == null) {
            return null;
        }
        var fmd = createFieldMetaData(null, defaultCipherDataKeyIdentifier, null, null, null);
        return decryptData(data, fmd);
    }

    @Udf(description = "🔓 decrypt string field using FPE with specified key identifier and cipher algorithm")
    public String decryptField(
            @UdfParameter(value = "data", description = "the encrypted string to decrypt")
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

    @Udf(description = "🔓 decrypt string field using FPE with specified key identifier, cipher algorithm, and tweak")
    public String decryptField(
            @UdfParameter(value = "data", description = "the encrypted string to decrypt")
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

    @Udf(description = "🔓 decrypt string field using FPE with specified key identifier, cipher algorithm, tweak, and alphabet type")
    public String decryptField(
            @UdfParameter(value = "data", description = "the encrypted string to decrypt")
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

    @Udf(description = "🔓 decrypt string field using FPE with all parameters including custom alphabet")
    public String decryptField(
            @UdfParameter(value = "data", description = "the encrypted string to decrypt")
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

    // ========== LIST<STRING> DECRYPTION ==========

    @Udf(description = "🔓 decrypt List<String> element-wise using FPE with configured defaults")
    public List<String> decryptField(
            @UdfParameter(value = "data", description = "the list of encrypted strings to decrypt element-wise")
            final List<String> data
    ) {
        if (data == null) {
            return null;
        }
        var fmd = createFieldMetaData(null, defaultCipherDataKeyIdentifier, null, null, null);
        return decryptListElements(data, fmd);
    }

    @Udf(description = "🔓 decrypt List<String> element-wise using FPE with specified key identifier and cipher algorithm")
    public List<String> decryptField(
            @UdfParameter(value = "data", description = "the list of encrypted strings to decrypt element-wise")
            final List<String> data,
            @UdfParameter(value = "keyIdentifier", description = "the key identifier")
            final String keyIdentifier,
            @UdfParameter(value = "cipherAlgorithm", description = "the cipher algorithm")
            final String cipherAlgorithm
    ) {
        if (data == null) {
            return null;
        }
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, null, null, null);
        return decryptListElements(data, fmd);
    }

    @Udf(description = "🔓 decrypt List<String> element-wise using FPE with specified key identifier, cipher algorithm, and tweak")
    public List<String> decryptField(
            @UdfParameter(value = "data", description = "the list of encrypted strings to decrypt element-wise")
            final List<String> data,
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
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, fpeTweak, null, null);
        return decryptListElements(data, fmd);
    }

    @Udf(description = "🔓 decrypt List<String> element-wise using FPE with specified key identifier, cipher algorithm, tweak, and alphabet type")
    public List<String> decryptField(
            @UdfParameter(value = "data", description = "the list of encrypted strings to decrypt element-wise")
            final List<String> data,
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
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, fpeTweak, fpeAlphabetType, null);
        return decryptListElements(data, fmd);
    }

    @Udf(description = "🔓 decrypt List<String> element-wise using FPE with all parameters")
    public List<String> decryptField(
            @UdfParameter(value = "data", description = "the list of encrypted strings to decrypt element-wise")
            final List<String> data,
            @UdfParameter(value = "keyIdentifier", description = "the key identifier")
            final String keyIdentifier,
            @UdfParameter(value = "cipherAlgorithm", description = "the cipher algorithm")
            final String cipherAlgorithm,
            @UdfParameter(value = "fpeTweak", description = "the FPE tweak value")
            final String fpeTweak,
            @UdfParameter(value = "fpeAlphabetType", description = "the FPE alphabet type")
            final String fpeAlphabetType,
            @UdfParameter(value = "fpeAlphabetCustom", description = "the custom FPE alphabet (use empty string if not CUSTOM)")
            final String fpeAlphabetCustom
    ) {
        if (data == null) {
            return null;
        }
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, fpeTweak, fpeAlphabetType,
                fpeAlphabetCustom.isEmpty() ? null : fpeAlphabetCustom);
        return decryptListElements(data, fmd);
    }

    private List<String> decryptListElements(List<String> data, FieldMetaData fmd) {
        return data.stream()
                .map(e -> e == null ? null : decryptData(e, fmd))
                .collect(Collectors.toList());
    }

    // ========== MAP<?,STRING> DECRYPTION ==========

    @Udf(description = "🔓 decrypt Map<?,String> values element-wise using FPE with configured defaults")
    public Map<?,String> decryptField(
            @UdfParameter(value = "data", description = "the map with encrypted string values to decrypt element-wise")
            final Map<?,String> data
    ) {
        if (data == null) {
            return null;
        }
        var fmd = createFieldMetaData(null, defaultCipherDataKeyIdentifier, null, null, null);
        return decryptMapValues(data, fmd);
    }

    @Udf(description = "🔓 decrypt Map<?,String> values element-wise using FPE with specified key identifier and cipher algorithm")
    public Map<?,String> decryptField(
            @UdfParameter(value = "data", description = "the map with encrypted string values to decrypt element-wise")
            final Map<?,String> data,
            @UdfParameter(value = "keyIdentifier", description = "the key identifier")
            final String keyIdentifier,
            @UdfParameter(value = "cipherAlgorithm", description = "the cipher algorithm")
            final String cipherAlgorithm
    ) {
        if (data == null) {
            return null;
        }
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, null, null, null);
        return decryptMapValues(data, fmd);
    }

    @Udf(description = "🔓 decrypt Map<?,String> values element-wise using FPE with specified key identifier, cipher algorithm, and tweak")
    public Map<?,String> decryptField(
            @UdfParameter(value = "data", description = "the map with encrypted string values to decrypt element-wise")
            final Map<?,String> data,
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
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, fpeTweak, null, null);
        return decryptMapValues(data, fmd);
    }

    @Udf(description = "🔓 decrypt Map<?,String> values element-wise using FPE with specified key identifier, cipher algorithm, tweak, and alphabet type")
    public Map<?,String> decryptField(
            @UdfParameter(value = "data", description = "the map with encrypted string values to decrypt element-wise")
            final Map<?,String> data,
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
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, fpeTweak, fpeAlphabetType, null);
        return decryptMapValues(data, fmd);
    }

    @Udf(description = "🔓 decrypt Map<?,String> values element-wise using FPE with all parameters")
    public Map<?,String> decryptField(
            @UdfParameter(value = "data", description = "the map with encrypted string values to decrypt element-wise")
            final Map<?,String> data,
            @UdfParameter(value = "keyIdentifier", description = "the key identifier")
            final String keyIdentifier,
            @UdfParameter(value = "cipherAlgorithm", description = "the cipher algorithm")
            final String cipherAlgorithm,
            @UdfParameter(value = "fpeTweak", description = "the FPE tweak value")
            final String fpeTweak,
            @UdfParameter(value = "fpeAlphabetType", description = "the FPE alphabet type")
            final String fpeAlphabetType,
            @UdfParameter(value = "fpeAlphabetCustom", description = "the custom FPE alphabet (use empty string if not CUSTOM)")
            final String fpeAlphabetCustom
    ) {
        if (data == null) {
            return null;
        }
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, fpeTweak, fpeAlphabetType,
                fpeAlphabetCustom.isEmpty() ? null : fpeAlphabetCustom);
        return decryptMapValues(data, fmd);
    }

    private Map<?,String> decryptMapValues(Map<?,String> data, FieldMetaData fmd) {
        return data.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(
                        e.getKey(),
                        e.getValue() == null ? null : decryptData(e.getValue(), fmd)
                ))
                .collect(LinkedHashMap::new, (lhm,e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);
    }

    // ========== STRUCT DECRYPTION ==========

    @Udf(description = "🔓 decrypt Struct string fields element-wise using FPE with configured defaults")
    public Struct decryptField(
            @UdfParameter(value = "data", description = "the struct to decrypt string fields element-wise")
            final Struct data
    ) {
        if (data == null) {
            return null;
        }
        var fmd = createFieldMetaData(null, defaultCipherDataKeyIdentifier, null, null, null);
        return decryptStructFields(data, fmd);
    }

    @Udf(description = "🔓 decrypt Struct string fields element-wise using FPE with specified key identifier and cipher algorithm")
    public Struct decryptField(
            @UdfParameter(value = "data", description = "the struct to decrypt string fields element-wise")
            final Struct data,
            @UdfParameter(value = "keyIdentifier", description = "the key identifier")
            final String keyIdentifier,
            @UdfParameter(value = "cipherAlgorithm", description = "the cipher algorithm")
            final String cipherAlgorithm
    ) {
        if (data == null) {
            return null;
        }
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, null, null, null);
        return decryptStructFields(data, fmd);
    }

    @Udf(description = "🔓 decrypt Struct string fields element-wise using FPE with specified key identifier, cipher algorithm, and tweak")
    public Struct decryptField(
            @UdfParameter(value = "data", description = "the struct to decrypt string fields element-wise")
            final Struct data,
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
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, fpeTweak, null, null);
        return decryptStructFields(data, fmd);
    }

    @Udf(description = "🔓 decrypt Struct string fields element-wise using FPE with specified key identifier, cipher algorithm, tweak, and alphabet type")
    public Struct decryptField(
            @UdfParameter(value = "data", description = "the struct to decrypt string fields element-wise")
            final Struct data,
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
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, fpeTweak, fpeAlphabetType, null);
        return decryptStructFields(data, fmd);
    }

    @Udf(description = "🔓 decrypt Struct string fields element-wise using FPE with all parameters")
    public Struct decryptField(
            @UdfParameter(value = "data", description = "the struct to decrypt string fields element-wise")
            final Struct data,
            @UdfParameter(value = "keyIdentifier", description = "the key identifier")
            final String keyIdentifier,
            @UdfParameter(value = "cipherAlgorithm", description = "the cipher algorithm")
            final String cipherAlgorithm,
            @UdfParameter(value = "fpeTweak", description = "the FPE tweak value")
            final String fpeTweak,
            @UdfParameter(value = "fpeAlphabetType", description = "the FPE alphabet type")
            final String fpeAlphabetType,
            @UdfParameter(value = "fpeAlphabetCustom", description = "the custom FPE alphabet (use empty string if not CUSTOM)")
            final String fpeAlphabetCustom
    ) {
        if (data == null) {
            return null;
        }
        var fmd = createFieldMetaData(cipherAlgorithm, keyIdentifier, fpeTweak, fpeAlphabetType,
                fpeAlphabetCustom.isEmpty() ? null : fpeAlphabetCustom);
        return decryptStructFields(data, fmd);
    }

    private Struct decryptStructFields(Struct data, FieldMetaData fmd) {
        SchemaBuilder schemaBuilder = SchemaBuilder.struct();
        Struct original = data;

        // Build schema: decrypt string fields, keep others as-is
        original.schema().fields().forEach(f -> {
            if (f.schema().type() == Schema.Type.STRING) {
                schemaBuilder.field(f.name(),
                        f.schema().isOptional() ? Schema.OPTIONAL_STRING_SCHEMA : Schema.STRING_SCHEMA);
            } else {
                schemaBuilder.field(f.name(), f.schema());
            }
        });

        Schema targetSchema = schemaBuilder.optional().build();
        Struct result = new Struct(targetSchema);

        // Decrypt only string fields, copy others unchanged
        original.schema().fields().forEach(f -> {
            Object fieldValue = original.get(f.name());
            if (f.schema().type() == Schema.Type.STRING) {
                result.put(f.name(), fieldValue == null ? null : decryptData((String)fieldValue, fmd));
            } else {
                result.put(f.name(), fieldValue);
            }
        });

        return result;
    }

}
