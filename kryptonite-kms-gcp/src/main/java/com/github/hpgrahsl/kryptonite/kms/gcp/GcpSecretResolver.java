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

package com.github.hpgrahsl.kryptonite.kms.gcp;

import com.github.hpgrahsl.kryptonite.keys.KeyMaterialResolver;
import com.github.hpgrahsl.kryptonite.keys.KeyNotFoundException;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GcpSecretResolver implements KeyMaterialResolver {

  private final SecretManagerServiceClient secretManagerClient;
  private final String projectId;
  private final String secretNamePrefix;

  public GcpSecretResolver(String jsonKmsConfig, String secretNamePrefix) {
    try {
      var config = new GcpSecretManagerConfig(jsonKmsConfig);
      GoogleCredentials credentials = GoogleCredentials.fromStream(
          new ByteArrayInputStream(config.getCredentials().getBytes(StandardCharsets.UTF_8))
      );
      SecretManagerServiceSettings settings = SecretManagerServiceSettings.newBuilder()
          .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
          .build();
      this.secretManagerClient = SecretManagerServiceClient.create(settings);
      this.projectId = config.getProjectId();
      this.secretNamePrefix = secretNamePrefix;
    } catch (Exception exc) {
      throw new RuntimeException("failed to create " + GcpSecretResolver.class.getSimpleName(), exc);
    }
  }

  public GcpSecretResolver(SecretManagerServiceClient secretManagerClient, String projectId, String secretNamePrefix) {
    this.secretManagerClient = secretManagerClient;
    this.projectId = projectId;
    this.secretNamePrefix = secretNamePrefix;
  }

  @Override
  public Collection<String> resolveIdentifiers() {
    List<String> identifiers = new ArrayList<>();
    for (Secret secret : secretManagerClient.listSecrets(ProjectName.of(projectId)).iterateAll()) {
      String secretId = SecretName.parse(secret.getName()).getSecret();
      if (secretId.startsWith(secretNamePrefix)) {
        identifiers.add(secretId.substring(secretNamePrefix.length()));
      }
    }
    return identifiers;
  }

  @Override
  public String resolveKeyset(String identifier) {
    try {
      SecretVersionName secretVersionName =
          SecretVersionName.of(projectId, secretNamePrefix + identifier, "latest");
      return secretManagerClient.accessSecretVersion(secretVersionName)
          .getPayload().getData().toStringUtf8();
    } catch (Exception exc) {
      throw new KeyNotFoundException("could not resolve key for identifier '"
          + identifier + "' (secret name: '" + secretNamePrefix + identifier
          + "') in " + GcpSecretResolver.class.getName() + " key resolver", exc);
    }
  }

}
