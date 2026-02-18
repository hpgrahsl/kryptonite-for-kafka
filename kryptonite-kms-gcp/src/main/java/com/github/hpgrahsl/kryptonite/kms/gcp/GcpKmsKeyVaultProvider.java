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

import com.github.hpgrahsl.kryptonite.keys.AbstractKeyVault;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyVaultProvider;

public class GcpKmsKeyVaultProvider implements KmsKeyVaultProvider {

  @Override
  public String kmsType() {
    return "GCP_SM_SECRETS";
  }

  @Override
  public AbstractKeyVault createKeyVault(String kmsConfig) {
    return new GcpKeyVault(new GcpSecretResolver(kmsConfig, GcpKeyVault.SECRET_NAME_PREFIX), true);
  }

  @Override
  public AbstractKeyVault createKeyVaultEncrypted(KmsKeyEncryption kmsKeyEncryption, String kmsConfig) {
    return new GcpKeyVaultEncrypted(kmsKeyEncryption,
        new GcpSecretResolver(kmsConfig, GcpKeyVaultEncrypted.SECRET_NAME_PREFIX), true);
  }

}
