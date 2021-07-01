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

import com.github.hpgrahsl.kafka.connect.transforms.kryptonite.FieldMetaData;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class PayloadMetaData {

  private String version;
  private String algorithmId;

  public PayloadMetaData() {
  }

  public PayloadMetaData(String version, String algorithmId) {
    this.version = Objects.requireNonNull(version);
    this.algorithmId = Objects.requireNonNull(algorithmId);
  }

  public static PayloadMetaData from(FieldMetaData fieldMetaData) {
    return new PayloadMetaData(
        Kryptonite.KRYPTONITE_VERSION,
        Kryptonite.CIPHERNAME_ID_LUT.get(fieldMetaData.getAlgorithm())
    );
  }

  public String getVersion() {
    return version;
  }

  public String getAlgorithmId() {
    return algorithmId;
  }

  public byte[] asBytes() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    baos.writeBytes(version.getBytes(StandardCharsets.UTF_8));
    baos.writeBytes(algorithmId.getBytes(StandardCharsets.UTF_8));
    return baos.toByteArray();
  }

  @Override
  public String toString() {
    return "PayloadMetaData{" +
        "version='" + version + '\'' +
        ", algorithmId='" + algorithmId + '\'' +
        '}';
  }

}
