package com.github.hpgrahsl.kroxylicious.filters.kryptonite.e2e;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;

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
 * End-to-end roundtrip tests for the Kroxylicious Kryptonite filter (AVRO format).
 *
 * <p>Mirrors {@link JsonSrProxyRoundTripIT} — same field names, same payload values,
 * same three test cases — but uses Avro records and the Avro proxy config.
 *
 * <p>Uses Confluent {@link KafkaAvroSerializer}/{@link KafkaAvroDeserializer} so
 * produce/consume works exactly as in production code.
 *
 * <p>Activate with: {@code -De2e.tests=true}
 */
@Testcontainers
@SuppressWarnings("resource")
class AvroProxyRoundTripIT extends AbstractKroxyliciousBaseIT {

    private static final String PERSON_SCHEMA_JSON = """
            {
              "type": "record",
              "name": "Person",
              "namespace": "com.github.hpgrahsl.e2e",
              "fields": [
                { "name": "firstname", "type": "string" },
                { "name": "lastname",  "type": "string" },
                { "name": "age",       "type": "int"    },
                { "name": "address",   "type": {
                    "type": "record", "name": "Address",
                    "fields": [
                      { "name": "street", "type": "string" },
                      { "name": "city",   "type": "string" }
                    ]
                  }
                },
                { "name": "tags",   "type": { "type": "array", "items": "string"  } },
                { "name": "scores", "type": { "type": "map",   "values": "int"    } }
              ]
            }
            """;

    private static final Schema PERSON_SCHEMA = new Schema.Parser().parse(PERSON_SCHEMA_JSON);

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
                    MountableFile.forClasspathResource("e2e-proxy-config-avro.yaml"),
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

    private static GenericRecord buildPayload() {
        Schema addressSchema = PERSON_SCHEMA.getField("address").schema();
        GenericRecord address = new GenericData.Record(addressSchema);
        address.put("street", "123 Main St");
        address.put("city", "Springfield");

        GenericRecord person = new GenericData.Record(PERSON_SCHEMA);
        person.put("firstname", "Alice");
        person.put("lastname", "Smith");
        person.put("age", 30);
        person.put("address", address);
        person.put("tags", List.of("admin", "user", "readonly"));
        person.put("scores", Map.of("math", 95, "science", 87));
        return person;
    }

    @Test
    void objectModeRoundTrip() throws Exception {
        String topic = "e2e-avro-" + UUID.randomUUID();
        createTopic(topic);

        GenericRecord original = buildPayload();
        produceViaProxy(topic, original);

        GenericRecord consumed = consumeViaProxy(topic);

        assertThat(consumed.get("firstname").toString()).isEqualTo("Alice");
        assertThat(consumed.get("lastname").toString()).isEqualTo("Smith");
        assertThat(consumed.get("age")).isEqualTo(30);
        assertThat(consumed.get("address").toString()).isEqualTo(original.get("address").toString());
    }

    @Test
    void elementModeRoundTrip() throws Exception {
        String topic = "e2e-avro-" + UUID.randomUUID();
        createTopic(topic);

        produceViaProxy(topic, buildPayload());

        GenericRecord consumed = consumeViaProxy(topic);

        var tags = (List<?>) consumed.get("tags");
        assertThat(tags.get(0).toString()).isEqualTo("admin");
        assertThat(tags.get(1).toString()).isEqualTo("user");
        assertThat(tags.get(2).toString()).isEqualTo("readonly");

        var scores = (Map<?, ?>) consumed.get("scores");
        assertThat(((Number) scores.get(new Utf8("math"))).intValue()).isEqualTo(95);
        assertThat(((Number) scores.get(new Utf8("science"))).intValue()).isEqualTo(87);
    }

    @Test
    void encryptedAtRest() throws Exception {
        String topic = "e2e-avro-" + UUID.randomUUID();
        createTopic(topic);

        produceViaProxy(topic, buildPayload());

        // Consume directly from Kafka bypassing the proxy; KafkaAvroDeserializer
        // resolves the encrypted schema automatically via the schema ID in the wire bytes
        GenericRecord atRest = consumeDirect(topic);

        // OBJECT-mode fields become encrypted strings
        assertEncryptedString(atRest, "firstname", "Alice");
        assertEncryptedString(atRest, "lastname", "Smith");
        assertThat(atRest.get("age")).isInstanceOf(Utf8.class);
        assertThat(atRest.get("age").toString()).isNotEqualTo("30");
        assertThat(atRest.get("address")).isInstanceOf(Utf8.class);
        assertThat(atRest.get("address").toString()).doesNotContain("Main St");

        // ELEMENT-mode array: each element is an encrypted string
        var tagsAtRest = (List<?>) atRest.get("tags");
        assertThat(tagsAtRest).isNotEmpty();
        for (Object element : tagsAtRest) {
            assertThat(element.toString()).isNotIn("admin", "user", "readonly");
        }

        // ELEMENT-mode map: each value is an encrypted string
        var scoresAtRest = (Map<?, ?>) atRest.get("scores");
        scoresAtRest.forEach((k, v) -> {
            assertThat(v).isInstanceOf(Utf8.class);
            assertThat(v.toString()).isNotIn("95", "87");
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void produceViaProxy(String topic, GenericRecord record) throws Exception {
        try (KafkaProducer<String, GenericRecord> producer = new KafkaProducer<>(Map.of(
                "bootstrap.servers", kroxyliciousBootstrap(),
                "key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer", KafkaAvroSerializer.class.getName(),
                "schema.registry.url", schemaRegistryURL()))) {
            producer.send(new ProducerRecord<>(topic, record)).get(30, TimeUnit.SECONDS);
        }
    }

    private static GenericRecord consumeViaProxy(String topic) throws Exception {
        return consumeFrom(topic, kroxyliciousBootstrap());
    }

    private static GenericRecord consumeDirect(String topic) throws Exception {
        return consumeFrom(topic, kafkaBootstrap());
    }

    private static GenericRecord consumeFrom(String topic, String bootstrap) throws Exception {
        try (KafkaConsumer<String, GenericRecord> consumer = new KafkaConsumer<>(Map.of(
                "bootstrap.servers", bootstrap,
                "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer", KafkaAvroDeserializer.class.getName(),
                "schema.registry.url", schemaRegistryURL(),
                "specific.avro.reader", "false",
                "auto.offset.reset", "earliest",
                "group.id", UUID.randomUUID().toString()))) {
            TopicPartition tp = new TopicPartition(topic, 0);
            consumer.assign(List.of(tp));
            consumer.seekToBeginning(List.of(tp));
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, GenericRecord> records = consumer.poll(Duration.ofSeconds(1));
                if (!records.isEmpty()) {
                    return records.iterator().next().value();
                }
            }
            throw new AssertionError("No record received from topic " + topic + " within 30s");
        }
    }

    private static void assertEncryptedString(GenericRecord record, String fieldName, String originalValue) {
        Object value = record.get(fieldName);
        assertThat(value).as("field '%s' should be present", fieldName).isNotNull();
        assertThat(value).as("field '%s' should be a string (encrypted)", fieldName).isInstanceOf(Utf8.class);
        assertThat(value.toString()).as("field '%s' should not equal original value", fieldName)
                .isNotEqualTo(originalValue);
        assertThatCode(() -> Base64.getDecoder().decode(value.toString()))
                .as("field '%s' should be valid Base64 but got: %s", fieldName, value)
                .doesNotThrowAnyException();
    }
}
