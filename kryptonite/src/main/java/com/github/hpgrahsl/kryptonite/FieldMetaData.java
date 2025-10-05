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

package com.github.hpgrahsl.kryptonite;

import java.util.Objects;

public class FieldMetaData {

  private String algorithm;

  private String dataType;

  private String keyId;

  private String tweak;

  private String encoding;

  public FieldMetaData() {
  }

  public FieldMetaData(String algorithm, String dataType, String keyId, String tweak, String encoding) {
    this.algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
    this.dataType = Objects.requireNonNull(dataType, "dataType must not be null");
    this.keyId = Objects.requireNonNull(keyId, "keyId must not be null");
    this.tweak = Objects.requireNonNull(tweak, "tweak must not be null");
    this.encoding = Objects.requireNonNull(encoding, "encoding must not be null");
  }

  public static Builder builder() {
    return new Builder();
  }

  private FieldMetaData(Builder builder) {
    this.algorithm = builder.algorithm;
    this.dataType = builder.dataType;
    this.keyId = builder.keyId;
    this.tweak = builder.tweak;
    this.encoding = builder.encoding;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public String getDataType() {
    return dataType;
  }

  public String getKeyId() {
    return keyId;
  }

  public String getTweak() {
    return tweak;
  }

  public String getEncoding() {
    return encoding;
  }

  @Override
  public String toString() {
    return "FieldMetaData{" +
        "algorithm='" + algorithm + '\'' +
        ", dataType='" + dataType + '\'' +
        ", keyId='" + keyId + '\'' +
        ", tweak='" + tweak + '\'' +
        ", encoding='" + encoding + '\'' +
        '}';
  }

  //TODO: add static validation methods for semantic correctness of fields and ensure they are called from builder and constructor

  public static final class Builder {

    private String algorithm;

    private String dataType;

    private String keyId;

    private String tweak;

    private String encoding;

    private Builder() {
    }

    //TODO: builder should perform semantic validation for all fields

    public Builder algorithm(String algorithm) {
      this.algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
      return this;
    }

    public Builder dataType(String dataType) {
      this.dataType = Objects.requireNonNull(dataType, "dataType must not be null");
      return this;
    }

    public Builder keyId(String keyId) {
      this.keyId = Objects.requireNonNull(keyId, "keyId must not be null");
      return this;
    }

    public Builder tweak(String tweak) {
      this.tweak = Objects.requireNonNull(tweak, "tweak must not be null");
      return this;
    }

    public Builder encoding(String encoding) {
      this.encoding = Objects.requireNonNull(encoding, "encoding must not be null");
      return this;
    }

    public FieldMetaData build() {
      return new FieldMetaData(this);
    }
  }

}
