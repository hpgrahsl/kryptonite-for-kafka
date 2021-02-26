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

package com.github.hpgrahsl.kafka.connect.transforms.kryptonite;

public class FieldMetaData {

  private String algorithm;

  private String dataType;

  private String keyId;

  public FieldMetaData() {
  }

  public FieldMetaData(String algorithm, String dataType, String keyId) {
    this.algorithm = algorithm;
    this.dataType = dataType;
    this.keyId = keyId;
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

  @Override
  public String toString() {
    return "FieldMetaData{" +
        "algorithm='" + algorithm + '\'' +
        ", dataType='" + dataType + '\'' +
        ", keyId='" + keyId + '\'' +
        '}';
  }

}
