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

package com.github.hpgrahsl.kryptonite.keys;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.github.hpgrahsl.kryptonite.TestFixtures;
import com.github.hpgrahsl.kryptonite.config.ConfigReader;

public class TinkKeyVaultTest {

    public static List<String> KNOWN_KEYSET_IDENTIFIERS = List.of("keyA","keyB","key9","key8");
    public static String UNKNOWN_KEYSET_IDENTIFIER = "keyXYZ";
    
    @Test
    void tinkKeyVaultLoadsFromValidConfigTest() {
        var tinkKeyConfig = ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG);
        var tinkKeyVault = new TinkKeyVault(tinkKeyConfig);
        
        assertAll(
            () -> assertEquals(TestFixtures.CIPHER_DATA_KEYS_COUNT, tinkKeyVault.numKeysetHandles()),
            () -> assertAll(
                KNOWN_KEYSET_IDENTIFIERS.stream().<Executable>map(
                    id -> (() -> assertNotNull(tinkKeyVault.readKeysetHandle(id),"error: known keyset identifier "+id+" not found in key vault"))
                )
            ),
            () -> assertThrows(KeyNotFoundException.class,() -> tinkKeyVault.readKeysetHandle(UNKNOWN_KEYSET_IDENTIFIER))
        );
    }

}
