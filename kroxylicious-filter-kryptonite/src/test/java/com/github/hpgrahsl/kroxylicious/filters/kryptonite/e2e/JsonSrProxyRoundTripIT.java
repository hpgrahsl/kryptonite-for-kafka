package com.github.hpgrahsl.kroxylicious.filters.kryptonite.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * End-to-end roundtrip tests for the Kroxylicious Kryptonite filter (JSON_SR
 * format).
 *
 * <p>
 * Container topology (managed by {@link AbstractKroxyliciousBaseIT}):
 * Kafka + Schema Registry + Kroxylicious proxy — all in Docker via
 * Testcontainers.
 *
 * <p>
 * Test payload schema ({@code $schema: draft-07}):
 * 
 * <pre>
 * {
 *   "firstname": string   — OBJECT mode encryption (string)
 *   "lastname":  string   — OBJECT mode encryption (string)
 *   "age":       integer  — OBJECT mode encryption (non-string primitive)
 *   "address":   object   — OBJECT mode encryption (entire subdocument as one blob)
 *   "tags":      string[] — ELEMENT mode encryption (each array element individually)
 *   "scores":    {k:int}  — ELEMENT mode encryption (each map value individually)
 * }
 * </pre>
 *
 * <p>
 * Activate with: {@code -De2e.tests=true}
 */
