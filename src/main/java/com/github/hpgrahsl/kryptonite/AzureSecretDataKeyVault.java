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

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class AzureSecretDataKeyVault extends KeyVault {

  private final SecretClient secretClient;
  private final Map<String, byte[]> keys;

  public AzureSecretDataKeyVault(SecretClient secretClient) {
    super(new NoOpKeyStrategy());
    this.secretClient = secretClient;
    this.keys = new HashMap<>();
  }

  public AzureSecretDataKeyVault(SecretClient secretClient, KeyStrategy keyStrategy) {
    super(keyStrategy);
    this.secretClient = secretClient;
    this.keys = new HashMap<>();
  }

  public AzureSecretDataKeyVault(SecretClient secretClient, KeyStrategy keyStrategy, Map<String,byte[]> keys) {
    super(keyStrategy);
    this.secretClient = secretClient;
    this.keys = keys;
  }

  @Override
  byte[] readKey(String identifier) {
    var keyBytes = keys.get(identifier);
    if(keyBytes == null) {
      try {
        KeyVaultSecret secret = secretClient.getSecret(identifier);
        keyBytes = Base64.getDecoder().decode(secret.getValue().getBytes(StandardCharsets.UTF_8));
        keys.put(identifier, keyBytes);
      } catch (ResourceNotFoundException exc) {
        throw new KeyNotFoundException("could not find key for identifier '"
            + identifier + "' in " + AzureSecretDataKeyVault.class.getName() + " key vault");
      }
    }
    return keyStrategy.processKey(keyBytes,identifier);
  }

}
