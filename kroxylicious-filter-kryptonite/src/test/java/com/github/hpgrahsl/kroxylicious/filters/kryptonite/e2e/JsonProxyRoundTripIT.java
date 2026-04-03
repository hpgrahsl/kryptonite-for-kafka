package com.github.hpgrahsl.kroxylicious.filters.kryptonite.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end roundtrip tests for the Kroxylicious Kryptonite filter (JSON
 * format).
 *
 * <p>
 * Container topology (managed by {@link AbstractKroxyliciousBaseIT}):
 * Kafka + Kroxylicious proxy — all in Docker via Testcontainers.
 * No Schema Registry is required; records are plain JSON strings.
 *
 * <p>
 * Uses Kafka's built-in {@code StringSerializer}/{@code StringDeserializer} —
 * the JSON payload is carried as a plain string value.
 *
 * <p>
 * Test payload schema:
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
@EnabledIfSystemProperty(
    named = "e2e.tests",
    matches = "true",
    disabledReason = "End-to-end tests are disabled by default; enable with -De2e.tests=true"
)
@SuppressWarnings("resource")
class JsonProxyRoundTripIT extends AbstractKroxyliciousBaseIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
            .dependsOn(KAFKA)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(System.getProperty("filter.jar.path",
                            "target/kroxylicious-filter-kryptonite-0.1.0-SNAPSHOT.jar")),
                    "/opt/kroxylicious/plugins/kroxylicious-filter-kryptonite.jar")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("e2e-proxy-config-json.yaml"),
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
            .withCommand("--config", "/opt/kroxylicious/config/e2e-config.yaml")
            .waitingFor(Wait.forLogMessage(".*Kroxylicious is started.*\\n", 1)
                    .withStartupTimeout(Duration.ofSeconds(30)));

    @Test
    void objectModeRoundTrip() throws Exception {
        String topic = "e2e-json-" + UUID.randomUUID();
        createTopic(topic);

        JsonNode original = MAPPER.readTree(PAYLOAD);
        produceViaProxy(topic, original);

        JsonNode consumed = consumeViaProxy(topic);

        assertThat(consumed.get("firstname").asText()).isEqualTo("Alice");
        assertThat(consumed.get("lastname").asText()).isEqualTo("Smith");
        assertThat(consumed.get("age").asInt()).isEqualTo(30);
        assertThat(consumed.get("address")).isEqualTo(original.get("address"));
    }

    @Test
    void elementModeRoundTrip() throws Exception {
        String topic = "e2e-json-" + UUID.randomUUID();
        createTopic(topic);

        produceViaProxy(topic, MAPPER.readTree(PAYLOAD));

        JsonNode consumed = consumeViaProxy(topic);

        assertThat(consumed.get("tags").get(0).asText()).isEqualTo("admin");
        assertThat(consumed.get("tags").get(1).asText()).isEqualTo("user");
        assertThat(consumed.get("tags").get(2).asText()).isEqualTo("readonly");

        assertThat(consumed.get("scores").get("math").asInt()).isEqualTo(95);
        assertThat(consumed.get("scores").get("science").asInt()).isEqualTo(87);
    }

    @Test
    void encryptedAtRest() throws Exception {
        String topic = "e2e-json-" + UUID.randomUUID();
        createTopic(topic);

        produceViaProxy(topic, MAPPER.readTree(PAYLOAD));

        // Consume directly from Kafka bypassing the proxy
        JsonNode atRest = consumeDirect(topic);

        // OBJECT-mode fields must be encrypted strings
        assertEncryptedString(atRest, "firstname", "Alice");
        assertEncryptedString(atRest, "lastname", "Smith");
        assertThat(atRest.get("age").isTextual()).isTrue();
        assertThat(atRest.get("age").asText()).isNotEqualTo("30");
        assertThat(atRest.get("address").isTextual()).isTrue();
        assertThat(atRest.get("address").asText()).doesNotContain("Main St");

        // ELEMENT-mode array: every element must be an encrypted string
        JsonNode tagsAtRest = atRest.get("tags");
        assertThat(tagsAtRest.isArray()).isTrue();
        for (JsonNode element : tagsAtRest) {
            assertThat(element.isTextual()).isTrue();
            assertThat(List.of("admin", "user", "readonly")).doesNotContain(element.asText());
        }

        // ELEMENT-mode object: every map value must be an encrypted string
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

    private static void produceViaProxy(String topic, JsonNode payload) throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                "bootstrap.servers", kroxyliciousBootstrap(),
                "key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer", "org.apache.kafka.common.serialization.StringSerializer"))) {
            producer.send(new ProducerRecord<>(topic, MAPPER.writeValueAsString(payload)))
                    .get(30, TimeUnit.SECONDS);
        }
    }

    private static JsonNode consumeViaProxy(String topic) throws Exception {
        return consumeFrom(topic, kroxyliciousBootstrap());
    }

    private static JsonNode consumeDirect(String topic) throws Exception {
        return consumeFrom(topic, kafkaBootstrap());
    }

    private static JsonNode consumeFrom(String topic, String bootstrap) throws Exception {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                "bootstrap.servers", bootstrap,
                "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
                "auto.offset.reset", "earliest",
                "group.id", UUID.randomUUID().toString()))) {
            TopicPartition tp = new TopicPartition(topic, 0);
            consumer.assign(List.of(tp));
            consumer.seekToBeginning(List.of(tp));
            AtomicReference<ConsumerRecord<String, String>> resultRecord = new AtomicReference<>();
            await().atMost(15, TimeUnit.SECONDS).until(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                if (!records.isEmpty()) {
                    resultRecord.set(records.iterator().next());
                    return true;
                }
                return false;
            });
            return MAPPER.readTree(resultRecord.get().value());
        }
    }

    private static void assertEncryptedString(JsonNode node, String fieldName, String originalValue) {
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
