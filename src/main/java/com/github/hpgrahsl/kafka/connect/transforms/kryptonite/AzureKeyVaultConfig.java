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

import java.util.Objects;

public class AzureKeyVaultConfig {

  private String clientId;
  private String tenantId;
  private String clientSecret;
  private String keyVaultUrl;

  public AzureKeyVaultConfig() {
  }

  public AzureKeyVaultConfig(String clientId, String tenantId, String clientSecret,
      String keyVaultUrl) {
    this.clientId = clientId;
    this.tenantId = tenantId;
    this.clientSecret = clientSecret;
    this.keyVaultUrl = keyVaultUrl;
  }

  public String getClientId() {
    return clientId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public String getKeyVaultUrl() {
    return keyVaultUrl;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AzureKeyVaultConfig)) {
      return false;
    }
    AzureKeyVaultConfig that = (AzureKeyVaultConfig) o;
    return Objects.equals(clientId, that.clientId) && Objects
        .equals(tenantId, that.tenantId) && Objects.equals(clientSecret, that.clientSecret)
        && Objects.equals(keyVaultUrl, that.keyVaultUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(clientId, tenantId, clientSecret, keyVaultUrl);
  }

  @Override
  public String toString() {
    return "AzureKeyVaultConfig{" +
        "clientId='" + clientId + '\'' +
        ", tenantId='" + tenantId + '\'' +
        ", clientSecret='" + clientSecret + '\'' +
        ", keyVaultUrl='" + keyVaultUrl + '\'' +
        '}';
  }

}
