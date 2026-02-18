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

import java.util.Objects;

public class GcpSecretManagerConfig {

  private String projectId;
  private String credentials;

  public GcpSecretManagerConfig() {
  }

  public GcpSecretManagerConfig(String projectId, String credentials) {
    this.projectId = projectId;
    this.credentials = credentials;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getCredentials() {
    return credentials;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GcpSecretManagerConfig)) {
      return false;
    }
    GcpSecretManagerConfig that = (GcpSecretManagerConfig) o;
    return Objects.equals(projectId, that.projectId)
        && Objects.equals(credentials, that.credentials);
  }

  @Override
  public int hashCode() {
    return Objects.hash(projectId, credentials);
  }

  @Override
  public String toString() {
    return "GcpSecretManagerConfig{"
        + "projectId='" + projectId + '\''
        + ", credentials='[REDACTED]'"
        + '}';
  }

}
