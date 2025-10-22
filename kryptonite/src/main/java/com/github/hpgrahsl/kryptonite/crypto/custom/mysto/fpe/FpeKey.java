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

/**
 * Represents an FPE key containing only the key material itself.
 *
 * Other settings such as the alphabet and the tweak to use are specified separately via FpeParameters.
 */
public class FpeKey {
    
    private final byte[] keyMaterial;

    public FpeKey(byte[] keyMaterial) {
        if (keyMaterial == null || (
                keyMaterial.length != 16
                && keyMaterial.length != 24
                && keyMaterial.length != 32)) {
            throw new IllegalArgumentException("key must be either 128 bits, 192 bits, or 256 bits!");
        }
        this.keyMaterial = keyMaterial;
    }

    public byte[] getKeyMaterial() {
        return keyMaterial;
    }
}
