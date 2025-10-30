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

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.functions.ScalarFunction;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import com.github.hpgrahsl.kryptonite.serdes.KryoInstance;
import com.github.hpgrahsl.kryptonite.serdes.KryoSerdeProcessor;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;

public abstract class AbstractCipherFieldUdf extends ScalarFunction {

    private transient Kryptonite kryptonite;
    private transient SerdeProcessor serdeProcessor;
    private transient Map<String,String> udfConfiguration;

    @Override
    public boolean isDeterministic() {
        return false;
    }

    @Override
    public void open(FunctionContext context) throws Exception {
        try {
            udfConfiguration = UdfConfiguration.load(context);
            kryptonite = Kryptonite.createFromConfig(udfConfiguration);
            serdeProcessor = new KryoSerdeProcessor();
        } catch (Exception e) {
            throw new KryptoniteException("failed to initialize the function with the given configuration "+udfConfiguration,e);
        }
    }

    String encryptData(Object data, FieldMetaData fieldMetaData) {
        try {
            var valueBytes = serdeProcessor.objectToBytes(data);
            var encryptedField = kryptonite.cipherField(valueBytes, PayloadMetaData.from(fieldMetaData));
            var output = new Output(new ByteArrayOutputStream());
            KryoInstance.get().writeObject(output, encryptedField);
            var encodedField = Base64.getEncoder().encodeToString(output.toBytes());
            return encodedField;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to encrypt data",exc);
        }
    }

    Object decryptData(String data) {
        try {
            var encryptedField = KryoInstance.get().readObject(new Input(Base64.getDecoder().decode(data)), EncryptedField.class);
            var plaintext = kryptonite.decipherField(encryptedField);
            var restored = serdeProcessor.bytesToObject(plaintext);
            return restored;
        } catch (Exception exc) {
            throw new KryptoniteException("failed to decrypt data",exc);
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
