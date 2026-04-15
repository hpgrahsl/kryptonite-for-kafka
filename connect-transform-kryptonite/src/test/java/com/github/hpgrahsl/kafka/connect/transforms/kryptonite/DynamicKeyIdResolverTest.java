package com.github.hpgrahsl.kafka.connect.transforms.kryptonite;

import com.github.hpgrahsl.kryptonite.config.KryptoniteSettings;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.transforms.util.SimpleConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("DynamicKeyIdResolver")
class DynamicKeyIdResolverTest {

    private static AbstractConfig config(Map<String, String> overrides) {
        var props = new LinkedHashMap<String, String>();
        props.put(KryptoniteSettings.FIELD_CONFIG, "[{\"name\":\"dummy\"}]");
        props.put(KryptoniteSettings.CIPHER_MODE, "ENCRYPT");
        props.put(KryptoniteSettings.CIPHER_ALGORITHM, "TINK/AES_GCM");
        props.put(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "default-key");
        props.put(KryptoniteSettings.DYNAMIC_KEY_ID_PREFIX, "__#");
        props.put(KryptoniteSettings.PATH_DELIMITER, ".");
        props.putAll(overrides);
        return new SimpleConfig(CipherField.CONFIG_DEF, props);
    }

    @Nested
    @DisplayName("Map root record")
    class MapRootRecord {

        @Test
        @DisplayName("returns static field-level key id unchanged")
        void returnsStaticFieldLevelKeyId() {
            var cfg = config(Map.of());
            var fc = FieldConfig.builder().name("id").keyId("explicit-key").build();

            assertEquals("explicit-key", DynamicKeyIdResolver.resolve(fc, cfg, mapRoot("de", "eu", "hello json")));
        }

        @Test
        @DisplayName("resolves dynamic default key id")
        void resolvesDynamicDefaultKeyId() {
            var cfg = config(Map.of(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "__#mySubDoc1.myString"));
            var fc = FieldConfig.builder().name("id").build();

            assertEquals("hello json", DynamicKeyIdResolver.resolve(fc, cfg, mapRoot("de", "eu", "hello json")));
        }

        @Test
        @DisplayName("resolves dynamic field-level key id")
        void resolvesDynamicFieldLevelKeyId() {
            var cfg = config(Map.of());
            var fc = FieldConfig.builder().name("id").keyId("__#myString").build();

            assertEquals("some foo bla text", DynamicKeyIdResolver.resolve(fc, cfg, mapRoot("de", "eu", "hello json")));
        }

        @Test
        @DisplayName("resolves dynamic envelope KEK fallback for KMS envelope")
        void resolvesDynamicEnvelopeKekFallbackForKmsEnvelope() {
            var cfg = config(Map.of(
                    KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "ignored-data-key",
                    KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER, "__#myString"));
            var fc = FieldConfig.builder().name("id").algorithm("TINK/AES_GCM_ENVELOPE_KMS").build();

            assertEquals("some foo bla text", DynamicKeyIdResolver.resolve(fc, cfg, mapRoot("de", "eu", "hello json")));
        }

        @Test
        @DisplayName("prefers field-level key id over envelope KEK fallback for KMS envelope")
        void prefersFieldLevelKeyIdOverEnvelopeKekFallbackForKmsEnvelope() {
            var cfg = config(Map.of(
                    KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER, "__#myString"));
            var fc = FieldConfig.builder().name("id").algorithm("TINK/AES_GCM_ENVELOPE_KMS").keyId("__#mySubDoc1.myString").build();

            assertEquals("hello json", DynamicKeyIdResolver.resolve(fc, cfg, mapRoot("de", "eu", "hello json")));
        }

        @Test
        @DisplayName("resolves path through map to nested struct")
        void resolvesPathThroughMapToNestedStruct() {
            var cfg = config(Map.of(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "__#mySubDoc2.nested.code"));
            var fc = FieldConfig.builder().name("id").build();
            var rootRecord = new LinkedHashMap<>(mapRoot("de", "eu", "hello json"));
            rootRecord.put("mySubDoc2", Map.of("nested", structNested("dk"), "k1", 9));

            assertEquals("dk", DynamicKeyIdResolver.resolve(fc, cfg, rootRecord));
        }

        @Test
        @DisplayName("throws when field path is missing")
        void throwsWhenFieldPathIsMissing() {
            var cfg = config(Map.of(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "__#missing.path"));
            var fc = FieldConfig.builder().name("id").build();

            var exception = assertThrows(DataException.class,
                    () -> DynamicKeyIdResolver.resolve(fc, cfg, mapRoot("de", "eu", "hello json")));
            assertEquals("Dynamic key identifier resolution failed for field path 'missing.path': an intermediate path segment was missing or did not resolve to a structured object", exception.getMessage());
        }

