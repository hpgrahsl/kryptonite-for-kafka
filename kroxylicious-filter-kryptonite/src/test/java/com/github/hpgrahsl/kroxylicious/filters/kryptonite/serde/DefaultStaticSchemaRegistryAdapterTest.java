package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.confluent.kafka.schemaregistry.ParsedSchema;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultStaticSchemaRegistryAdapter}.
 *
 * <p>STATIC mode: no {@code register} or {@code updateCompatibility} calls are ever made.
 * All schema lookups are read-only SR calls against pre-registered subjects.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultStaticSchemaRegistryAdapter")
class DefaultStaticSchemaRegistryAdapterTest {

    @Mock SchemaRegistryClient mockClient;

    private static final String TOPIC = "payments";
    private static final int ORIGINAL_ID = 1;
    private static final int ENCRYPTED_ID = 42;
    private static final int PARTIAL_DECRYPT_ID = 77;

    private static final String META_JSON = "{\"type\":\"object\",\"x-kryptonite-metadata\":"
            + "{\"originalSchemaId\":1,\"encryptedSchemaId\":42,"
            + "\"encryptedFields\":[{\"name\":\"age\"}]}}";

    // ---- resolveEncryptedSchemaId ----

    @Nested
    @DisplayName("resolveEncryptedSchemaId")
    class GetOrRegisterEncryptedSchemaId {

        @Test
        @DisplayName("reads encryptedSchemaId from pre-registered __k4k_meta_<originalSchemaId> subject")
        void readsEncryptedIdFromOriginalIdSubject() throws Exception {
            SchemaMetadata meta = new SchemaMetadata(99, 1, META_JSON);
            when(mockClient.getLatestSchemaMetadata(eq("payments-value__k4k_meta_1")))
                    .thenReturn(meta);

            var adapter = new DefaultStaticSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            int result = adapter.resolveEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs);

            assertThat(result).isEqualTo(ENCRYPTED_ID);
        }

