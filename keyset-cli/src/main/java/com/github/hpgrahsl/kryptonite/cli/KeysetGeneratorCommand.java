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

package com.github.hpgrahsl.kryptonite.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "keyset-generator",
    mixinStandardHelpOptions = true,
    version = "keyset-generator 0.1.0",
    description = "Generates Tink keyset JSON configurations for use with kryptonite-for-kafka modules."
)
public class KeysetGeneratorCommand implements Callable<Integer> {

    public enum Algorithm {
        AES_GCM,
        AES_GCM_SIV,
        FPE_FF31
    }

    public enum KeySize {
        BITS_128(128),
        BITS_192(192),
        BITS_256(256);

        private final int bits;

        KeySize(int bits) {
            this.bits = bits;
        }

        public int getBits() {
            return bits;
        }

        public int getBytes() {
            return bits / 8;
        }

        public static KeySize fromBits(int bits) {
            for (KeySize ks : values()) {
                if (ks.bits == bits) {
                    return ks;
                }
            }
            String valid = Arrays.stream(values())
                .map(ks -> String.valueOf(ks.bits))
                .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                "Invalid key size: " + bits + ". Valid values: " + valid);
        }
    }

    static class KeySizeConverter implements ITypeConverter<KeySize> {
        @Override
        public KeySize convert(String value) {
            return KeySize.fromBits(Integer.parseInt(value));
        }
    }

    public enum OutputFormat {
        FULL,
        RAW
    }

    @Spec
    private CommandSpec spec;

    @Option(names = {"-a", "--algorithm"}, required = true,
            description = "Cipher algorithm: ${COMPLETION-CANDIDATES}")
    private Algorithm algorithm;

    @Option(names = {"-i", "--identifier"},
            description = "Keyset identifier (required when output format is FULL)")
    private String identifier;

    @Option(names = {"-f", "--output-format"}, defaultValue = "FULL",
            description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
    private OutputFormat outputFormat;

    @Option(names = {"-o", "--output"},
            description = "Output file path (default: stdout)")
    private File outputFile;

    @Option(names = {"-s", "--key-size"}, defaultValue = "256",
            converter = KeySizeConverter.class,
            description = "Key size in bits (default: ${DEFAULT-VALUE}). "
                + "AES_GCM: 128 or 256. AES_GCM_SIV: fixed (flag ignored). FPE_FF31: 128, 192, or 256.")
    private KeySize keySize;

    @Option(names = {"-n", "--num-keys"}, defaultValue = "1",
            description = "Number of keys per keyset (default: ${DEFAULT-VALUE}, range: 1-1000)")
    private int numKeys;

    @Option(names = {"-k", "--num-keysets"}, defaultValue = "1",
            description = "Number of keysets to generate (default: ${DEFAULT-VALUE}, range: 1-1000). "
                + "When > 1, output is a JSON array and identifiers are suffixed with _1, _2, etc.")
    private int numKeysets;

    @Option(names = {"--initial-key-id"}, defaultValue = "10000",
            description = "Starting key ID (default: ${DEFAULT-VALUE}), incremented by 1 for each additional key")
    private int initialKeyId;

    @Option(names = {"-p", "--pretty"}, defaultValue = "false",
            description = "Pretty-print JSON output (default: single-line)")
    private boolean pretty;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (outputFormat == OutputFormat.FULL && (identifier == null || identifier.isBlank())) {
            err.println("Error: --identifier is required when output format is FULL");
            return 1;
        }

        if (numKeys < 1 || numKeys > 1000) {
            err.println("Error: --num-keys must be between 1 and 1000, got: " + numKeys);
            return 1;
        }

        if (numKeysets < 1 || numKeysets > 1000) {
            err.println("Error: --num-keysets must be between 1 and 1000, got: " + numKeysets);
            return 1;
        }

        if (algorithm == Algorithm.AES_GCM_SIV && keySize != KeySize.BITS_256) {
            err.println("NOTE: --key-size is ignored for AES_GCM_SIV (fixed key size).");
        }

        ObjectMapper mapper = new ObjectMapper();
        if (pretty) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        }

        String output;
        if (numKeysets == 1) {
            output = generateSingleKeyset(mapper);
        } else {
            output = generateMultipleKeysets(mapper);
        }

        if (outputFile != null) {
            try (PrintWriter fileWriter = new PrintWriter(new FileWriter(outputFile))) {
                fileWriter.println(output);
            }
            err.println("Keyset written to: " + outputFile.getAbsolutePath());
        } else {
            out.println(output);
        }

        return 0;
    }

    private String generateSingleKeyset(ObjectMapper mapper) throws Exception {
        KeysetGenerator generator = createGenerator(initialKeyId);
        String rawKeysetJson = generator.generateKeysetJson();
        if (outputFormat == OutputFormat.FULL) {
            var rawNode = mapper.readTree(rawKeysetJson);
            ObjectNode wrapperNode = mapper.createObjectNode();
            wrapperNode.put("identifier", identifier);
            wrapperNode.set("material", rawNode);
            return mapper.writeValueAsString(wrapperNode);
        } else {
            var node = mapper.readTree(rawKeysetJson);
            return mapper.writeValueAsString(node);
        }
    }

    private String generateMultipleKeysets(ObjectMapper mapper) throws Exception {
        ArrayNode arrayNode = mapper.createArrayNode();
        for (int k = 0; k < numKeysets; k++) {
            int keyIdOffset = initialKeyId + (k * numKeys);
            KeysetGenerator generator = createGenerator(keyIdOffset);
            String rawKeysetJson = generator.generateKeysetJson();
            if (outputFormat == OutputFormat.FULL) {
                var rawNode = mapper.readTree(rawKeysetJson);
                ObjectNode wrapperNode = mapper.createObjectNode();
                wrapperNode.put("identifier", identifier + "_" + (k + 1));
                wrapperNode.set("material", rawNode);
                arrayNode.add(wrapperNode);
            } else {
                arrayNode.add(mapper.readTree(rawKeysetJson));
            }
        }
        return mapper.writeValueAsString(arrayNode);
    }

    private KeysetGenerator createGenerator(int keyIdStart) {
        return switch (algorithm) {
            case AES_GCM -> new TinkAeadKeysetGenerator(algorithm, keySize, numKeys, keyIdStart);
            case AES_GCM_SIV -> new TinkAeadKeysetGenerator(algorithm, KeySize.BITS_256, numKeys, keyIdStart);
            case FPE_FF31 -> new FpeKeysetGenerator(keySize, numKeys, keyIdStart);
        };
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new KeysetGeneratorCommand()).execute(args);
        System.exit(exitCode);
    }

}
