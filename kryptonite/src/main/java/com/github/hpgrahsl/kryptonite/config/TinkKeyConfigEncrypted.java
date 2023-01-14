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

package com.github.hpgrahsl.kryptonite.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;

public class TinkKeyConfigEncrypted {

  @JsonIgnore
  public static final int RAW_KEY_BYTES_OFFSET = 2;

  private String encryptedKeyset;
  
  private KeysetInfo keysetInfo;

  public TinkKeyConfigEncrypted() {
  }

  public String getEncryptedKeyset() {
    return encryptedKeyset;
  }

  public KeysetInfo getKeysetInfo() {
    return keysetInfo;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((encryptedKeyset == null) ? 0 : encryptedKeyset.hashCode());
    result = prime * result + ((keysetInfo == null) ? 0 : keysetInfo.hashCode());
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
    TinkKeyConfigEncrypted other = (TinkKeyConfigEncrypted) obj;
    if (encryptedKeyset == null) {
      if (other.encryptedKeyset != null)
        return false;
    } else if (!encryptedKeyset.equals(other.encryptedKeyset))
      return false;
    if (keysetInfo == null) {
      if (other.keysetInfo != null)
        return false;
    } else if (!keysetInfo.equals(other.keysetInfo))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return "TinkKeyConfigEncrypted [encryptedKeyset=" + encryptedKeyset + ", keysetInfo=" + keysetInfo + "]";
  }

  /* 
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
  */

  public static class KeysetInfo {

    private int primaryKeyId;
    private Set<KeyInfo> keyInfo;
    
    public KeysetInfo() {}

    public KeysetInfo(int primaryKeyId, Set<KeyInfo> keyInfo) {
      this.primaryKeyId = primaryKeyId;
      this.keyInfo = keyInfo;
    }

    public int getPrimaryKeyId() {
      return primaryKeyId;
    }

    public Set<KeyInfo> getKeyInfo() {
      return keyInfo;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + primaryKeyId;
      result = prime * result + ((keyInfo == null) ? 0 : keyInfo.hashCode());
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
      KeysetInfo other = (KeysetInfo) obj;
      if (primaryKeyId != other.primaryKeyId)
        return false;
      if (keyInfo == null) {
        if (other.keyInfo != null)
          return false;
      } else if (!keyInfo.equals(other.keyInfo))
        return false;
      return true;
    }

    @Override
    public String toString() {
      return "KeysetInfo [primaryKeyId=" + primaryKeyId + ", keyInfo=" + keyInfo + "]";
    }
    
  }

  public static class KeyInfo {

    public enum Status {
      ENABLED,
      DISABLED
    }

    private String typeUrl;
    private Status status;
    private int keyId;
    private String outputPrefixType;
    
    public KeyInfo() {}

    public KeyInfo(String typeUrl, Status status, int keyId, String outputPrefixType) {
      this.typeUrl = typeUrl;
      this.status = status;
      this.keyId = keyId;
      this.outputPrefixType = outputPrefixType;
    }

    public String getTypeUrl() {
      return typeUrl;
    }

    public Status getStatus() {
      return status;
    }

    public int getKeyId() {
      return keyId;
    }

    public String getOutputPrefixType() {
      return outputPrefixType;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((typeUrl == null) ? 0 : typeUrl.hashCode());
      result = prime * result + ((status == null) ? 0 : status.hashCode());
      result = prime * result + keyId;
      result = prime * result + ((outputPrefixType == null) ? 0 : outputPrefixType.hashCode());
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
      KeyInfo other = (KeyInfo) obj;
      if (typeUrl == null) {
        if (other.typeUrl != null)
          return false;
      } else if (!typeUrl.equals(other.typeUrl))
        return false;
      if (status != other.status)
        return false;
      if (keyId != other.keyId)
        return false;
      if (outputPrefixType == null) {
        if (other.outputPrefixType != null)
          return false;
      } else if (!outputPrefixType.equals(other.outputPrefixType))
        return false;
      return true;
    }

    @Override
    public String toString() {
      return "KeyInfo [typeUrl=" + typeUrl + ", status=" + status + ", keyId=" + keyId + ", outputPrefixType="
          + outputPrefixType + "]";
    }

  }

}
