/*
 * Copyright (c) 2026. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.kryptonite.kms.aws;

import java.util.Objects;

public class AwsSecretsManagerConfig {

  private String accessKey;
  private String secretKey;
  private String region;

  public AwsSecretsManagerConfig() {
  }

  public AwsSecretsManagerConfig(String accessKey, String secretKey, String region) {
    this.accessKey = accessKey;
    this.secretKey = secretKey;
    this.region = region;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public String getRegion() {
    return region;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AwsSecretsManagerConfig)) {
      return false;
    }
    AwsSecretsManagerConfig that = (AwsSecretsManagerConfig) o;
    return Objects.equals(accessKey, that.accessKey)
        && Objects.equals(secretKey, that.secretKey)
        && Objects.equals(region, that.region);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accessKey, secretKey, region);
  }

  @Override
  public String toString() {
    return "AwsSecretsManagerConfig{"
        + "accessKey='" + accessKey + '\''
        + ", secretKey='" + secretKey + '\''
        + ", region='" + region + '\''
        + '}';
  }

}
