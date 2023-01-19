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
