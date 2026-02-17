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

import javax.annotation.Nullable;

import org.apache.flink.table.functions.FunctionContext;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;

public class EncryptArrayUdf extends AbstractCipherFieldUdf {

    private transient String defaultCipherDataKeyIdentifier;

    @Override
    public void open(FunctionContext context) throws Exception {
        super.open(context);
        var cipherDataKeyIdentifier = getConfigurationSetting(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER);
        if (cipherDataKeyIdentifier == null
                || KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER_DEFAULT.equals(cipherDataKeyIdentifier)) {
            throw new KryptoniteException(
                    "missing required setting for " + KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER_DEFAULT
                            + " which is neither defined by environment variables nor by job parameters");
        }
        defaultCipherDataKeyIdentifier = cipherDataKeyIdentifier;
    }

    public @Nullable String[] eval(@Nullable final String[] data) {
        return process(data, null, null);
    }

    public @Nullable String[] eval(@Nullable final String[] data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    }

    public @Nullable String[] eval(@Nullable final Boolean[] data) {
        return process(data, null, null);
    }

    public @Nullable String[] eval(@Nullable final Boolean[] data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    }

    public @Nullable String[] eval(@Nullable final Integer[] data) {
        return process(data, null, null);
    }

    public @Nullable String[] eval(@Nullable final Integer[] data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    }

    public @Nullable String[] eval(@Nullable final Long[] data) {
        return process(data, null, null);
    }

    public @Nullable String[] eval(@Nullable final Long[] data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    }

    // about 2 sec delay before processing starts when adding these two
    public @Nullable String[] eval(@Nullable final Float[] data) {
        return process(data, null, null);
    }

    public @Nullable String[] eval(@Nullable final Float[] data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    }

    // delays for about 10 sec before processing starts when adding two more
    
    public @Nullable String[] eval(@Nullable final Double[] data) {
        return process(data, null, null);
    }

    public @Nullable String[] eval(@Nullable final Double[] data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    }

    // delays for about 4(!) mins before processing starts when adding two more
    // which must be a weird bug of some kind in the table api runtime of Flink?

    // public @Nullable String[] eval(@Nullable final Byte[] data) {
    // return process(data, null, null);
    // }

    // public @Nullable String[] eval(@Nullable final Byte[] data, String cipherDataKeyIdentifier,
    // String cipherAlgorithm) {
    // return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    // }

    private <T> String[] process(T[] array, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        if (array == null) {
            return null;
        }
        
        var dataEnc = new String[array.length];
        if (dataEnc.length == 0) {
            return dataEnc;
        }
        
        var fmd = createFieldMetaData(
                    cipherAlgorithm == null ? KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT : cipherAlgorithm,
                    dataEnc[0],
                    cipherDataKeyIdentifier == null ? defaultCipherDataKeyIdentifier : cipherDataKeyIdentifier);
        for (int s = 0; s < array.length; s++) {
            dataEnc[s] = encryptData(array[s], fmd);
        }
        return dataEnc;
    }

}