        @Test
        @DisplayName("cache hit: SR is called only once for repeated calls with same originalSchemaId")
        void cacheHitCallsSrOnce() throws Exception {
            SchemaMetadata meta = new SchemaMetadata(99, 1, META_JSON);
            when(mockClient.getLatestSchemaMetadata(eq("payments-value__k4k_meta_1")))
                    .thenReturn(meta);

            var adapter = new DefaultStaticSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            adapter.resolveEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs);
            int result = adapter.resolveEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs);

            assertThat(result).isEqualTo(ENCRYPTED_ID);
            verify(mockClient, times(1)).getLatestSchemaMetadata("payments-value__k4k_meta_1");
        }

        @Test
        @DisplayName("missing pre-registration throws SchemaRegistryAdapterException with descriptive message")
        void missingPreRegistrationThrows() throws Exception {
            when(mockClient.getLatestSchemaMetadata(eq("payments-value__k4k_meta_1")))
                    .thenThrow(new RuntimeException("subject not found"));

            var adapter = new DefaultStaticSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            assertThatThrownBy(() -> adapter.resolveEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs))
                    .isInstanceOf(SchemaRegistryAdapterException.class)
                    .hasMessageContaining("STATIC mode")
                    .hasMessageContaining("originalSchemaId=1")
                    .hasMessageContaining("pre-register");
        }

        @Test
        @DisplayName("never calls register or updateCompatibility")
        void neverRegisters() throws Exception {
            SchemaMetadata meta = new SchemaMetadata(99, 1, META_JSON);
            when(mockClient.getLatestSchemaMetadata(eq("payments-value__k4k_meta_1")))
                    .thenReturn(meta);

            var adapter = new DefaultStaticSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            adapter.resolveEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs);

            verify(mockClient, never()).register(anyString(), any(ParsedSchema.class));
            verify(mockClient, never()).updateCompatibility(anyString(), anyString());
        }
    }

    // ---- resolveDecryptedSchemaId — full decrypt ----

    @Nested
    @DisplayName("resolveDecryptedSchemaId — full decrypt")
    class FullDecrypt {

        @Test
        @DisplayName("full decrypt returns originalSchemaId via decrypt metadata subject")
        void fullDecryptReturnsOriginalSchemaId() throws Exception {
            SchemaMetadata meta = new SchemaMetadata(99, 1, META_JSON);
            when(mockClient.getLatestSchemaMetadata(eq("payments-value__k4k_meta_42")))
                    .thenReturn(meta);

            var adapter = new DefaultStaticSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            int result = adapter.resolveDecryptedSchemaId(ENCRYPTED_ID, TOPIC, fieldConfigs);

            assertThat(result).isEqualTo(ORIGINAL_ID);
        }

        @Test
        @DisplayName("full decrypt cache hit: primed via resolveEncryptedSchemaId, no extra SR calls on decrypt")
        void fullDecryptCacheHitWhenEncryptPathPrimed() throws Exception {
            SchemaMetadata meta = new SchemaMetadata(99, 1, META_JSON);
            when(mockClient.getLatestSchemaMetadata(eq("payments-value__k4k_meta_1")))
                    .thenReturn(meta);

            var adapter = new DefaultStaticSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            // Prime caches via encrypt path
            adapter.resolveEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs);

            // Decrypt path must be served from cache — no additional SR call for __k4k_meta_42
            int result = adapter.resolveDecryptedSchemaId(ENCRYPTED_ID, TOPIC, fieldConfigs);

            assertThat(result).isEqualTo(ORIGINAL_ID);
            verify(mockClient, never()).getLatestSchemaMetadata("payments-value__k4k_meta_42");
        }
    }

    // ---- resolveDecryptedSchemaId — partial decrypt ----

    @Nested
    @DisplayName("resolveDecryptedSchemaId — partial decrypt")
    class PartialDecrypt {

        // Two-field schema so partial decrypt leaves one field encrypted
        private static final String TWO_FIELD_META_JSON = "{\"type\":\"object\",\"x-kryptonite-metadata\":"
                + "{\"originalSchemaId\":1,\"encryptedSchemaId\":42,"
                + "\"encryptedFields\":[{\"name\":\"age\"},{\"name\":\"salary\"}]}}";

        @Test
        @DisplayName("partial decrypt reads pre-registered __k4k_dec_<hash> subject")
        void partialDecryptReadsPreRegisteredSubject() throws Exception {
            SchemaMetadata encMeta = new SchemaMetadata(99, 1, TWO_FIELD_META_JSON);
            when(mockClient.getLatestSchemaMetadata(eq("payments-value__k4k_meta_42")))
                    .thenReturn(encMeta);
            // Partial-decrypt subject for decrypting only "age"
            SchemaMetadata partialMeta = new SchemaMetadata(PARTIAL_DECRYPT_ID, 1, "{\"type\":\"object\"}");
            lenient().when(mockClient.getLatestSchemaMetadata(org.mockito.ArgumentMatchers.contains("_k4k_dec_")))
                    .thenReturn(partialMeta);

            var adapter = new DefaultStaticSchemaRegistryAdapter(mockClient);
            // Only decrypt "age", leaving "salary" encrypted
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            int result = adapter.resolveDecryptedSchemaId(ENCRYPTED_ID, TOPIC, fieldConfigs);

            assertThat(result).isEqualTo(PARTIAL_DECRYPT_ID);
        }

        @Test
        @DisplayName("partial decrypt throws SchemaRegistryAdapterException if subject not pre-registered")
        void partialDecryptThrowsIfNotPreRegistered() throws Exception {
            SchemaMetadata encMeta = new SchemaMetadata(99, 1, TWO_FIELD_META_JSON);
            when(mockClient.getLatestSchemaMetadata(eq("payments-value__k4k_meta_42")))
                    .thenReturn(encMeta);
            when(mockClient.getLatestSchemaMetadata(org.mockito.ArgumentMatchers.contains("_k4k_dec_")))
                    .thenThrow(new RuntimeException("subject not found"));

            var adapter = new DefaultStaticSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            assertThatThrownBy(() -> adapter.resolveDecryptedSchemaId(ENCRYPTED_ID, TOPIC, fieldConfigs))
                    .isInstanceOf(SchemaRegistryAdapterException.class)
                    .hasMessageContaining("STATIC mode")
                    .hasMessageContaining("partial-decrypt")
                    .hasMessageContaining("pre-register");
        }

        @Test
        @DisplayName("partial decrypt never calls register or updateCompatibility")
        void neverRegisters() throws Exception {
            SchemaMetadata encMeta = new SchemaMetadata(99, 1, TWO_FIELD_META_JSON);
            when(mockClient.getLatestSchemaMetadata(eq("payments-value__k4k_meta_42")))
                    .thenReturn(encMeta);
            SchemaMetadata partialMeta = new SchemaMetadata(PARTIAL_DECRYPT_ID, 1, "{\"type\":\"object\"}");
            lenient().when(mockClient.getLatestSchemaMetadata(org.mockito.ArgumentMatchers.contains("_k4k_dec_")))
                    .thenReturn(partialMeta);

            var adapter = new DefaultStaticSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            adapter.resolveDecryptedSchemaId(ENCRYPTED_ID, TOPIC, fieldConfigs);

            verify(mockClient, never()).register(anyString(), any(ParsedSchema.class));
            verify(mockClient, never()).updateCompatibility(anyString(), anyString());
        }
    }

    // ---- fetchSchema ----

    @Nested
    @DisplayName("fetchSchema")
    class FetchSchema {

        @Test
        @DisplayName("delegates to SR client and returns the parsed schema")
        void delegatesToSrClient() throws Exception {
            JsonSchema schema = new JsonSchema("{\"type\":\"object\"}");
            when(mockClient.getSchemaById(eq(5))).thenReturn(schema);

            var adapter = new DefaultStaticSchemaRegistryAdapter(mockClient);
            Object result = adapter.fetchSchema(5);

            assertThat(result).isEqualTo(schema);
        }

        @Test
        @DisplayName("wraps SR exception in SchemaRegistryAdapterException")
        void wrapsException() throws Exception {
            when(mockClient.getSchemaById(any(int.class))).thenThrow(new RuntimeException("SR down"));

            var adapter = new DefaultStaticSchemaRegistryAdapter(mockClient);

            assertThatThrownBy(() -> adapter.fetchSchema(5))
                    .isInstanceOf(SchemaRegistryAdapterException.class);
        }
    }
}
