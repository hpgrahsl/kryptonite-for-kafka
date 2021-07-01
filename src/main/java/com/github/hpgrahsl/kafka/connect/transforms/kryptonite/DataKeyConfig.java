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

import java.util.Base64;
import java.util.Objects;

public class DataKeyConfig {

  private String identifier;
  private String material = "";

  public DataKeyConfig() {
  }

  public DataKeyConfig(String identifier, String material) {
    this.identifier = identifier;
    this.material = material;
  }

  public String getIdentifier() {
    return identifier;
  }

  public String getMaterial() {
    return material;
  }

  public byte[] getKeyBytes() {
    return Base64.getDecoder().decode(material);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DataKeyConfig)) {
      return false;
    }
    DataKeyConfig that = (DataKeyConfig) o;
    return Objects.equals(identifier, that.identifier) && Objects
        .equals(material, that.material);
  }

  @Override
  public int hashCode() {
    return Objects.hash(identifier, material);
  }

  @Override
  public String toString() {
    return "DataKeyConfig{" +
        "identifier='" + identifier + '\'' +
        ", material='" + material + '\'' +
        '}';
  }

}
