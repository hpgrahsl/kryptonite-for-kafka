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

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.ListSecretsRequest;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.amazonaws.services.secretsmanager.model.SecretListEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.keys.KeyMaterialResolver;
import com.github.hpgrahsl.kryptonite.keys.KeyNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AwsSecretResolver implements KeyMaterialResolver {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final AWSSecretsManager secretsManagerClient;
  private final String secretNamePrefix;

  public AwsSecretResolver(String jsonKmsConfig, String secretNamePrefix) {
    try {
      var config = OBJECT_MAPPER.readValue(jsonKmsConfig, AwsSecretsManagerConfig.class);
      this.secretsManagerClient = AWSSecretsManagerClientBuilder.standard()
          .withCredentials(new AWSStaticCredentialsProvider(
              new BasicAWSCredentials(config.getAccessKey(), config.getSecretKey())
          ))
          .withRegion(config.getRegion())
          .build();
      this.secretNamePrefix = secretNamePrefix;
    } catch (Exception exc) {
      throw new RuntimeException("failed to create " + AwsSecretResolver.class.getSimpleName(), exc);
    }
  }

  public AwsSecretResolver(AWSSecretsManager secretsManagerClient, String secretNamePrefix) {
    this.secretsManagerClient = secretsManagerClient;
    this.secretNamePrefix = secretNamePrefix;
  }

  @Override
  public Collection<String> resolveIdentifiers() {
    List<String> identifiers = new ArrayList<>();
    String nextToken = null;
    do {
      var request = new ListSecretsRequest();
      if (nextToken != null) {
        request.setNextToken(nextToken);
      }
      var result = secretsManagerClient.listSecrets(request);
      for (SecretListEntry entry : result.getSecretList()) {
        String name = entry.getName();
        if (name.startsWith(secretNamePrefix)) {
          identifiers.add(name.substring(secretNamePrefix.length()));
        }
      }
      nextToken = result.getNextToken();
    } while (nextToken != null);
    return identifiers;
  }

  @Override
  public String resolveKeyset(String identifier) {
    try {
      var result = secretsManagerClient.getSecretValue(
          new GetSecretValueRequest().withSecretId(secretNamePrefix + identifier)
      );
      return result.getSecretString();
    } catch (ResourceNotFoundException exc) {
      throw new KeyNotFoundException("could not resolve key for identifier '"
          + identifier + "' (secret name: '" + secretNamePrefix + identifier
          + "') in " + AwsSecretResolver.class.getName() + " key resolver", exc);
    }
  }

}