        @Test
        @DisplayName("throws when resolved value is not a string")
        void throwsWhenResolvedValueIsNotAString() {
            var cfg = config(Map.of(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "__#mySubDoc2.k1"));
            var fc = FieldConfig.builder().name("id").build();

            var exception = assertThrows(DataException.class,
                    () -> DynamicKeyIdResolver.resolve(fc, cfg, mapRoot("de", "eu", "hello json")));
            assertEquals("Dynamic key identifier resolution failed for field path 'k1': resolved to type Integer but a string value is required", exception.getMessage());
        }

        @Test
        @DisplayName("throws when resolved value is blank")
        void throwsWhenResolvedValueIsBlank() {
            var cfg = config(Map.of(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "__#mySubDoc1.myString"));
            var fc = FieldConfig.builder().name("id").build();

            var exception = assertThrows(DataException.class,
                    () -> DynamicKeyIdResolver.resolve(fc, cfg, mapRoot("de", "eu", "   ")));
            assertEquals("Dynamic key identifier resolution failed for expression '__#mySubDoc1.myString': field path 'mySubDoc1.myString' resolved to a blank string but a non-blank key id is required", exception.getMessage());
        }

        @Test
        @DisplayName("throws when dynamic expression has no suffix after prefix")
        void throwsWhenDynamicExpressionHasNoSuffix() {
            var cfg = config(Map.of(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "__#"));
            var fc = FieldConfig.builder().name("id").build();

            var exception = assertThrows(DataException.class,
                    () -> DynamicKeyIdResolver.resolve(fc, cfg, mapRoot("de", "eu", "hello json")));
            assertEquals("Dynamic key identifier '__#' has no field path after prefix '__#'", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Struct root record")
    class StructRootRecord {

        @Test
        @DisplayName("returns static field-level key id unchanged")
        void returnsStaticFieldLevelKeyId() {
            var cfg = config(Map.of());
            var fc = FieldConfig.builder().name("id").keyId("explicit-key").build();

            assertEquals("explicit-key", DynamicKeyIdResolver.resolve(fc, cfg, structRoot("de", "eu", "hello json")));
        }

        @Test
        @DisplayName("resolves dynamic default key id")
        void resolvesDynamicDefaultKeyId() {
            var cfg = config(Map.of(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "__#mySubDoc1.myString"));
            var fc = FieldConfig.builder().name("id").build();

            assertEquals("hello json", DynamicKeyIdResolver.resolve(fc, cfg, structRoot("de", "eu", "hello json")));
        }

        @Test
        @DisplayName("resolves dynamic field-level key id")
        void resolvesDynamicFieldLevelKeyId() {
            var cfg = config(Map.of());
            var fc = FieldConfig.builder().name("id").keyId("__#myString").build();

            assertEquals("some foo bla text", DynamicKeyIdResolver.resolve(fc, cfg, structRoot("de", "eu", "hello json")));
        }

        @Test
        @DisplayName("resolves dynamic envelope KEK fallback for KMS envelope")
        void resolvesDynamicEnvelopeKekFallbackForKmsEnvelope() {
            var cfg = config(Map.of(
                    KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "ignored-data-key",
                    KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER, "__#myString"));
            var fc = FieldConfig.builder().name("id").algorithm("TINK/AES_GCM_ENVELOPE_KMS").build();

            assertEquals("some foo bla text", DynamicKeyIdResolver.resolve(fc, cfg, structRoot("de", "eu", "hello json")));
        }

        @Test
        @DisplayName("prefers field-level key id over envelope KEK fallback for KMS envelope")
        void prefersFieldLevelKeyIdOverEnvelopeKekFallbackForKmsEnvelope() {
            var cfg = config(Map.of(
                    KryptoniteSettings.ENVELOPE_KEK_IDENTIFIER, "__#myString"));
            var fc = FieldConfig.builder().name("id").algorithm("TINK/AES_GCM_ENVELOPE_KMS").keyId("__#mySubDoc1.myString").build();

            assertEquals("hello json", DynamicKeyIdResolver.resolve(fc, cfg, structRoot("de", "eu", "hello json")));
        }

        @Test
        @DisplayName("resolves path through struct to nested map to nested struct")
        void resolvesPathThroughStructToNestedMapToNestedStruct() {
            var cfg = config(Map.of(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "__#mySubDoc2.nested.code"));
            var fc = FieldConfig.builder().name("id").build();

            assertEquals("dk", DynamicKeyIdResolver.resolve(fc, cfg, structRootWithNestedStructInMap("de", "eu", "hello json", "dk")));
        }

        @Test
        @DisplayName("throws when field path is missing")
        void throwsWhenFieldPathIsMissing() {
            var cfg = config(Map.of(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "__#missing.path"));
            var fc = FieldConfig.builder().name("id").build();

            var exception = assertThrows(DataException.class,
                    () -> DynamicKeyIdResolver.resolve(fc, cfg, structRoot("de", "eu", "hello json")));
            assertEquals("Dynamic key identifier resolution failed for field path 'missing.path': an intermediate path segment was missing or did not resolve to a structured object", exception.getMessage());
        }

        @Test
        @DisplayName("throws when resolved value is not a string")
        void throwsWhenResolvedValueIsNotAString() {
            var cfg = config(Map.of(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "__#mySubDoc2.k1"));
            var fc = FieldConfig.builder().name("id").build();

            var exception = assertThrows(DataException.class,
                    () -> DynamicKeyIdResolver.resolve(fc, cfg, structRoot("de", "eu", "hello json")));
            assertEquals("Dynamic key identifier resolution failed for field path 'k1': resolved to type Integer but a string value is required", exception.getMessage());
        }

        @Test
        @DisplayName("throws when resolved value is blank")
        void throwsWhenResolvedValueIsBlank() {
            var cfg = config(Map.of(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "__#mySubDoc1.myString"));
            var fc = FieldConfig.builder().name("id").build();

            var exception = assertThrows(DataException.class,
                    () -> DynamicKeyIdResolver.resolve(fc, cfg, structRoot("de", "eu", "   ")));
            assertEquals("Dynamic key identifier resolution failed for expression '__#mySubDoc1.myString': field path 'mySubDoc1.myString' resolved to a blank string but a non-blank key id is required", exception.getMessage());
        }

        @Test
        @DisplayName("throws when dynamic expression has no suffix after prefix")
        void throwsWhenDynamicExpressionHasNoSuffix() {
            var cfg = config(Map.of(KryptoniteSettings.CIPHER_DATA_KEY_IDENTIFIER, "__#"));
            var fc = FieldConfig.builder().name("id").build();

            var exception = assertThrows(DataException.class,
                    () -> DynamicKeyIdResolver.resolve(fc, cfg, structRoot("de", "eu", "hello json")));
            assertEquals("Dynamic key identifier '__#' has no field path after prefix '__#'", exception.getMessage());
        }
    }

    private static Map<String, Object> mapRoot(String country, String region, String nestedString) {
        return new LinkedHashMap<>(Map.of(
                "id", "1234567890",
                "myString", "some foo bla text",
                "mySubDoc1", Map.of("country", country, "region", region, "myString", nestedString),
                "mySubDoc2", Map.of("k1", 9, "k2", 8),
                "myArray1", List.of("str_1", "str_2")
        ));
    }

    private static Struct structNested(String code) {
        var nestedSchema = SchemaBuilder.struct()
                .field("code", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .build();
        return new Struct(nestedSchema).put("code", code);
    }

    private static Struct structRoot(String country, String region, String nestedString) {
        var subSchema = SchemaBuilder.struct()
                .field("country", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("region", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("myString", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .build();
        var schema = SchemaBuilder.struct()
                .field("id", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("myString", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("mySubDoc1", subSchema)
                .field("mySubDoc2", SchemaBuilder.map(org.apache.kafka.connect.data.Schema.STRING_SCHEMA, org.apache.kafka.connect.data.Schema.INT32_SCHEMA).build())
                .build();
        return new Struct(schema)
                .put("id", "1234567890")
                .put("myString", "some foo bla text")
                .put("mySubDoc1", new Struct(subSchema)
                        .put("country", country)
                        .put("region", region)
                        .put("myString", nestedString))
                .put("mySubDoc2", Map.of("k1", 9, "k2", 8));
    }

    private static Struct structRootWithNestedStructInMap(String country, String region, String nestedString, String nestedCode) {
        var subSchema = SchemaBuilder.struct()
                .field("country", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("region", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("myString", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .build();
        var nestedSchema = SchemaBuilder.struct()
                .field("code", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .build();
        var schema = SchemaBuilder.struct()
                .field("id", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("myString", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                .field("mySubDoc1", subSchema)
                .field("mySubDoc2", SchemaBuilder.map(org.apache.kafka.connect.data.Schema.STRING_SCHEMA, nestedSchema).build())
                .build();
        return new Struct(schema)
                .put("id", "1234567890")
                .put("myString", "some foo bla text")
                .put("mySubDoc1", new Struct(subSchema)
                        .put("country", country)
                        .put("region", region)
                        .put("myString", nestedString))
                .put("mySubDoc2", Map.of("nested", structNested(nestedCode)));
    }
}
