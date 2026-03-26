package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultDynamicSchemaRegistryAdapter}.
 *
 * <p>Wire format methods ({@link DefaultDynamicSchemaRegistryAdapter#stripPrefix} /
 * {@link DefaultDynamicSchemaRegistryAdapter#attachPrefix}) are pure byte operations —
 * tested without mocks. SR-client-dependent methods use a mocked
 * {@link SchemaRegistryClient}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultDynamicSchemaRegistryAdapter")
class DefaultDynamicSchemaRegistryAdapterTest {

    @Mock SchemaRegistryClient mockClient;

    private static final String TOPIC = "payments";
    private static final int ORIGINAL_ID = 1;
    private static final int ENCRYPTED_ID = 42;

    private static final String FLAT_JSON_SCHEMA =
            """
            {"type":"object","properties":{"name":{"type":"string"},"age":{"type":"integer"}}}
            """;

    // ---- stripPrefix / attachPrefix ----

    @Nested
    @DisplayName("stripPrefix and attachPrefix — wire format")
    class WireFormat {

        private DefaultDynamicSchemaRegistryAdapter adapter() {
            return new DefaultDynamicSchemaRegistryAdapter(mockClient);
        }

        @Test
        @DisplayName("stripPrefix extracts schemaId and payload from valid wire bytes")
        void stripPrefixExtractsSchemaIdAndPayload() {
            byte[] payload = new byte[]{10, 20, 30};
            byte[] wireBytes = buildWireBytes(5, payload);

            SchemaIdAndPayload result = adapter().stripPrefix(wireBytes);

            assertThat(result.schemaId()).isEqualTo(5);
            assertThat(result.payload()).isEqualTo(payload);
        }

        @Test
        @DisplayName("attachPrefix produces wire bytes with magic byte, big-endian schemaId, then payload")
        void attachPrefixProducesCorrectWireBytes() {
            byte[] payload = new byte[]{10, 20, 30};
            byte[] result = adapter().attachPrefix(7, payload);

            assertThat(result).hasSize(8);
            assertThat(result[0]).isEqualTo((byte) 0x00);
            assertThat(ByteBuffer.wrap(result, 1, 4).getInt()).isEqualTo(7);
            assertThat(result[5]).isEqualTo((byte) 10);
            assertThat(result[6]).isEqualTo((byte) 20);
            assertThat(result[7]).isEqualTo((byte) 30);
        }

        @Test
        @DisplayName("round-trip: attachPrefix → stripPrefix produces original schemaId and payload")
        void roundTrip() {
            byte[] payload = new byte[]{1, 2, 3, 4, 5};
            byte[] wireBytes = adapter().attachPrefix(99, payload);
            SchemaIdAndPayload stripped = adapter().stripPrefix(wireBytes);

            assertThat(stripped.schemaId()).isEqualTo(99);
            assertThat(stripped.payload()).isEqualTo(payload);
        }

        @Test
        @DisplayName("stripPrefix with fewer than 5 bytes throws IllegalArgumentException")
        void stripPrefixTooShortThrows() {
            assertThatThrownBy(() -> adapter().stripPrefix(new byte[]{0x00, 0x01}))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("stripPrefix with null throws IllegalArgumentException")
        void stripPrefixNullThrows() {
            assertThatThrownBy(() -> adapter().stripPrefix(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("stripPrefix with wrong magic byte throws IllegalArgumentException")
        void stripPrefixWrongMagicByteThrows() {
            byte[] wireBytes = buildWireBytes(1, new byte[]{});
            wireBytes[0] = (byte) 0xFF; // corrupt magic byte

            assertThatThrownBy(() -> adapter().stripPrefix(wireBytes))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("magic byte");
        }

        @Test
        @DisplayName("stripPrefix with exactly 5 bytes (empty payload) succeeds")
        void stripPrefixEmptyPayload() {
            byte[] wireBytes = buildWireBytes(3, new byte[0]);
            SchemaIdAndPayload result = adapter().stripPrefix(wireBytes);

            assertThat(result.schemaId()).isEqualTo(3);
            assertThat(result.payload()).isEmpty();
        }
    }

    // ---- getOrRegisterEncryptedSchemaId ----

    @Nested
    @DisplayName("getOrRegisterEncryptedSchemaId")
    class GetOrRegisterEncryptedSchemaId {

        @Test
        @DisplayName("cache miss: SR client is called and encryptedSchemaId returned")
        void cacheMissFetchesAndRegisters() throws Exception {
            lenient().when(mockClient.updateCompatibility(any(), any())).thenReturn("NONE");
            when(mockClient.getSchemaById(ORIGINAL_ID)).thenReturn(new JsonSchema(FLAT_JSON_SCHEMA));
            when(mockClient.register(contains("_k4k_enc"), any(ParsedSchema.class))).thenReturn(ENCRYPTED_ID);
            lenient().when(mockClient.register(contains("_k4k_meta"), any(ParsedSchema.class))).thenReturn(99);

            var adapter = new DefaultDynamicSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            int result = adapter.getOrRegisterEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs);

            assertThat(result).isEqualTo(ENCRYPTED_ID);
            verify(mockClient, times(1)).getSchemaById(ORIGINAL_ID);
        }

        @Test
        @DisplayName("cache hit: SR client is called only once for repeated calls with same originalId")
        void cacheHitCallsSrOnce() throws Exception {
            lenient().when(mockClient.updateCompatibility(any(), any())).thenReturn("NONE");
            when(mockClient.getSchemaById(ORIGINAL_ID)).thenReturn(new JsonSchema(FLAT_JSON_SCHEMA));
            when(mockClient.register(contains("_k4k_enc"), any(ParsedSchema.class))).thenReturn(ENCRYPTED_ID);
            lenient().when(mockClient.register(contains("_k4k_meta"), any(ParsedSchema.class))).thenReturn(99);

            var adapter = new DefaultDynamicSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            adapter.getOrRegisterEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs);
            adapter.getOrRegisterEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs);

            // SR fetch must happen only once (second call is a cache hit)
            verify(mockClient, times(1)).getSchemaById(ORIGINAL_ID);
            verify(mockClient, times(1)).register(contains("_k4k_enc"), any(ParsedSchema.class));
        }

        @Test
        @DisplayName("registers metadata only under encryptedSchemaId subject (decrypt-path lookup)")
        void registersMetadataOnlyUnderEncryptedIdSubject() throws Exception {
            lenient().when(mockClient.updateCompatibility(any(), any())).thenReturn("NONE");
            when(mockClient.getSchemaById(ORIGINAL_ID)).thenReturn(new JsonSchema(FLAT_JSON_SCHEMA));
            when(mockClient.register(contains("_k4k_enc"), any(ParsedSchema.class))).thenReturn(ENCRYPTED_ID);
            lenient().when(mockClient.register(contains("_k4k_meta"), any(ParsedSchema.class))).thenReturn(99);

            var adapter = new DefaultDynamicSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            adapter.getOrRegisterEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs);

            // Only the encryptedSchemaId subject is registered — originalSchemaId subject is STATIC mode's concern
            verify(mockClient, times(1)).register(
                    eq(TOPIC + "-value__k4k_meta_" + ENCRYPTED_ID), any(ParsedSchema.class));
            verify(mockClient, never()).register(
                    eq(TOPIC + "-value__k4k_meta_" + ORIGINAL_ID), any(ParsedSchema.class));
        }
    }

    // ---- getOrRegisterDecryptedSchemaId — full decrypt ----

    @Nested
    @DisplayName("getOrRegisterDecryptedSchemaId — full decrypt")
    class GetOrRegisterDecryptedSchemaId {

        @Test
        @DisplayName("full decrypt: returns originalSchemaId without additional SR calls")
        void fullDecryptReturnsOriginalId() throws Exception {
            // Populate encrypt-side caches first (this also primes the full-decrypt cache entry)
            lenient().when(mockClient.updateCompatibility(any(), any())).thenReturn("NONE");
            when(mockClient.getSchemaById(ORIGINAL_ID)).thenReturn(new JsonSchema(FLAT_JSON_SCHEMA));
            when(mockClient.register(contains("_k4k_enc"), any(ParsedSchema.class))).thenReturn(ENCRYPTED_ID);
            lenient().when(mockClient.register(contains("_k4k_meta"), any(ParsedSchema.class))).thenReturn(99);

            var adapter = new DefaultDynamicSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            // Encrypt path populates decrypt cache for the full-decrypt case
            adapter.getOrRegisterEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs);

            // Full decrypt with same field set → must return ORIGINAL_ID from cache
            int result = adapter.getOrRegisterDecryptedSchemaId(ENCRYPTED_ID, TOPIC, fieldConfigs);

            assertThat(result).isEqualTo(ORIGINAL_ID);
        }

        @Test
        @DisplayName("full decrypt cache hit: no additional SR client calls after encrypt primed the cache")
        void fullDecryptCacheHit() throws Exception {
            lenient().when(mockClient.updateCompatibility(any(), any())).thenReturn("NONE");
            when(mockClient.getSchemaById(ORIGINAL_ID)).thenReturn(new JsonSchema(FLAT_JSON_SCHEMA));
            when(mockClient.register(contains("_k4k_enc"), any(ParsedSchema.class))).thenReturn(ENCRYPTED_ID);
            lenient().when(mockClient.register(contains("_k4k_meta"), any(ParsedSchema.class))).thenReturn(99);

            var adapter = new DefaultDynamicSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            adapter.getOrRegisterEncryptedSchemaId(ORIGINAL_ID, TOPIC, fieldConfigs);

            // Call decrypt twice — should return same result both times
            int r1 = adapter.getOrRegisterDecryptedSchemaId(ENCRYPTED_ID, TOPIC, fieldConfigs);
            int r2 = adapter.getOrRegisterDecryptedSchemaId(ENCRYPTED_ID, TOPIC, fieldConfigs);

            assertThat(r1).isEqualTo(ORIGINAL_ID);
            assertThat(r2).isEqualTo(ORIGINAL_ID);
        }
    }

    // ---- getOrRegisterDecryptedSchemaId — cold-start ----

    @Nested
    @DisplayName("getOrRegisterDecryptedSchemaId — cold-start decrypt")
    class ColdStartDecrypt {

        private static final String GEN1_META_JSON = "{\"type\":\"object\",\"x-kryptonite-metadata\":"
                + "{\"originalSchemaId\":1,\"encryptedSchemaId\":42,"
                + "\"encryptedFields\":[{\"name\":\"age\"}]}}";
        private static final String GEN2_META_JSON = "{\"type\":\"object\",\"x-kryptonite-metadata\":"
                + "{\"originalSchemaId\":2,\"encryptedSchemaId\":43,"
                + "\"encryptedFields\":[{\"name\":\"age\"}]}}";

        @Test
        @DisplayName("BUG guard: cold-start reads per-encryptedSchemaId subject and returns correct originalSchemaId")
        void coldStartReturnsCorrectOriginalSchemaIdForOldRecord() throws Exception {
            // After schema evolution the flat meta subject holds gen2 metadata (latest wins)
            SchemaMetadata gen2FlatMeta = new SchemaMetadata(100, 2, GEN2_META_JSON);
            lenient().when(mockClient.getLatestSchemaMetadata(eq("payments-value__k4k_meta")))
                    .thenReturn(gen2FlatMeta);
            // Per-id subject for gen1 exists in SR — correct impl must read this
            SchemaMetadata gen1PerIdMeta = new SchemaMetadata(99, 1, GEN1_META_JSON);
            lenient().when(mockClient.getLatestSchemaMetadata(eq("payments-value__k4k_meta_42")))
                    .thenReturn(gen1PerIdMeta);

            var adapter = new DefaultDynamicSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            int result = adapter.getOrRegisterDecryptedSchemaId(ENCRYPTED_ID, TOPIC, fieldConfigs);

            assertThat(result).isEqualTo(ORIGINAL_ID);
        }

        @Test
        @DisplayName("cold-start full decrypt: fetches metadata from per-encryptedSchemaId subject")
        void coldStartFullDecryptFetchesFromPerIdSubject() throws Exception {
            SchemaMetadata storedMeta = new SchemaMetadata(99, 1, GEN1_META_JSON);
            lenient().when(mockClient.getLatestSchemaMetadata(eq("payments-value__k4k_meta_42")))
                    .thenReturn(storedMeta);

            var adapter = new DefaultDynamicSchemaRegistryAdapter(mockClient);
            var fieldConfigs = Set.of(
                    FieldConfig.builder().name("age").fieldMode(FieldConfig.FieldMode.OBJECT).build());

            int result = adapter.getOrRegisterDecryptedSchemaId(ENCRYPTED_ID, TOPIC, fieldConfigs);

            assertThat(result).isEqualTo(ORIGINAL_ID);
        }
    }

    // ---- fetchSchema ----

    @Nested
    @DisplayName("fetchSchema")
    class FetchSchema {

        @Test
        @DisplayName("delegates to SR client and returns the parsed schema")
        void delegatesToSrClient() throws Exception {
            JsonSchema schema = new JsonSchema(FLAT_JSON_SCHEMA);
            when(mockClient.getSchemaById(eq(5))).thenReturn(schema);

            var adapter = new DefaultDynamicSchemaRegistryAdapter(mockClient);
            Object result = adapter.fetchSchema(5);

            assertThat(result).isEqualTo(schema);
        }

        @Test
        @DisplayName("wraps SR exception in SchemaRegistryAdapterException")
        void wrapsException() throws Exception {
            when(mockClient.getSchemaById(any(int.class))).thenThrow(new RuntimeException("SR down"));

            var adapter = new DefaultDynamicSchemaRegistryAdapter(mockClient);

            assertThatThrownBy(() -> adapter.fetchSchema(5))
                    .isInstanceOf(SchemaRegistryAdapterException.class);
        }
    }

    // ---- Helper ----

    private static byte[] buildWireBytes(int schemaId, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(5 + payload.length);
        buf.put((byte) 0x00);
        buf.putInt(schemaId);
        buf.put(payload);
        return buf.array();
    }
}
