/*
 * Copyright (c) 2021. Hans-Peter Grahsl (grahslhp@gmail.com)
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

import java.util.Map;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import com.github.hpgrahsl.kryptonite.serdes.KryoSerdeProcessor;
import com.github.hpgrahsl.kryptonite.serdes.SerdeProcessor;
import static com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.*;
import static com.github.hpgrahsl.ksqldb.functions.kryptonite.CustomUdfConfig.*;

import io.confluent.ksql.function.udf.UdfDescription;

public abstract class AbstractCipherFieldUdf {

    private Kryptonite kryptonite;
    private SerdeProcessor serdeProcessor = new KryoSerdeProcessor();

    public Kryptonite getKryptonite() {
        return kryptonite;
    }

    public SerdeProcessor getSerdeProcessor() {
        return serdeProcessor;
    }

    public void configure(Map<String, ?> configMap, UdfDescription udfDescription) {
        var functionName = udfDescription.name();
        
        var cipherDataKeyIdentifierConfig = (String)configMap.get(getPrefixedConfigParam(functionName,CONFIG_PARAM_CIPHER_DATA_KEY_IDENTIFIER));
        var cipherDataKeyIdentifier = cipherDataKeyIdentifierConfig != null ? cipherDataKeyIdentifierConfig : CIPHER_DATA_KEY_IDENTIFIER_DEFAULT;
        
        var keySourceConfig = (String)configMap.get(getPrefixedConfigParam(functionName,CONFIG_PARAM_KEY_SOURCE));
        var keySource = keySourceConfig != null ? keySourceConfig : KEY_SOURCE_DEFAULT;
        
        var kmsTypeConfig = (String)configMap.get(getPrefixedConfigParam(functionName, CONFIG_PARAM_KMS_TYPE));
        var kmsType = kmsTypeConfig != null ? kmsTypeConfig : KMS_TYPE_DEFAULT;
        
        var kmsConfigConfig = (String)configMap.get(getPrefixedConfigParam(functionName, CONFIG_PARAM_KMS_CONFIG));
        var kmsConfig = kmsConfigConfig != null ? kmsConfigConfig : KMS_CONFIG_DEFAULT;
        
        var kekTypeConfig = (String)configMap.get(getPrefixedConfigParam(functionName, CONFIG_PARAM_KEK_TYPE));
        var kekType = kekTypeConfig != null ? kekTypeConfig : KEK_TYPE_DEFAULT;
        
        var kekConfigConfig = (String)configMap.get(getPrefixedConfigParam(functionName, CONFIG_PARAM_KEK_CONFIG));
        var kekConfig = kekConfigConfig != null ? kekConfigConfig : KEK_CONFIG_DEFAULT;
        
        var kekUriConfig = (String)configMap.get(getPrefixedConfigParam(functionName, CONFIG_PARAM_KEK_URI));
        var kekUri = kekUriConfig != null ? kekUriConfig : "";

        var normalizedStringsMap = Map.ofEntries(
                Map.entry(KryptoniteSettings.CIPHER_DATA_KEYS,(String)configMap.get(getPrefixedConfigParam(functionName, CONFIG_PARAM_CIPHER_DATA_KEYS))),
                Map.entry(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER,cipherDataKeyIdentifier),
                Map.entry(KryptoniteSettings.KEY_SOURCE,keySource),
                Map.entry(KryptoniteSettings.KMS_TYPE,kmsType),
                Map.entry(KryptoniteSettings.KMS_CONFIG,kmsConfig),
                Map.entry(KryptoniteSettings.KEK_TYPE,kekType),
                Map.entry(KryptoniteSettings.KEK_CONFIG,kekConfig),
                Map.entry(KryptoniteSettings.KEK_URI,kekUri)
        );
        kryptonite = Kryptonite.createFromConfig(normalizedStringsMap);
    }

}
