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
import java.util.Objects;
import java.util.Optional;

import com.github.hpgrahsl.funqy.http.kryptonite.KryptoniteConfiguration.FieldMode;
import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings.AlphabetTypeFPE;

public class FieldConfig {

  private String name;
  private String algorithm;
  private String keyId;
  private Map<String,Object> schema;
  private String fpeTweak;
  private AlphabetTypeFPE fpeAlphabetType;
  private String fpeAlphabetCustom;
  private String encoding;

  private FieldMode fieldMode;

  private FieldConfig(String name, String algorithm, String keyId,
                     Map<String, Object> schema, FieldMode fieldMode, String fpeTweak, AlphabetTypeFPE fpeAlphabetType, String fpeAlphabetCustom, String encoding) {
    this.name = Objects.requireNonNull(name,"field config's name must not be null");
    this.algorithm = algorithm;
    this.keyId = keyId;
    this.schema = schema;
    this.fieldMode = fieldMode;
    this.fpeTweak = fpeTweak;
    this.fpeAlphabetType = fpeAlphabetType;
    this.fpeAlphabetCustom = fpeAlphabetCustom;
    this.encoding = encoding;
  }

  public String getName() {
    return name;
  }

  public Optional<String> getAlgorithm() {
    return Optional.ofNullable(algorithm);
  }

  public Optional<String> getKeyId() {
    return Optional.ofNullable(keyId);
  }

  public Optional<Map<String, Object>> getSchema() {
    return Optional.ofNullable(schema);
  }

  public Optional<FieldMode> getFieldMode() {
    return Optional.ofNullable(fieldMode);
  }

  public Optional<String> getFpeTweak() {
    return Optional.ofNullable(fpeTweak);
  }

  public Optional<AlphabetTypeFPE> getFpeAlphabetType() {
    return Optional.ofNullable(fpeAlphabetType);
  }

  public Optional<String> getFpeAlphabetCustom() {
    return Optional.ofNullable(fpeAlphabetCustom);
  }

  public Optional<String> getEncoding() {
    return Optional.ofNullable(encoding);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    FieldConfig other = (FieldConfig) obj;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return "FieldConfig{" +
            "name='" + name + '\'' +
            ", algorithm='" + algorithm + '\'' +
            ", keyId='" + keyId + '\'' +
            ", schema=" + schema +
            ", fieldMode=" + fieldMode +
            ", fpeTweak='" + fpeTweak + '\'' +
            ", fpeAlphabetType='" + fpeAlphabetType + '\'' +
            ", fpeAlphabetCustom='" + fpeAlphabetCustom + '\'' +
            ", encoding='" + encoding + '\'' +
            '}';
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String name;
    private String algorithm;
    private String keyId;
    private Map<String, Object> schema;
    private String fpeTweak;
    private AlphabetTypeFPE fpeAlphabetType;
    private String fpeAlphabetCustom;
    private String encoding;
    private FieldMode fieldMode;

    private Builder() {
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder algorithm(String algorithm) {
      this.algorithm = algorithm;
      return this;
    }

    public Builder keyId(String keyId) {
      this.keyId = keyId;
      return this;
    }

    public Builder schema(Map<String, Object> schema) {
      this.schema = schema;
      return this;
    }

    public Builder fpeTweak(String fpeTweak) {
      this.fpeTweak = fpeTweak;
      return this;
    }

    public Builder fpeAlphabetType(AlphabetTypeFPE fpeAlphabetType) {
      this.fpeAlphabetType = fpeAlphabetType;
      return this;
    }

    public Builder fpeAlphabetCustomer(String fpeAlphabetCustom) {
      this.fpeAlphabetCustom = fpeAlphabetCustom;
      return this;
    }

    public Builder encoding(String encoding) {
      this.encoding = encoding;
      return this;
    }

    public Builder fieldMode(FieldMode fieldMode) {
      this.fieldMode = fieldMode;
      return this;
    }

    public FieldConfig build() {
      return new FieldConfig(name, algorithm, keyId, schema, fieldMode, fpeTweak, fpeAlphabetType, fpeAlphabetCustom, encoding);
    }
  }

}

