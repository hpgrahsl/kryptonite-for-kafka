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

package com.github.hpgrahsl.kryptonite.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.daead.DeterministicAeadConfig;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import picocli.CommandLine;

class KeysetGeneratorCommandTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeAll
    static void setup() throws Exception {
        AeadConfig.register();
        DeterministicAeadConfig.register();
    }

    private CommandLine createCommand(StringWriter out, StringWriter err) {
        CommandLine cmd = new CommandLine(new KeysetGeneratorCommand());
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        return cmd;
    }

    @Test
    @DisplayName("generate AES_GCM keyset in FULL format with identifier")
    void testAesGcmFullFormat() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-i", "my-test-key", "-f", "FULL");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertEquals("my-test-key", root.get("identifier").asText());
        assertNotNull(root.get("material"));
        JsonNode material = root.get("material");
        assertTrue(material.has("primaryKeyId"));
        assertTrue(material.has("key"));
        JsonNode keyEntry = material.get("key").get(0);
        assertEquals("type.googleapis.com/google.crypto.tink.AesGcmKey",
            keyEntry.get("keyData").get("typeUrl").asText());
        assertEquals("ENABLED", keyEntry.get("status").asText());
        assertEquals("TINK", keyEntry.get("outputPrefixType").asText());
    }

    @Test
    @DisplayName("generate AES_GCM_SIV keyset in FULL format with identifier")
    void testAesGcmSivFullFormat() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM_SIV", "-i", "my-siv-key", "-f", "FULL");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertEquals("my-siv-key", root.get("identifier").asText());
        JsonNode keyEntry = root.get("material").get("key").get(0);
        assertEquals("type.googleapis.com/google.crypto.tink.AesSivKey",
            keyEntry.get("keyData").get("typeUrl").asText());
    }

    @Test
    @DisplayName("generate AES_GCM keyset in RAW format without wrapper")
    void testAesGcmRawFormat() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-f", "RAW");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertFalse(root.has("identifier"));
        assertTrue(root.has("primaryKeyId"));
        assertTrue(root.has("key"));
    }

    @Test
    @DisplayName("generated AES_GCM keyset can be loaded by Tink")
    void testAesGcmKeysetIsLoadable() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-f", "RAW");

        assertEquals(0, exitCode);
        var handle = TinkJsonProtoKeysetFormat.parseKeyset(
            out.toString().trim(), InsecureSecretKeyAccess.get());
        assertNotNull(handle);
    }

    @Test
    @DisplayName("generated AES_GCM_SIV keyset can be loaded by Tink")
    void testAesGcmSivKeysetIsLoadable() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM_SIV", "-f", "RAW");

        assertEquals(0, exitCode);
        var handle = TinkJsonProtoKeysetFormat.parseKeyset(
            out.toString().trim(), InsecureSecretKeyAccess.get());
        assertNotNull(handle);
    }

    @Test
    @DisplayName("generate AES_GCM keyset with 128-bit key size")
    void testAesGcm128BitKey() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-f", "RAW", "-s", "128");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        JsonNode keyEntry = root.get("key").get(0);
        assertEquals("type.googleapis.com/google.crypto.tink.AesGcmKey",
            keyEntry.get("keyData").get("typeUrl").asText());
        var handle = TinkJsonProtoKeysetFormat.parseKeyset(
            out.toString().trim(), InsecureSecretKeyAccess.get());
        assertNotNull(handle);
    }

    @Test
    @DisplayName("AES_GCM_SIV with non-default key-size prints info message")
    void testAesGcmSivIgnoresKeySize() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM_SIV", "-f", "RAW", "-s", "128");

        assertEquals(0, exitCode);
        assertTrue(err.toString().contains("ignored for AES_GCM_SIV"));
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertTrue(root.has("primaryKeyId"));
    }

    static Stream<Arguments> fpeKeySizes() {
        return Stream.of(
            Arguments.of(128, 16),
            Arguments.of(192, 24),
            Arguments.of(256, 32)
        );
    }

    @ParameterizedTest(name = "FPE key size {0} bits produces {1} byte key material")
    @MethodSource("fpeKeySizes")
    @DisplayName("generate FPE_FF31 keyset with different key sizes")
    void testFpeKeyGeneration(int keySizeBits, int expectedBytes) throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "FPE_FF31", "-f", "RAW", "-s", String.valueOf(keySizeBits));

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertTrue(root.has("primaryKeyId"));
        JsonNode keyEntry = root.get("key").get(0);
        assertEquals(FpeKeysetGenerator.FPE_TYPE_URL,
            keyEntry.get("keyData").get("typeUrl").asText());
        assertEquals("RAW", keyEntry.get("outputPrefixType").asText());
        assertEquals("SYMMETRIC", keyEntry.get("keyData").get("keyMaterialType").asText());

        String base64Value = keyEntry.get("keyData").get("value").asText();
        byte[] decoded = Base64.getDecoder().decode(base64Value);
        assertEquals(expectedBytes, decoded.length);
    }

    @Test
    @DisplayName("FPE_FF31 keyset in FULL format includes identifier")
    void testFpeFullFormat() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "FPE_FF31", "-i", "my-fpe-key", "-f", "FULL", "-s", "128");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertEquals("my-fpe-key", root.get("identifier").asText());
        assertNotNull(root.get("material"));
    }

    @Test
    @DisplayName("FULL format without identifier returns error")
    void testFullFormatWithoutIdentifierFails() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-f", "FULL");

        assertEquals(1, exitCode);
        assertTrue(err.toString().contains("--identifier is required"));
    }

    @Test
    @DisplayName("pretty-print produces indented JSON output")
    void testPrettyPrint() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-f", "RAW", "-p");

        assertEquals(0, exitCode);
        String output = out.toString();
        assertTrue(output.contains("\n"), "pretty-printed output should contain newlines");
        assertTrue(output.contains("  "), "pretty-printed output should contain indentation");
    }

    @Test
    @DisplayName("single-line output by default (no pretty-print)")
    void testSingleLineDefault() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-f", "RAW");

        assertEquals(0, exitCode);
        String output = out.toString().trim();
        assertFalse(output.contains("\n"), "default output should be single-line");
    }

    @Test
    @DisplayName("write keyset to output file")
    void testOutputToFile(@TempDir File tempDir) throws Exception {
        File outputFile = new File(tempDir, "keyset.json");
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-i", "file-key", "-f", "FULL",
            "-o", outputFile.getAbsolutePath());

        assertEquals(0, exitCode);
        assertTrue(outputFile.exists());
        String content = Files.readString(outputFile.toPath());
        JsonNode root = OBJECT_MAPPER.readTree(content);
        assertEquals("file-key", root.get("identifier").asText());
        assertNotNull(root.get("material"));
    }

    @Test
    @DisplayName("generate AES_GCM keyset with multiple keys and sequential IDs")
    void testMultiKeyAesGcm() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-f", "RAW", "-n", "5", "--initial-key-id", "20000");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertEquals(5, root.get("key").size());
        assertEquals(20000, root.get("primaryKeyId").asInt());

        Set<Integer> keyIds = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            JsonNode keyEntry = root.get("key").get(i);
            int keyId = keyEntry.get("keyId").asInt();
            assertEquals(20000 + i, keyId);
            keyIds.add(keyId);
            assertEquals("type.googleapis.com/google.crypto.tink.AesGcmKey",
                keyEntry.get("keyData").get("typeUrl").asText());
            assertEquals("ENABLED", keyEntry.get("status").asText());
        }
        assertEquals(5, keyIds.size(), "all key IDs must be unique");

        var handle = TinkJsonProtoKeysetFormat.parseKeyset(
            out.toString().trim(), InsecureSecretKeyAccess.get());
        assertNotNull(handle);
    }

    @Test
    @DisplayName("generate AES_GCM_SIV keyset with multiple keys")
    void testMultiKeyAesGcmSiv() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM_SIV", "-f", "RAW", "-n", "3", "--initial-key-id", "50000");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertEquals(3, root.get("key").size());
        assertEquals(50000, root.get("primaryKeyId").asInt());

        var handle = TinkJsonProtoKeysetFormat.parseKeyset(
            out.toString().trim(), InsecureSecretKeyAccess.get());
        assertNotNull(handle);
    }

    @Test
    @DisplayName("generate FPE_FF31 keyset with multiple keys and sequential IDs")
    void testMultiKeyFpe() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "FPE_FF31", "-f", "RAW", "-n", "4",
            "--initial-key-id", "30000", "-s", "192");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertEquals(4, root.get("key").size());
        assertEquals(30000, root.get("primaryKeyId").asInt());

        Set<Integer> keyIds = new HashSet<>();
        Set<String> keyValues = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            JsonNode keyEntry = root.get("key").get(i);
            int keyId = keyEntry.get("keyId").asInt();
            assertEquals(30000 + i, keyId);
            keyIds.add(keyId);

            String base64Value = keyEntry.get("keyData").get("value").asText();
            keyValues.add(base64Value);
            byte[] decoded = Base64.getDecoder().decode(base64Value);
            assertEquals(24, decoded.length, "192-bit key = 24 bytes");
        }
        assertEquals(4, keyIds.size(), "all key IDs must be unique");
        assertEquals(4, keyValues.size(), "all key values must be unique");
    }

    @Test
    @DisplayName("multi-key keyset in FULL format wraps correctly")
    void testMultiKeyFullFormat() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-i", "multi-key-set", "-f", "FULL",
            "-n", "3", "--initial-key-id", "40000");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertEquals("multi-key-set", root.get("identifier").asText());
        JsonNode material = root.get("material");
        assertEquals(3, material.get("key").size());
        assertEquals(40000, material.get("primaryKeyId").asInt());
    }

    @Test
    @DisplayName("num-keys out of range returns error")
    void testNumKeysOutOfRange() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-f", "RAW", "-n", "0");

        assertEquals(1, exitCode);
        assertTrue(err.toString().contains("--num-keys must be between 1 and 1000"));
    }

    @Test
    @DisplayName("default single key uses default initial-key-id")
    void testDefaultSingleKeyId() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-f", "RAW");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertEquals(1, root.get("key").size());
        assertEquals(10000, root.get("primaryKeyId").asInt());
        assertEquals(10000, root.get("key").get(0).get("keyId").asInt());
    }

    @Test
    @DisplayName("multiple keysets in FULL format produces JSON array with suffixed identifiers")
    void testMultipleKeysetsFull() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-i", "demo-key", "-f", "FULL",
            "-k", "3", "-n", "2", "--initial-key-id", "10000");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertTrue(root.isArray(), "output should be a JSON array");
        assertEquals(3, root.size());

        for (int k = 0; k < 3; k++) {
            JsonNode keyset = root.get(k);
            assertEquals("demo-key_" + (k + 1), keyset.get("identifier").asText());
            JsonNode material = keyset.get("material");
            assertEquals(2, material.get("key").size());
            int expectedStartId = 10000 + (k * 2);
            assertEquals(expectedStartId, material.get("primaryKeyId").asInt());
            assertEquals(expectedStartId, material.get("key").get(0).get("keyId").asInt());
            assertEquals(expectedStartId + 1, material.get("key").get(1).get("keyId").asInt());
        }
    }

    @Test
    @DisplayName("multiple keysets in RAW format produces JSON array without identifiers")
    void testMultipleKeysetsRaw() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "FPE_FF31", "-f", "RAW", "-k", "2",
            "-n", "3", "--initial-key-id", "5000", "-s", "128");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertTrue(root.isArray(), "output should be a JSON array");
        assertEquals(2, root.size());

        for (int k = 0; k < 2; k++) {
            JsonNode keyset = root.get(k);
            assertFalse(keyset.has("identifier"));
            assertEquals(3, keyset.get("key").size());
            int expectedStartId = 5000 + (k * 3);
            assertEquals(expectedStartId, keyset.get("primaryKeyId").asInt());
        }
    }

    @Test
    @DisplayName("single keyset (default) does not produce array")
    void testSingleKeysetNoArray() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-i", "solo", "-f", "FULL");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        assertFalse(root.isArray(), "single keyset should not be an array");
        assertEquals("solo", root.get("identifier").asText());
    }

    @Test
    @DisplayName("multiple keysets have non-overlapping key IDs")
    void testMultipleKeysetsNonOverlappingIds() throws Exception {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM_SIV", "-i", "siv-set", "-f", "FULL",
            "-k", "3", "-n", "4", "--initial-key-id", "1000");

        assertEquals(0, exitCode);
        JsonNode root = OBJECT_MAPPER.readTree(out.toString());
        Set<Integer> allKeyIds = new HashSet<>();
        for (int k = 0; k < 3; k++) {
            JsonNode keys = root.get(k).get("material").get("key");
            for (JsonNode key : keys) {
                assertTrue(allKeyIds.add(key.get("keyId").asInt()),
                    "key ID must be unique across all keysets");
            }
        }
        assertEquals(12, allKeyIds.size());
    }

    @Test
    @DisplayName("num-keysets out of range returns error")
    void testNumKeysetsOutOfRange() {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = createCommand(out, err);

        int exitCode = cmd.execute("-a", "AES_GCM", "-f", "RAW", "-k", "0");

        assertEquals(1, exitCode);
        assertTrue(err.toString().contains("--num-keysets must be between 1 and 1000"));
    }
}
