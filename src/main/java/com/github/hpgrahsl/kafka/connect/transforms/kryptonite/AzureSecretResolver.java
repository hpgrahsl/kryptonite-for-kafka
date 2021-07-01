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

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.github.hpgrahsl.kryptonite.KeyInvalidException;
import com.github.hpgrahsl.kryptonite.KeyNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AzureSecretResolver implements KeyMaterialResolver {

  private SecretClient secretClient;

  public AzureSecretResolver(SecretClient secretClient) {
    this.secretClient = secretClient;
  }

  @Override
  public byte[] resolve(DataKeyConfig dataKeyConfig) {
    try {
      KeyVaultSecret secret = secretClient.getSecret(dataKeyConfig.getIdentifier());
      return Base64.getDecoder().decode(secret.getValue().getBytes(StandardCharsets.UTF_8));
    } catch (ResourceNotFoundException exc) {
      throw new KeyNotFoundException("could not resolve key for identifier '"
          + dataKeyConfig.getIdentifier() + "' in " + AzureSecretResolver.class.getName() + " key resolver", exc);
    } catch (IllegalArgumentException exc) {
      throw new KeyInvalidException("could not resolve key for identifier '"
          + dataKeyConfig.getIdentifier() + "' in " + AzureSecretResolver.class.getName() + " key resolver", exc);
    }
  }

}
