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

import java.util.Map;
import java.util.Optional;

import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.functions.ScalarFunction;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.serdes.FieldHandler;

public abstract class AbstractCipherFieldUdf extends ScalarFunction {

    protected transient Kryptonite kryptonite;
    private transient Map<String, String> udfConfiguration;

    @Override
    public boolean isDeterministic() {
        return false;
    }

    @Override
    public void open(FunctionContext context) throws Exception {
        try {
            udfConfiguration = UdfConfiguration.load(context);
            kryptonite = Kryptonite.createFromConfig(udfConfiguration);
        } catch (Exception e) {
            throw new KryptoniteException(
                    "failed to initialize the function with the given configuration " + udfConfiguration, e);
        }
    }

    String encryptData(Object data, FieldMetaData fieldMetaData) {
        try {
            var metadata = PayloadMetaData.from(fieldMetaData);
            return FieldHandler.encryptField(data, metadata, kryptonite,
                    udfConfiguration.getOrDefault(KryptoniteSettings.SERDE_TYPE, KryptoniteSettings.SERDE_TYPE_DEFAULT));
        } catch (Exception exc) {
            throw new KryptoniteException("failed to encrypt data", exc);
        }
    }

    Object decryptData(String data) {
        if (data == null) {
            return null;
        }
        try {
            return FieldHandler.decryptField(data, kryptonite);
        } catch (Exception exc) {
            throw new KryptoniteException("failed to decrypt data", exc);
        }
    }

    protected String getConfigurationSetting(String key) {
        return udfConfiguration.get(key);
    }

    protected FieldMetaData createFieldMetaData(String cipherAlgorithm, Object data, String cipherDataKeyIdentifier) {
        return FieldMetaData.builder()
                .algorithm(cipherAlgorithm)
                .dataType(Optional.ofNullable(data).map(o -> o.getClass().getName()).orElse(""))
                .keyId(cipherDataKeyIdentifier)
                .build();
    }

}
