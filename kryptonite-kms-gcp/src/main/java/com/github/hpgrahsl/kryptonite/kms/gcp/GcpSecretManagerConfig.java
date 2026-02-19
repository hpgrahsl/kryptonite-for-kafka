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

import com.google.gson.JsonParser;
import java.util.Objects;

public class GcpSecretManagerConfig {

  private final String credentials;
  private final String projectId;

  public GcpSecretManagerConfig(String credentials) {
    this.credentials = credentials;
    try {
      this.projectId = JsonParser.parseString(credentials).getAsJsonObject()
          .get("project_id").getAsString();
    } catch (Exception exc) {
      throw new RuntimeException("failed to extract project_id from credentials", exc);
    }
  }

  public String getCredentials() {
    return credentials;
  }

  public String getProjectId() {
    return projectId;
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
    return Objects.equals(credentials, that.credentials)
        && Objects.equals(projectId, that.projectId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(credentials, projectId);
  }

  @Override
  public String toString() {
    return "GcpSecretManagerConfig{"
        + "projectId='" + projectId + '\''
        + ", credentials='[REDACTED]'"
        + '}';
  }

}
