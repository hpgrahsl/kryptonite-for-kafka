/*
 * Copyright (c) 2022. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.funqy.http.kryptonite;

import javax.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@Singleton
public class CustomConfiguration {
    
    @ConfigProperty(name="cipher.algorithm", defaultValue = "TINK/AES_GCM")
    public String cipherAlgorithm;

    @ConfigProperty(name="cipher.data.key.identifier")
    public String cipherDataKeyIdentifier;

    @ConfigProperty(name="secret.key.material")
    public String secretKeyMaterial;

    @ConfigProperty(name="key.source")
    public CipherFieldResource.KeySource keySource;

    @ConfigProperty(name="kms.type")
    public CipherFieldResource.KmsType kmsType;

    @ConfigProperty(name="kms.config", defaultValue = "{}")
    public String kmsConfig;

    @ConfigProperty(name="dynamic.key.id.prefix", defaultValue = "__#")
    public String dynamicKeyIdPrefix;

    @ConfigProperty(name="path.delimiter", defaultValue = ".")
    public String pathDelimiter;

    @ConfigProperty(name="field.mode")
    public CipherFieldResource.FieldMode fieldMode;

}
