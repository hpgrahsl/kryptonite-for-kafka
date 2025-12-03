/*
 * Copyright (c) 2024. Hans-Peter Grahsl (grahslhp@gmail.com)
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

    public String[] eval(final String[] data) {
        return process(data, null, null);
    }

    public String[] eval(final String[] data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    }

    public String[] eval(final Boolean[] data) {
        return process(data, null, null);
    }

    public String[] eval(final Boolean[] data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    }

    public String[] eval(final Integer[] data) {
        return process(data, null, null);
    }

    public String[] eval(final Integer[] data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    }

    public String[] eval(final Long[] data) {
        return process(data, null, null);
    }

    public String[] eval(final Long[] data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    }

    // about 2 sec delay before processing starts when adding these two
    public String[] eval(final Float[] data) {
        return process(data, null, null);
    }

    public String[] eval(final Float[] data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    }

    // delays for about 10 sec before processing starts when adding two more
    public String[] eval(final Double[] data) {
        return process(data, null, null);
    }

    public String[] eval(final Double[] data, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    }

    // delays for about 4(!) mins before processing starts when adding two more
    // public String[] eval(final Byte[] data) {
    // return process(data, null, null);
    // }

    // public String[] eval(final Byte[] data, String cipherDataKeyIdentifier,
    // String cipherAlgorithm) {
    // return process(data, cipherDataKeyIdentifier, cipherAlgorithm);
    // }

    private <T> String[] process(T[] array, String cipherDataKeyIdentifier, String cipherAlgorithm) {
        if (array == null) {
            return null;
        }
        var dataEnc = new String[array.length];
        for (int s = 0; s < array.length; s++) {
            var fmd = createFieldMetaData(
                    cipherAlgorithm == null ? KryptoniteSettings.CIPHER_ALGORITHM_DEFAULT : cipherAlgorithm,
                    dataEnc,
                    cipherDataKeyIdentifier == null ? defaultCipherDataKeyIdentifier : cipherDataKeyIdentifier);
            dataEnc[s] = encryptData(array[s], fmd);
        }
        return dataEnc;
    }

}
