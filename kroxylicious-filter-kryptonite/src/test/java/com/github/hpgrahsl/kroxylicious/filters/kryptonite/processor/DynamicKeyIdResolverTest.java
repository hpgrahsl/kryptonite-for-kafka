package com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.AvroGenericRecordAccessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.accessor.JsonObjectNodeAccessor;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DynamicKeyIdResolver")
class DynamicKeyIdResolverTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static KryptoniteFilterConfig config(Map<String, Object> overrides) {
        var base = new HashMap<String, Object>();
        base.put("key_source", "CONFIG");
        base.put("cipher_algorithm", "TINK/AES_GCM");
        base.put("cipher_data_key_identifier", "default-key");
        base.put("dynamic_key_id_prefix", "__#");
        base.putAll(overrides);
        return MAPPER.convertValue(base, KryptoniteFilterConfig.class);
    }

    @Nested
    @DisplayName("JsonObjectNodeAccessor")
    class JsonAccessor {

        @Test
        @DisplayName("returns static field-level key id unchanged")
        void returnsStaticFieldLevelKeyId() {
            var cfg = config(Map.of("cipher_data_key_identifier", "default-key"));
            var fc = FieldConfig.builder().name("age").keyId("explicit-key").build();
            var accessor = JsonObjectNodeAccessor.from("""
                    {
                      "customer": {
                        "country": "de"
                      },
                      "age": 30
                    }
                    """.getBytes());

            assertThat(DynamicKeyIdResolver.resolve(fc, cfg, accessor)).isEqualTo("explicit-key");
        }

        @Test
        @DisplayName("resolves dynamic default key id from nested field path")
        void resolvesDynamicDefaultKeyId() {
            var cfg = config(Map.of("cipher_data_key_identifier", "__#customer.country"));
            var fc = FieldConfig.builder().name("age").build();
            var accessor = JsonObjectNodeAccessor.from("""
                    {
                      "customer": {
                        "country": "de"
                      },
                      "age": 30
                    }
                    """.getBytes());

            assertThat(DynamicKeyIdResolver.resolve(fc, cfg, accessor)).isEqualTo("de");
        }

        @Test
        @DisplayName("resolves dynamic field-level key id from nested field path")
        void resolvesDynamicFieldLevelKeyId() {
            var cfg = config(Map.of());
            var fc = FieldConfig.builder().name("age").keyId("__#customer.country").build();
            var accessor = JsonObjectNodeAccessor.from("""
                    {
                      "customer": {
                        "country": "de"
                      },
                      "age": 30
                    }
                    """.getBytes());

            assertThat(DynamicKeyIdResolver.resolve(fc, cfg, accessor)).isEqualTo("de");
        }

        @Test
        @DisplayName("resolves dynamic KMS envelope fallback from envelope_kek_identifier")
        void resolvesDynamicKmsEnvelopeFallbackFromEnvelopeKekIdentifier() {
            var cfg = config(Map.of(
                    "cipher_algorithm", "TINK/AES_GCM_ENVELOPE_KMS",
                    "cipher_data_key_identifier", "data-key",
                    "envelope_kek_identifier", "__#customer.country"));
            var fc = FieldConfig.builder().name("age").build();
            var accessor = JsonObjectNodeAccessor.from("""
                    {
                      "customer": {
                        "country": "de"
                      },
                      "age": 30
                    }
                    """.getBytes());

            assertThat(DynamicKeyIdResolver.resolve(fc, cfg, accessor)).isEqualTo("de");
        }

        @Test
        @DisplayName("prefers field-level keyId over envelope_kek_identifier for KMS envelope")
        void prefersFieldLevelKeyIdOverEnvelopeKekIdentifierForKmsEnvelope() {
            var cfg = config(Map.of(
                    "cipher_algorithm", "TINK/AES_GCM_ENVELOPE_KMS",
                    "envelope_kek_identifier", "__#customer.country"));
            var fc = FieldConfig.builder().name("age").keyId("__#customer.region").build();
            var accessor = JsonObjectNodeAccessor.from("""
                    {
                      "customer": {
                        "country": "de",
                        "region": "eu"
                      },
                      "age": 30
                    }
                    """.getBytes());

            assertThat(DynamicKeyIdResolver.resolve(fc, cfg, accessor)).isEqualTo("eu");
        }

        @Test
        @DisplayName("throws when dynamic field path is missing")
        void throwsWhenDynamicFieldPathMissing() {
            var cfg = config(Map.of("cipher_data_key_identifier", "__#customer.country"));
            var fc = FieldConfig.builder().name("age").build();
            var accessor = JsonObjectNodeAccessor.from("""
                    {
                      "age": 30
                    }
                    """.getBytes());

            assertThatThrownBy(() -> DynamicKeyIdResolver.resolve(fc, cfg, accessor))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("customer.country");
        }

        @Test
        @DisplayName("throws when resolved value is not textual")
        void throwsWhenResolvedValueIsNotTextual() {
            var cfg = config(Map.of("cipher_data_key_identifier", "__#customer.id"));
            var fc = FieldConfig.builder().name("age").build();
            var accessor = JsonObjectNodeAccessor.from("""
                    {
                      "customer": {
                        "id": 123
                      },
                      "age": 30
                    }
                    """.getBytes());

            assertThatThrownBy(() -> DynamicKeyIdResolver.resolve(fc, cfg, accessor))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("textual");
        }

        @Test
        @DisplayName("throws when resolved value is blank")
        void throwsWhenResolvedValueIsBlank() {
            var cfg = config(Map.of("cipher_data_key_identifier", "__#customer.country"));
            var fc = FieldConfig.builder().name("age").build();
            var accessor = JsonObjectNodeAccessor.from("""
                    {
                      "customer": {
                        "country": "   "
                      },
                      "age": 30
                    }
                    """.getBytes());

            assertThatThrownBy(() -> DynamicKeyIdResolver.resolve(fc, cfg, accessor))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("blank string");
        }

        @Test
        @DisplayName("throws when dynamic expression has no suffix after prefix")
        void throwsWhenDynamicExpressionHasNoSuffix() {
            var cfg = config(Map.of("cipher_data_key_identifier", "__#"));
            var fc = FieldConfig.builder().name("age").build();
            var accessor = JsonObjectNodeAccessor.from("""
                    {
                      "age": 30
                    }
                    """.getBytes());

            assertThatThrownBy(() -> DynamicKeyIdResolver.resolve(fc, cfg, accessor))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("has no field path");
        }
    }

    @Nested
    @DisplayName("AvroGenericRecordAccessor")
    class AvroAccessor {

        private Schema customerSchema() {
            return SchemaBuilder.record("Customer").namespace("test").fields()
                    .name("country").type().stringType().noDefault()
                    .name("region").type().stringType().noDefault()
                    .name("id").type().intType().noDefault()
                    .endRecord();
        }

        private Schema rootSchema() {
            return SchemaBuilder.record("Order").namespace("test").fields()
                    .name("customer").type(customerSchema()).noDefault()
                    .name("age").type().intType().noDefault()
                    .endRecord();
        }

        private AvroGenericRecordAccessor accessor(String country, String region, Integer id) {
            Schema customerSchema = customerSchema();
            Schema rootSchema = rootSchema();
            GenericRecord customer = new GenericData.Record(customerSchema);
            customer.put("country", country != null ? new Utf8(country) : null);
            customer.put("region", region != null ? new Utf8(region) : null);
            customer.put("id", id);
            GenericRecord root = new GenericData.Record(rootSchema);
            root.put("customer", customer);
            root.put("age", 30);
            return AvroGenericRecordAccessor.of(root, rootSchema);
        }

        private AvroGenericRecordAccessor accessorWithoutCustomer() {
            Schema rootSchema = rootSchema();
            GenericRecord root = new GenericData.Record(rootSchema);
            root.put("customer", null);
            root.put("age", 30);
            return AvroGenericRecordAccessor.of(root, rootSchema);
        }

        @Test
        @DisplayName("returns static field-level key id unchanged")
        void returnsStaticFieldLevelKeyId() {
            var cfg = config(Map.of("cipher_data_key_identifier", "default-key"));
            var fc = FieldConfig.builder().name("age").keyId("explicit-key").build();

            assertThat(DynamicKeyIdResolver.resolve(fc, cfg, accessor("de", "eu", 123))).isEqualTo("explicit-key");
        }

        @Test
        @DisplayName("resolves dynamic default key id from nested field path")
        void resolvesDynamicDefaultKeyId() {
            var cfg = config(Map.of("cipher_data_key_identifier", "__#customer.country"));
            var fc = FieldConfig.builder().name("age").build();

            assertThat(DynamicKeyIdResolver.resolve(fc, cfg, accessor("de", "eu", 123))).isEqualTo("de");
        }

        @Test
        @DisplayName("resolves dynamic field-level key id from nested field path")
        void resolvesDynamicFieldLevelKeyId() {
            var cfg = config(Map.of());
            var fc = FieldConfig.builder().name("age").keyId("__#customer.country").build();

            assertThat(DynamicKeyIdResolver.resolve(fc, cfg, accessor("de", "eu", 123))).isEqualTo("de");
        }

        @Test
        @DisplayName("resolves dynamic KMS envelope fallback from envelope_kek_identifier")
        void resolvesDynamicKmsEnvelopeFallbackFromEnvelopeKekIdentifier() {
            var cfg = config(Map.of(
                    "cipher_algorithm", "TINK/AES_GCM_ENVELOPE_KMS",
                    "cipher_data_key_identifier", "data-key",
                    "envelope_kek_identifier", "__#customer.country"));
            var fc = FieldConfig.builder().name("age").build();

            assertThat(DynamicKeyIdResolver.resolve(fc, cfg, accessor("de", "eu", 123))).isEqualTo("de");
        }

        @Test
        @DisplayName("prefers field-level keyId over envelope_kek_identifier for KMS envelope")
        void prefersFieldLevelKeyIdOverEnvelopeKekIdentifierForKmsEnvelope() {
            var cfg = config(Map.of(
                    "cipher_algorithm", "TINK/AES_GCM_ENVELOPE_KMS",
                    "envelope_kek_identifier", "__#customer.country"));
            var fc = FieldConfig.builder().name("age").keyId("__#customer.region").build();

            assertThat(DynamicKeyIdResolver.resolve(fc, cfg, accessor("de", "eu", 123))).isEqualTo("eu");
        }

        @Test
        @DisplayName("throws when dynamic field path is missing")
        void throwsWhenDynamicFieldPathMissing() {
            var cfg = config(Map.of("cipher_data_key_identifier", "__#customer.country"));
            var fc = FieldConfig.builder().name("age").build();

            assertThatThrownBy(() -> DynamicKeyIdResolver.resolve(fc, cfg, accessorWithoutCustomer()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("customer.country");
        }

        @Test
        @DisplayName("throws when resolved value is not textual")
        void throwsWhenResolvedValueIsNotTextual() {
            var cfg = config(Map.of("cipher_data_key_identifier", "__#customer.id"));
            var fc = FieldConfig.builder().name("age").build();

            assertThatThrownBy(() -> DynamicKeyIdResolver.resolve(fc, cfg, accessor("de", "eu", 123)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("string value is required");
        }

        @Test
        @DisplayName("throws when resolved value is blank")
        void throwsWhenResolvedValueIsBlank() {
            var cfg = config(Map.of("cipher_data_key_identifier", "__#customer.country"));
            var fc = FieldConfig.builder().name("age").build();

            assertThatThrownBy(() -> DynamicKeyIdResolver.resolve(fc, cfg, accessor("   ", "eu", 123)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("blank string");
        }

        @Test
        @DisplayName("throws when dynamic expression has no suffix after prefix")
        void throwsWhenDynamicExpressionHasNoSuffix() {
            var cfg = config(Map.of("cipher_data_key_identifier", "__#"));
            var fc = FieldConfig.builder().name("age").build();

            assertThatThrownBy(() -> DynamicKeyIdResolver.resolve(fc, cfg, accessor("de", "eu", 123)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("has no field path");
        }
    }
}
