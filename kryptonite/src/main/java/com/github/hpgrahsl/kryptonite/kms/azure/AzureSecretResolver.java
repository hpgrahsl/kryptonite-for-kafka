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

package com.github.hpgrahsl.kryptonite.kms.azure;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.keys.KeyInvalidException;
import com.github.hpgrahsl.kryptonite.keys.KeyMaterialResolver;
import com.github.hpgrahsl.kryptonite.keys.KeyNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AzureSecretResolver implements KeyMaterialResolver {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private SecretClient secretClient;

  public AzureSecretResolver(String jsonKmsConfig) throws Exception {
    var keyVaultConfig = OBJECT_MAPPER.readValue(jsonKmsConfig,AzureKeyVaultConfig.class);
    this.secretClient = new SecretClientBuilder()
        .vaultUrl(keyVaultConfig.getKeyVaultUrl())
        .credential(new ClientSecretCredentialBuilder()
            .clientId(keyVaultConfig.getClientId())
            .clientSecret(keyVaultConfig.getClientSecret())
            .tenantId(keyVaultConfig.getTenantId())
            .build()).buildClient();
  }

  public AzureSecretResolver(SecretClient secretClient) {
    this.secretClient = secretClient;
  }

  @Override
  public byte[] resolveBytesKey(String identifier) {
    try {
      var secret = secretClient.getSecret(identifier);
      return Base64.getDecoder().decode(secret.getValue().getBytes(StandardCharsets.UTF_8));
    } catch (ResourceNotFoundException exc) {
      throw new KeyNotFoundException("could not resolve key for identifier '"
          + identifier + "' in " + AzureSecretResolver.class.getName() + " key resolver", exc);
    } catch (IllegalArgumentException exc) {
      throw new KeyInvalidException("could not decode key for identifier '"
          + identifier + "' in " + AzureSecretResolver.class.getName() + " key resolver", exc);
    }
  }

  @Override
  public String resolveBase64Key(String identifier) {
    try {
      var secret = secretClient.getSecret(identifier);
      var decoded = Base64.getDecoder().decode(secret.getValue().getBytes(StandardCharsets.UTF_8));
      return secret.getValue();
    } catch (ResourceNotFoundException exc) {
      throw new KeyNotFoundException("could not resolve key for identifier '"
          + identifier + "' in " + AzureSecretResolver.class.getName() + " key resolver", exc);
    } catch (IllegalArgumentException exc) {
      throw new KeyInvalidException("hit key with invalid encoding for identifier '"
          + identifier + "' in " + AzureSecretResolver.class.getName() + " key resolver", exc);
    }
  }

}