@Testcontainers
@SuppressWarnings("resource")
class JsonSrProxyRoundTripIT extends AbstractKroxyliciousBaseIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PERSON_SCHEMA = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "firstname": { "type": "string" },
                "lastname":  { "type": "string" },
                "age":       { "type": "integer" },
                "address": {
                  "type": "object",
                  "properties": {
                    "street": { "type": "string" },
                    "city":   { "type": "string" }
                  }
                },
                "tags":   { "type": "array",  "items": { "type": "string" } },
                "scores": { "type": "object", "additionalProperties": { "type": "integer" } }
              }
            }
            """;

    private static final String PAYLOAD = """
            {
              "firstname": "Alice",
              "lastname":  "Smith",
              "age":       30,
              "address":   { "street": "123 Main St", "city": "Springfield" },
              "tags":      ["admin", "user", "readonly"],
              "scores":    { "math": 95, "science": 87 }
            }
            """;

    @Container
    protected static final GenericContainer<?> KROXYLICIOUS = new GenericContainer<>(
            DockerImageName.parse(KROXYLICIOUS_IMAGE))
            .withNetwork(NETWORK)
            .withNetworkAliases("kroxylicious")
            .dependsOn(KAFKA, SCHEMA_REGISTRY)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(
                            "/Users/hpgrahsl/github/hpgrahsl/kryptonite-for-kafka/kroxylicious-filter-kryptonite/target/kroxylicious-filter-kryptonite-0.1.0-SNAPSHOT.jar"),
                    "/opt/kroxylicious/plugins/kroxylicious-filter-kryptonite.jar")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("e2e-proxy-config-jsonsr.yaml"),
                    "/opt/kroxylicious/config/e2e-config.yaml")
            .withEnv("KROXYLICIOUS_CLASSPATH", "/opt/kroxylicious/plugins/*")
            .withEnv("JAVA_OPTIONS",
                    "--add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED")
            .withCreateContainerCmdModifier(cmd -> {
                HostConfig hc = cmd.getHostConfig() != null ? cmd.getHostConfig()
                        : HostConfig.newHostConfig();
                Ports portBindings = new Ports();
                portBindings.bind(ExposedPort.tcp(PROXY_BOOTSTRAP_PORT),
                        Ports.Binding.bindPort(PROXY_BOOTSTRAP_PORT));
                portBindings.bind(ExposedPort.tcp(PROXY_BROKER_PORT),
                        Ports.Binding.bindPort(PROXY_BROKER_PORT));
                portBindings.bind(ExposedPort.tcp(PROXY_BROKER_PORT + 1),
                        Ports.Binding.bindPort(PROXY_BROKER_PORT + 1));
                cmd.withHostConfig(hc.withPortBindings(portBindings));
            })
            // .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("e2e.kroxylicious")))
            .withCommand("--config", "/opt/kroxylicious/config/e2e-config.yaml")
            .waitingFor(Wait.forLogMessage(".*Kroxylicious is started.*\\n", 1)
                    .withStartupTimeout(Duration.ofSeconds(30)));

    /**
     * Full proxy roundtrip for OBJECT-mode fields.
     *
     * <p>
     * Produces the payload through the proxy (encrypt), consumes through the proxy
     * (decrypt),
     * and asserts that {@code firstname}, {@code lastname}, {@code age}, and
     * {@code address}
     * are restored to their original values.
     */
    @Test
    void objectModeRoundTrip() throws Exception {
        String topic = "e2e-jsonsr-" + UUID.randomUUID();
        createTopic(topic);

        int schemaId;
        try (SchemaRegistryClient srClient = new CachedSchemaRegistryClient(schemaRegistryURL(), 100)) {
            schemaId = srClient.register(topic + "-value", new JsonSchema(PERSON_SCHEMA));
        }

        ObjectNode original = (ObjectNode) MAPPER.readTree(PAYLOAD);
        produceViaProxy(topic, schemaId, original);

        ObjectNode consumed = consumeViaProxy(topic);

        assertThat(consumed.get("firstname").asText()).isEqualTo("Alice");
        assertThat(consumed.get("lastname").asText()).isEqualTo("Smith");
        assertThat(consumed.get("age").asInt()).isEqualTo(30);
        assertThat(consumed.get("address")).isEqualTo(original.get("address"));
    }

    /**
     * Full proxy roundtrip for ELEMENT-mode fields.
     *
     * <p>
     * Produces the payload through the proxy (encrypt each element/value
     * individually),
     * consumes through the proxy (decrypt), and asserts that {@code tags} array
     * elements
     * and {@code scores} map values are fully restored.
     */
    @Test
    void elementModeRoundTrip() throws Exception {
        String topic = "e2e-jsonsr-" + UUID.randomUUID();
        createTopic(topic);

        int schemaId;
        try (SchemaRegistryClient srClient = new CachedSchemaRegistryClient(schemaRegistryURL(), 100)) {
            schemaId = srClient.register(topic + "-value", new JsonSchema(PERSON_SCHEMA));
        }

        ObjectNode original = (ObjectNode) MAPPER.readTree(PAYLOAD);
        produceViaProxy(topic, schemaId, original);

        ObjectNode consumed = consumeViaProxy(topic);

        // Array ELEMENT mode: each element encrypted then decrypted individually
        assertThat(consumed.get("tags").get(0).asText()).isEqualTo("admin");
        assertThat(consumed.get("tags").get(1).asText()).isEqualTo("user");
        assertThat(consumed.get("tags").get(2).asText()).isEqualTo("readonly");

        // Object ELEMENT mode: each map value encrypted then decrypted individually
        assertThat(consumed.get("scores").get("math").asInt()).isEqualTo(95);
        assertThat(consumed.get("scores").get("science").asInt()).isEqualTo(87);
    }

    /**
     * Verifies that fields are actually encrypted at rest in Kafka.
     *
     * <p>
     * Produces through the proxy (which encrypts), then consumes <em>directly from
     * Kafka</em>
     * (bypassing the proxy) to assert that all configured fields are stored as
     * opaque encrypted
     * strings — not their original values. Unencrypted fields (none in this
     * payload) would
     * pass through unchanged.
     */
    @Test
    void encryptedAtRest() throws Exception {
        String topic = "e2e-jsonsr-" + UUID.randomUUID();
        createTopic(topic);

        int schemaId;
        try (SchemaRegistryClient srClient = new CachedSchemaRegistryClient(schemaRegistryURL(), 100)) {
            schemaId = srClient.register(topic + "-value", new JsonSchema(PERSON_SCHEMA));
        }

        ObjectNode original = (ObjectNode) MAPPER.readTree(PAYLOAD);
        produceViaProxy(topic, schemaId, original);

        // Read the raw (encrypted) record directly from Kafka, bypassing the proxy
        ObjectNode atRest = consumeDirect(topic);

        // OBJECT-mode fields must be encrypted strings (not their original
        // types/values)
        assertEncryptedString(atRest, "firstname", "Alice");
        assertEncryptedString(atRest, "lastname", "Smith");
        // age was an integer; after OBJECT-mode encryption it becomes an encrypted
        // string
        assertThat(atRest.get("age").isTextual()).isTrue();
        assertThat(atRest.get("age").asText()).isNotEqualTo("30");
        // address was an object; after OBJECT-mode encryption it becomes an encrypted
        // string
        assertThat(atRest.get("address").isTextual()).isTrue();
        assertThat(atRest.get("address").asText()).doesNotContain("Main St");

        // ELEMENT-mode array: every element must be an encrypted string, not the
        // original value
        JsonNode tagsAtRest = atRest.get("tags");
        assertThat(tagsAtRest.isArray()).isTrue();
        for (JsonNode element : tagsAtRest) {
            assertThat(element.isTextual()).isTrue();
            assertThat(List.of("admin", "user", "readonly")).doesNotContain(element.asText());
        }

        // ELEMENT-mode object: every map value must be an encrypted string, not the
        // original int
        JsonNode scoresAtRest = atRest.get("scores");
        assertThat(scoresAtRest.isObject()).isTrue();
        scoresAtRest.properties().forEach(entry -> {
            assertThat(entry.getValue().isTextual()).isTrue();
            assertThat(List.of(95, 87)).doesNotContain(entry.getValue().asInt());
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void produceViaProxy(String topic, int schemaId, ObjectNode payload) throws Exception {
        produceTo(topic, schemaId, payload, kroxyliciousBootstrap());
    }

    private static void produceTo(String topic, int schemaId, ObjectNode payload, String bootstrap) throws Exception {
        byte[] valueBytes = toWireBytes(schemaId, MAPPER.writeValueAsBytes(payload));
        try (KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(Map.of(
                "bootstrap.servers", bootstrap,
                "key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer",
                "value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer"))) {
            producer.send(new ProducerRecord<>(topic, valueBytes)).get(30, TimeUnit.SECONDS);
        }
    }

    private static ObjectNode consumeViaProxy(String topic) throws Exception {
        return consumeFrom(topic, kroxyliciousBootstrap());
    }

    private static ObjectNode consumeDirect(String topic) throws Exception {
        return consumeFrom(topic, kafkaBootstrap());
    }

    private static ObjectNode consumeFrom(String topic, String bootstrap) throws Exception {
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(Map.of(
                "bootstrap.servers", bootstrap,
                "key.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer",
                "value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer",
                "auto.offset.reset", "earliest",
                "group.id", UUID.randomUUID().toString()))) {
            TopicPartition tp = new TopicPartition(topic, 0);
            consumer.assign(List.of(tp));
            consumer.seekToBeginning(List.of(tp));
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofSeconds(1));
                if (!records.isEmpty()) {
                    ConsumerRecord<byte[], byte[]> record = records.iterator().next();
                    byte[] jsonBytes = stripWirePrefix(record.value());
                    return (ObjectNode) MAPPER.readTree(jsonBytes);
                }
            }
            throw new AssertionError("No record received from topic " + topic + " within 30s");
        }
    }

    /**
     * Asserts that a field is an encrypted string (textual, Base64-decodable, not
     * the original value).
     */
    private static void assertEncryptedString(ObjectNode node, String fieldName, String originalValue) {
        JsonNode field = node.get(fieldName);
        assertThat(field).as("field '%s' should be present", fieldName).isNotNull();
        assertThat(field.isTextual()).as("field '%s' should be a string (encrypted)", fieldName).isTrue();
        assertThat(field.asText()).as("field '%s' should not equal original value", fieldName)
                .isNotEqualTo(originalValue);
        assertThatCode(() -> Base64.getDecoder().decode(field.asText()))
                .as("field '%s' should be valid Base64 but got: %s ", fieldName, field.asText())
                .doesNotThrowAnyException();
    }
}
