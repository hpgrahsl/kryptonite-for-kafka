/*
 * Copyright (c) 2023. Hans-Peter Grahsl (grahslhp@gmail.com)
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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;

import com.github.hpgrahsl.kryptonite.CipherMode;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;
import com.github.hpgrahsl.kryptonite.converters.FunqyFieldConverter;
import com.github.hpgrahsl.kryptonite.serdes.FieldHandler;

@ApplicationScoped
public class CipherFieldService {

    KryptoniteConfiguration config;
    Kryptonite kryptonite;
    FunqyFieldConverter fieldConverter = new FunqyFieldConverter();
    
    public CipherFieldService(KryptoniteConfiguration config) {
        this.config = config;
        this.kryptonite = Kryptonite.createFromConfig(config.adaptToNormalizedStringsMap());
    }

    public KryptoniteConfiguration getKryptoniteConfiguration() {
        return config;
    }
    
    public String encryptData(Object data) {
        try {
            var fieldMetaData = createFieldMetaData(data);
            if (CipherSpec.fromName(config.cipherAlgorithm.toUpperCase()).isCipherFPE()) {
                if (!(data instanceof String)) {
                    throw new KryptoniteException("FPE encryption only supports data of type String");
                }
                return encryptFPE((String) data, fieldMetaData); 
            } 
            return encryptNonFPE(data, fieldMetaData);
        } catch (Exception exc) {
            throw new KryptoniteException(exc.getMessage(), exc);
        }
    }

    private String encryptNonFPE(Object data, FieldMetaData fieldMetaData) {
        var metadata = PayloadMetaData.from(fieldMetaData);
        return FieldHandler.encryptField(fieldConverter.fromFunqy(data, null, config.serdeType.name()), metadata, kryptonite, config.serdeType.name());
    }

    private String encryptFPE(String data, FieldMetaData fieldMetaData) {
        // NOTE: null is by definition not encryptable with FPE ciphers
        if (data == null) {
            return null;
        }
        var plaintext = data.getBytes(StandardCharsets.UTF_8);
        var ciphertext = new String(kryptonite.cipherFieldFPE(plaintext, fieldMetaData), StandardCharsets.UTF_8);
        return ciphertext;
    }

    public Object decryptData(String data) {
        try {
            var fieldMetaData = createFieldMetaData(data);
            if (CipherSpec.fromName(config.cipherAlgorithm.toUpperCase()).isCipherFPE()) {
                return decryptFPE(data, fieldMetaData);  
            } 
            return decryptNonFPE(data, fieldMetaData);
        } catch (Exception exc) {
            throw new KryptoniteException(exc.getMessage(), exc);
        }
    }

    private Object decryptNonFPE(String data, FieldMetaData fieldMetaData) {
        if (data == null) {
            return null;
        }
        return fieldConverter.toFunqy(FieldHandler.decryptField(data, kryptonite));
    }

    private Object decryptFPE(String data, FieldMetaData fieldMetaData) {
        if (data == null) {
            return null;
        }
        var ciphertext = data.getBytes(StandardCharsets.UTF_8);
        var plaintext = new String(kryptonite.decipherFieldFPE(ciphertext, fieldMetaData), StandardCharsets.UTF_8);
        return plaintext;
    }

    public Object processDataWithFieldConfig(Object data, Map<String, FieldConfig> fieldConfig, CipherMode cipherMode) {
        return new RecordHandler(config, kryptonite, cipherMode, fieldConfig, fieldConverter)
                    .matchFields(data,"");
    }

    private FieldMetaData createFieldMetaData(Object value) {
        return FieldMetaData.builder()
              .algorithm(config.cipherAlgorithm)
              .dataType(Optional.ofNullable(value).map(o -> o.getClass().getName()).orElse(""))
              .keyId(config.cipherDataKeyIdentifier)
              .fpeTweak(config.cipherFpeTweak)
              .fpeAlphabet(
                config.cipherFpeAlphabetType == AlphabetTypeFPE.CUSTOM
                    ? config.cipherFpeAlphabetCustom.orElse("")
                    : config.cipherFpeAlphabetType.getAlphabet()
              )
              .encoding(config.cipherTextEncoding)
              .build();
    }

}
