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
import java.util.Optional;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;

import io.confluent.ksql.function.udf.UdfDescription;

public abstract class AbstractCipherFieldUdf {

    private Kryptonite kryptonite;
    private String serdeType;

    public Kryptonite getKryptonite() {
        return kryptonite;
    }

    public String getSerdeType() {
        return serdeType;
    }

    public void configure(Map<String, ?> configMap, UdfDescription udfDescription) {
        kryptonite = CustomUdfConfig.KryptoniteUtil.createKryptoniteFromConfig(configMap, udfDescription.name());
        serdeType = Optional.ofNullable(
                (String) configMap.get(CustomUdfConfig.getPrefixedConfigParam(udfDescription.name(), CustomUdfConfig.CONFIG_PARAM_SERDE_TYPE)))
                .orElse(KryptoniteSettings.SERDE_TYPE_DEFAULT);
    }

}
