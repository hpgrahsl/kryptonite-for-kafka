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

package com.github.hpgrahsl.flink.functions.kryptonite;

import org.apache.flink.table.functions.FunctionContext;

import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;

public class EncryptFpeUdf extends AbstractCipherFieldFpeUdf {

    private transient String defaultCipherDataKeyIdentifier;

    @Override
    public void open(FunctionContext context) throws Exception {
        super.open(context);
        var cipherDataKeyIdentifier = getConfigurationSetting(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER);
        if (cipherDataKeyIdentifier == null
                || KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER_DEFAULT
                        .equals(cipherDataKeyIdentifier)) {
            throw new KryptoniteException(
                    "missing required setting for "
                            + KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER_DEFAULT
                            + " which is neither defined by environment variables nor by job parameters");
        }
        defaultCipherDataKeyIdentifier = cipherDataKeyIdentifier;
    }

    public String eval(final String data) {
        var fmd = createFieldMetaData(null, defaultCipherDataKeyIdentifier, null, null, null);
        return encryptData(data, fmd);
    }

    public String eval(
            final String data,
            String cipherDataKeyIdentifier, String cipherAlgorithm) {
        if (cipherDataKeyIdentifier == null || cipherAlgorithm == null) {
            throw new IllegalArgumentException(
                    "error: cipher data key identifier and/or cipher algorithm must not be null");
        }
        var fmd = createFieldMetaData(cipherAlgorithm, cipherDataKeyIdentifier, null, null, null);
        return encryptData(data, fmd);
    }

    public String eval(
            final String data,
            String cipherDataKeyIdentifier, String cipherAlgorithm, String fpeTweak) {
        if (cipherDataKeyIdentifier == null || cipherAlgorithm == null || fpeTweak == null) {
            throw new IllegalArgumentException(
                    "error: cipher data key identifier and/or cipher algorithm and/or fpeTweak must not be null");
        }
        var fmd = createFieldMetaData(cipherAlgorithm, cipherDataKeyIdentifier, fpeTweak, null, null);                
        return encryptData(data, fmd);
    }

    public String eval(
            final String data,
            String cipherDataKeyIdentifier, String cipherAlgorithm, String fpeTweak,
            String fpeAlphabetType) {
        if (cipherDataKeyIdentifier == null || cipherAlgorithm == null || fpeTweak == null
                || fpeAlphabetType == null) {
            throw new IllegalArgumentException(
                    "error: cipher data key identifier and/or cipher algorithm and/or fpeTweak "
                            + "and/or fpeAlphabetType must not be null");
        }
        var fmd = createFieldMetaData(cipherAlgorithm, cipherDataKeyIdentifier, fpeTweak, fpeAlphabetType, null);                
        return encryptData(data, fmd);
    }

    public String eval(
            final String data,
            String cipherDataKeyIdentifier, String cipherAlgorithm, String fpeTweak,
            String fpeAlphabetType, String fpeAlphabetCustom) {
        if (cipherDataKeyIdentifier == null || cipherAlgorithm == null || fpeTweak == null
                || fpeAlphabetType == null || fpeAlphabetCustom == null) {
            throw new IllegalArgumentException(
                    "error: cipher data key identifier and/or cipher algorithm and/or fpeTweak "
                            + "and/or fpeAlphabetType and/or fpeAlphabetCustom must not be null");
        }
        if (!AlphabetTypeFPE.CUSTOM.name().equals(fpeAlphabetType)) {
            throw new IllegalArgumentException(
                    "error: fpeAlphabetCustom can only be set if fpeAlphabetType is set to "
                            + AlphabetTypeFPE.CUSTOM.name());
        }
        if (fpeAlphabetType.isEmpty()) {
            throw new IllegalArgumentException(
                    "error: fpeAlphabetCustom must not be empty when fpeAlphabetType is set to "
                            + AlphabetTypeFPE.CUSTOM.name());
        }
        var fmd = createFieldMetaData(cipherAlgorithm, cipherDataKeyIdentifier, fpeTweak, fpeAlphabetType, fpeAlphabetCustom);                
        return encryptData(data, fmd);
    }

}
