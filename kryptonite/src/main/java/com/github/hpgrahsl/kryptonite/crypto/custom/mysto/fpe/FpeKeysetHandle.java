/*
 * Copyright (c) 2025. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe;

import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.proto.KeyData;
import com.google.crypto.tink.proto.Keyset;
import java.security.GeneralSecurityException;

/**
 * Provides integration between FPE and Tink's KeysetHandle.
 *
 * Keys contain only key material (no alphabet).
 * Alphabet is specified separately via FpeParameters when getting primitives.
 *
 * This allows:
 * - Same key to be used with different alphabets
 * - Standard Tink tooling compatibility
 * - Clean separation of key material from algorithm parameters
 */
public final class FpeKeysetHandle {
    
    private static final String TYPE_URL = "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey";
    
    private FpeKeysetHandle() {}

    /**
     * Extracts an FPE primitive from a KeysetHandle with specified parameters.
     *
     * This method reads the keyset and creates an FPE instance from the
     * primary key's data, using the provided parameters for alphabet configuration.
     *
     * @param keysetHandle the KeysetHandle containing the FPE key
     * @param parameters the FPE parameters (alphabet, etc.)
     * @return an FPE primitive instance
     */
    public static Fpe getPrimitive(KeysetHandle keysetHandle, FpeParameters parameters)
            throws Exception {
        try {
            if (parameters == null) {
                throw new IllegalArgumentException("FpeParameters must not be null");
            }
            Keyset keyset = CleartextKeysetHandle.getKeyset(keysetHandle);
            Keyset.Key primaryKey = null;
            for (Keyset.Key key : keyset.getKeyList()) {
                if (key.getKeyId() == keyset.getPrimaryKeyId()) {
                    primaryKey = key;
                    break;
                }
            }
            if (primaryKey == null) {
                throw new GeneralSecurityException("No primary key found");
            }
            KeyData keyData = primaryKey.getKeyData();
            if (!TYPE_URL.equals(keyData.getTypeUrl())) {
                throw new GeneralSecurityException(
                    "expected FPE key (type url: "+ TYPE_URL +"), got: " + keyData.getTypeUrl());
            }
            return new FpeImpl(new FpeKey(keyData.getValue().toByteArray()), parameters);
        } catch (Exception e) {
            throw new KryptoniteException("Failed to extract FPE primitive", e);
        }
    }

}
