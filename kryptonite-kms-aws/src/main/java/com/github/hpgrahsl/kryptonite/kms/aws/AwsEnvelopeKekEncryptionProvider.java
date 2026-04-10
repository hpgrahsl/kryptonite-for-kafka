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

package com.github.hpgrahsl.kryptonite.kms.aws;

import com.github.hpgrahsl.kryptonite.kms.EnvelopeKekEncryption;
import com.github.hpgrahsl.kryptonite.kms.EnvelopeKekEncryptionProvider;

public class AwsEnvelopeKekEncryptionProvider implements EnvelopeKekEncryptionProvider {

    @Override
    public String kekType() {
        return "AWS";
    }

    @Override
    public EnvelopeKekEncryption createEnvelopeKekEncryption(String kekUri, String kekConfig) {
        return new AwsEnvelopeKekEncryption(kekUri, kekConfig);
    }

}
