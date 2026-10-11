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
import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.KafkaException;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
                    MountableFile.forHostPath(FILTER_JAR_PATH),
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

    @Test
    void encryptionFailureFailsProduceAndNothingReachesBroker() throws Exception {
        String topic = "e2e-json-" + UUID.randomUUID();
        createTopic(topic);

        // not a JSON object, so field encryption fails inside the proxy with a record format error, which
        // the filter answers with a non-retriable INVALID_RECORD: a default producer fails fast, no retries
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                "bootstrap.servers", kroxyliciousBootstrap(),
                "key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer", "org.apache.kafka.common.serialization.StringSerializer"))) {
            assertThatThrownBy(() -> producer.send(new ProducerRecord<>(topic, "this is not json"))
                    .get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(InvalidRecordException.class);
        }

        // the failed batch must never have been forwarded to the broker
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                "bootstrap.servers", kafkaBootstrap(),
                "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer"))) {
            TopicPartition tp = new TopicPartition(topic, 0);
            assertThat(consumer.endOffsets(List.of(tp))).containsEntry(tp, 0L);
        }
    }

    @Test
    void undecryptableRecordFailsConsumerAtExactOffset() throws Exception {
        String topic = "e2e-json-" + UUID.randomUUID();
        createTopic(topic);

        produceViaProxy(topic, MAPPER.readTree(PAYLOAD));  // offset 0
        produceDirect(topic, "this is not json");          // offset 1, bypasses the proxy
        produceViaProxy(topic, MAPPER.readTree(PAYLOAD));  // offset 2

        TopicPartition tp = new TopicPartition(topic, 0);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                "bootstrap.servers", kroxyliciousBootstrap(),
                "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
                "enable.auto.commit", "false"))) {
            consumer.assign(List.of(tp));
            consumer.seekToBeginning(List.of(tp));

            // records before the undecryptable one are delivered, then poll() fails at its exact offset
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            AtomicReference<KafkaException> failure = new AtomicReference<>();
            await().atMost(15, TimeUnit.SECONDS).until(() -> {
                try {
                    consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                    return false;
                } catch (KafkaException e) {
                    failure.set(e);
                    return true;
                }
            });
            assertThat(received).extracting(ConsumerRecord::offset).containsExactly(0L);
            assertThat(MAPPER.readTree(received.get(0).value()).get("firstname").asText()).isEqualTo("Alice");
            assertThat(failure.get())
                    .hasMessageContaining("corrupt message")
                    .hasMessageContaining("offset 1 ");

            // the application can skip the bad record and continue with the next one
            consumer.seek(tp, 2);
            AtomicReference<ConsumerRecord<String, String>> next = new AtomicReference<>();
            await().atMost(15, TimeUnit.SECONDS).until(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                if (records.isEmpty()) return false;
                next.set(records.iterator().next());
                return true;
            });
            assertThat(next.get().offset()).isEqualTo(2L);
            assertThat(MAPPER.readTree(next.get().value()).get("firstname").asText()).isEqualTo("Alice");
        }
    }

    @Test
    void tombstonesPassThroughUnchanged() throws Exception {
        String topic = "e2e-json-" + UUID.randomUUID();
        createTopic(topic);

        produceViaProxy(topic, MAPPER.readTree(PAYLOAD));      // offset 0
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                "bootstrap.servers", kroxyliciousBootstrap(),
                "key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer", "org.apache.kafka.common.serialization.StringSerializer"))) {
            producer.send(new ProducerRecord<>(topic, "deleted-key", null)).get(10, TimeUnit.SECONDS); // offset 1
        }
        produceViaProxy(topic, MAPPER.readTree(PAYLOAD));      // offset 2

        // at rest: the tombstone is stored as a tombstone
        List<ConsumerRecord<String, String>> atRest = consumeAll(topic, kafkaBootstrap(), "read_uncommitted", 3);
        assertThat(atRest.get(1).key()).isEqualTo("deleted-key");
        assertThat(atRest.get(1).value()).isNull();

        // through the proxy: the tombstone is delivered as is and does not block the records around it
        List<ConsumerRecord<String, String>> viaProxy = consumeAll(topic, kroxyliciousBootstrap(), "read_uncommitted", 3);
        assertThat(viaProxy).extracting(ConsumerRecord::offset).containsExactly(0L, 1L, 2L);
        assertThat(viaProxy.get(1).key()).isEqualTo("deleted-key");
        assertThat(viaProxy.get(1).value()).isNull();
        assertThat(MAPPER.readTree(viaProxy.get(0).value()).get("firstname").asText()).isEqualTo("Alice");
        assertThat(MAPPER.readTree(viaProxy.get(2).value()).get("firstname").asText()).isEqualTo("Alice");
    }

    @Test
    void transactionalRecordsAndMarkersRoundTrip() throws Exception {
        String topic = "e2e-json-" + UUID.randomUUID();
        createTopic(topic);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                "bootstrap.servers", kroxyliciousBootstrap(),
                "key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer", "org.apache.kafka.common.serialization.StringSerializer",
                "transactional.id", "e2e-txn-" + UUID.randomUUID()))) {
            producer.initTransactions();
            producer.beginTransaction();
            producer.send(new ProducerRecord<>(topic, "committed", MAPPER.writeValueAsString(MAPPER.readTree(PAYLOAD))));
            producer.commitTransaction();                      // offset 0 data, offset 1 commit marker
            producer.beginTransaction();
            producer.send(new ProducerRecord<>(topic, "aborted", MAPPER.writeValueAsString(MAPPER.readTree(PAYLOAD))));
            producer.flush();
            producer.abortTransaction();                       // offset 2 data, offset 3 abort marker
        }
        produceViaProxy(topic, MAPPER.readTree(PAYLOAD));      // offset 4, non-transactional

        // at rest: transactional records are encrypted like any other record
        List<ConsumerRecord<String, String>> atRest = consumeAll(topic, kafkaBootstrap(), "read_uncommitted", 3);
        assertEncryptedString(MAPPER.readTree(atRest.get(0).value()), "firstname", "Alice");

        // markers pass through the proxy unchanged, so consumers apply transaction semantics as usual
        List<ConsumerRecord<String, String>> committed = consumeAll(topic, kroxyliciousBootstrap(), "read_committed", 2);
        assertThat(committed).extracting(ConsumerRecord::offset).containsExactly(0L, 4L);
        List<ConsumerRecord<String, String>> uncommitted = consumeAll(topic, kroxyliciousBootstrap(), "read_uncommitted", 3);
        assertThat(uncommitted).extracting(ConsumerRecord::offset).containsExactly(0L, 2L, 4L);
        for (ConsumerRecord<String, String> record : uncommitted) {
            assertThat(MAPPER.readTree(record.value()).get("firstname").asText()).isEqualTo("Alice");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<ConsumerRecord<String, String>> consumeAll(String topic, String bootstrap,
                                                                   String isolationLevel, int expectedRecords) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                "bootstrap.servers", bootstrap,
                "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
                "isolation.level", isolationLevel,
                "enable.auto.commit", "false"))) {
            TopicPartition tp = new TopicPartition(topic, 0);
            consumer.assign(List.of(tp));
            consumer.seekToBeginning(List.of(tp));
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            await().atMost(15, TimeUnit.SECONDS).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                return received.size() >= expectedRecords;
            });
            return received;
        }
    }

    private static void produceDirect(String topic, String value) throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                "bootstrap.servers", kafkaBootstrap(),
                "key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer", "org.apache.kafka.common.serialization.StringSerializer"))) {
            producer.send(new ProducerRecord<>(topic, value)).get(30, TimeUnit.SECONDS);
        }
    }

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
