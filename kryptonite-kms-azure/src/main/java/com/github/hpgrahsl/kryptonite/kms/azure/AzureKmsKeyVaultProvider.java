/*
 * Copyright (c) 2024. Hans-Peter Grahsl (grahslhp@gmail.com)
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

import com.github.hpgrahsl.kryptonite.keys.AbstractKeyVault;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyEncryption;
import com.github.hpgrahsl.kryptonite.kms.KmsKeyVaultProvider;

public class AzureKmsKeyVaultProvider implements KmsKeyVaultProvider {

    @Override
    public String kmsType() {
        return "AZ_KV_SECRETS";
    }

    @Override
    public AbstractKeyVault createKeyVault(String kmsConfig) {
        return new AzureKeyVault(new AzureSecretResolver(kmsConfig), true);
    }

    @Override
    public AbstractKeyVault createKeyVaultEncrypted(KmsKeyEncryption kmsKeyEncryption, String kmsConfig) {
        return new AzureKeyVaultEncrypted(kmsKeyEncryption, new AzureSecretResolver(kmsConfig), true);
    }

}
