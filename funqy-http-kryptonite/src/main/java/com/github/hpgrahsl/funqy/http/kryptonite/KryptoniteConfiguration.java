/*
 * Copyright (c) 2022. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.funqy.http.kryptonite;

import java.util.Map;
import java.util.Optional;

import jakarta.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.KekType;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.KeySource;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.KmsType;

@Singleton
public class KryptoniteConfiguration {
 
    public enum FieldMode {
        ELEMENT,
        OBJECT
    }
    
    @ConfigProperty(name="cipher_data_keys")
    public String cipherDataKeys;

    @ConfigProperty(name="cipher_data_key_identifier")
    public String cipherDataKeyIdentifier;

    @ConfigProperty(name="cipher_text_encoding", defaultValue = "BASE64")
    public String cipherTextEncoding;

    @ConfigProperty(name="cipher_fpe_tweak", defaultValue = "0000000")
    public String cipherFpeTweak;

    @ConfigProperty(name="cipher_fpe_alphabet_type")
    public AlphabetTypeFPE cipherFpeAlphabetType;

    @ConfigProperty(name="cipher_fpe_alphabet_custom")
    public Optional<String> cipherFpeAlphabetCustom;

    @ConfigProperty(name="key_source")
    public KeySource keySource;

    @ConfigProperty(name="kms_type")
    public KmsType kmsType;

    @ConfigProperty(name="kms_config", defaultValue = "{}")
    public String kmsConfig;

    @ConfigProperty(name="kek_type")
    public KekType kekType;

    @ConfigProperty(name="kek_config", defaultValue = "{}")
    public String kekConfig;

    @ConfigProperty(name="kek_uri", defaultValue = "gcp-kms://")
    public String kekUri;

    @ConfigProperty(name="dynamic_key_id_prefix", defaultValue = "__#")
    public String dynamicKeyIdPrefix;

    @ConfigProperty(name="path_delimiter", defaultValue = ".")
    public String pathDelimiter;

    @ConfigProperty(name="field_mode")
    public FieldMode fieldMode;

    @ConfigProperty(name="cipher_algorithm", defaultValue = "TINK/AES_GCM")
    public String cipherAlgorithm;

    public static KryptoniteConfiguration fromSettings(String cipherDataKeys, String cipherDataKeyIdentifier,
            String cipherTextEncoding, String cipherFpeTweak, AlphabetTypeFPE cipherFpeAlphabetType, String cipherFpeAlphabetCustom, 
            KeySource keySource, KmsType kmsType, String kmsConfig, KekType kekType, String kekConfig,
            String kekUri, String dynamicKeyIdPrefix, String pathDelimiter, FieldMode fieldMode, String cipherAlgorithm) {
        var kc = new KryptoniteConfiguration();
        kc.cipherDataKeys = cipherDataKeys;
        kc.cipherDataKeyIdentifier = cipherDataKeyIdentifier;
        kc.cipherTextEncoding = cipherTextEncoding;
        kc.cipherFpeTweak = cipherFpeTweak;
        kc.cipherFpeAlphabetType = cipherFpeAlphabetType;
        kc.cipherFpeAlphabetCustom = Optional.of(cipherFpeAlphabetCustom);
        kc.keySource = keySource;
        kc.kmsConfig = kmsConfig;
        kc.kekType = kekType;
        kc.kekConfig = kekConfig;
        kc.kekUri = kekUri;
        kc.dynamicKeyIdPrefix = dynamicKeyIdPrefix;
        kc.pathDelimiter = pathDelimiter;
        kc.fieldMode = fieldMode;
        kc.cipherAlgorithm = cipherAlgorithm;
        return kc;
    }

    public Map<String,String> adaptToNormalizedStringsMap() {
        return Map.ofEntries(
            Map.entry(KryptoniteSettings.CIPHER_DATA_KEYS,cipherDataKeys),
            Map.entry(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER,cipherDataKeyIdentifier),
            Map.entry(KryptoniteSettings.CIPHER_TEXT_ENCODING,cipherTextEncoding),
            Map.entry(KryptoniteSettings.CIPHER_FPE_TWEAK,cipherFpeTweak),
            Map.entry(KryptoniteSettings.CIPHER_FPE_ALPHABET_TYPE,cipherFpeAlphabetType.name()),
            Map.entry(KryptoniteSettings.CIPHER_FPE_ALPHABET_CUSTOM,cipherFpeAlphabetCustom.orElse("")),
            Map.entry(KryptoniteSettings.KEY_SOURCE,keySource.name()),
            Map.entry(KryptoniteSettings.KMS_TYPE,kmsType.name()),
            Map.entry(KryptoniteSettings.KMS_CONFIG,kmsConfig),
            Map.entry(KryptoniteSettings.KEK_TYPE,kekType.name()),
            Map.entry(KryptoniteSettings.KEK_CONFIG,kekConfig),
            Map.entry(KryptoniteSettings.KEK_URI,kekUri),
            Map.entry(KryptoniteSettings.PATH_DELIMITER,pathDelimiter),
            Map.entry(KryptoniteSettings.FIELD_MODE,fieldMode.name()),
            Map.entry(KryptoniteSettings.CIPHER_ALGORITHM,cipherAlgorithm)
        );
    }
}
