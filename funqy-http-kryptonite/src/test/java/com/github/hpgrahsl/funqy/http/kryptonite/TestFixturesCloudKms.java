/*
 * Copyright (c) 2023. Hans-Peter Grahsl (grahslhp@gmail.com)
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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class TestFixturesCloudKms {

    private static final String PATH = "src/test/resources/credentials.properties";
    private static final Properties CREDENTIALS = new Properties();

    public static synchronized Properties readCredentials() throws IOException {
        if(!CREDENTIALS.isEmpty()) {
            return CREDENTIALS;
        }
        try (InputStreamReader isr = new FileReader(new File(PATH), StandardCharsets.UTF_8)) {
            CREDENTIALS.load(isr);
            return CREDENTIALS;
        }
    }

}
