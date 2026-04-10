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

package com.github.hpgrahsl.kryptonite.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * One entry in the {@code envelope_kek_configs} JSON array.
 *
 * <p>Example:
 * <pre>{@code
 * {
 *   "identifier": "kek-gcp-1",
 *   "kek_type":   "GCP",
 *   "kek_uri":    "gcp-kms://projects/my-project/locations/global/keyRings/ring/cryptoKeys/myKek",
 *   "kek_config": "{\"type\":\"service_account\",\"project_id\":\"...\"}"
 * }
 * }</pre>
 *
 * <p>{@code kek_config} is an opaque JSON string whose format is provider-specific —
 * each {@code EnvelopeKekEncryptionProvider} implementation parses its own format.
 */
public class EnvelopeKekConfig {

    private String identifier;

    @JsonProperty("kek_type")
    private String kekType;

    @JsonProperty("kek_uri")
    private String kekUri;

    @JsonProperty("kek_config")
    private String kekConfig;

    public EnvelopeKekConfig() {
    }

    public EnvelopeKekConfig(String identifier, String kekType, String kekUri, String kekConfig) {
        this.identifier = identifier;
        this.kekType = kekType;
        this.kekUri = kekUri;
        this.kekConfig = kekConfig;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getKekType() {
        return kekType;
    }

    public String getKekUri() {
        return kekUri;
    }

    public String getKekConfig() {
        return kekConfig;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EnvelopeKekConfig)) return false;
        EnvelopeKekConfig that = (EnvelopeKekConfig) o;
        return Objects.equals(identifier, that.identifier)
            && Objects.equals(kekType, that.kekType)
            && Objects.equals(kekUri, that.kekUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, kekType, kekUri);
    }

    @Override
    public String toString() {
        return "EnvelopeKekConfig{identifier='" + identifier + "', kekType='" + kekType + "'}";
    }

}
