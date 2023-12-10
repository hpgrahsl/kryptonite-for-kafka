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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig.KeyConfig.Status;
import com.github.hpgrahsl.kryptonite.keys.KeyNotFoundException;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

public class TinkKeyConfig {

  @JsonIgnore
  public static final int RAW_KEY_BYTES_OFFSET = 2;

  private long primaryKeyId;

  private Set<KeyConfig> key;

  public TinkKeyConfig() {
  }

  public TinkKeyConfig(long primaryKeyId,
      Set<KeyConfig> key) {
    this.primaryKeyId = primaryKeyId;
    this.key = key;
  }

  public long getPrimaryKeyId() {
    return primaryKeyId;
  }

  public Set<KeyConfig> getKey() {
    return key;
  }

  @JsonIgnore
  public byte[] getKeyBytesForEnabledPkId() {
    return key.stream().filter(
          kc -> primaryKeyId == kc.getKeyId() && kc.getStatus() == Status.ENABLED)
        .map(kc -> Base64.getDecoder().decode(kc.getKeyData().getValue()))
        .findFirst().orElseThrow(
            () -> new KeyNotFoundException("no ENABLED key config having pk id "+primaryKeyId
                + " could be found in the respective set of configured keys")
        );
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TinkKeyConfig)) {
      return false;
    }
    TinkKeyConfig that = (TinkKeyConfig) o;
    return Objects.equals(primaryKeyId, that.primaryKeyId) && Objects
        .equals(key, that.key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(primaryKeyId, key);
  }

  @Override
  public String toString() {
    return "TinkKeyConfig{" +
        "primaryKeyId=" + primaryKeyId +
        ", key=" + key +
        '}';
  }

  public static class KeyConfig {

    public enum Status {
      ENABLED,
      DISABLED
    }

    private KeyData keyData;
    private Status status;
    private long keyId;
    private String outputPrefixType;

    public KeyConfig() {
    }

    public KeyConfig(KeyData keyData, Status status, long keyId, String outputPrefixType) {
      this.keyData = keyData;
      this.status = status;
      this.keyId = keyId;
      this.outputPrefixType = outputPrefixType;
    }

    public KeyData getKeyData() {
      return keyData;
    }

    public Status getStatus() {
      return status;
    }

    public long getKeyId() {
      return keyId;
    }

    public String getOutputPrefixType() {
      return outputPrefixType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof KeyConfig)) {
        return false;
      }
      KeyConfig keyConfig = (KeyConfig) o;
      return Objects.equals(keyData, keyConfig.keyData) && Objects
          .equals(status, keyConfig.status) && Objects.equals(keyId, keyConfig.keyId)
          && Objects.equals(outputPrefixType, keyConfig.outputPrefixType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(keyData, status, keyId, outputPrefixType);
    }

    @Override
    public String toString() {
      return "KeyConfig{" +
          "keyData=" + keyData +
          ", status='" + status + '\'' +
          ", keyId=" + keyId +
          ", outputPrefixType='" + outputPrefixType + '\'' +
          '}';
    }
  }

  public static class KeyData {

    private String typeUrl;
    private String value;
    private String keyMaterialType;

    public KeyData() {
    }

    public KeyData(String typeUrl, String value, String keyMaterialType) {
      this.typeUrl = typeUrl;
      this.value = value;
      this.keyMaterialType = keyMaterialType;
    }

    public String getTypeUrl() {
      return typeUrl;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    public String getKeyMaterialType() {
      return keyMaterialType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof KeyData)) {
        return false;
      }
      KeyData keyData = (KeyData) o;
      return Objects.equals(typeUrl, keyData.typeUrl) && Objects
          .equals(value, keyData.value) && Objects
          .equals(keyMaterialType, keyData.keyMaterialType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(typeUrl, value, keyMaterialType);
    }

    @Override
    public String toString() {
      return "KeyData{" +
          "typeUrl='" + typeUrl + '\'' +
          ", value='" + value + '\'' +
          ", keyMaterialType='" + keyMaterialType + '\'' +
          '}';
    }
  }

}
