package com.github.hpgrahsl.kroxylicious.filters.kryptonite.it;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Base class for Schema Registry integration tests.
 *
 * <p>Uses the Testcontainers Singleton Containers pattern — Kafka and Schema Registry are
 * started once in a {@code static} block and shared across all subclasses within the same
 * JVM run, avoiding repeated container startup overhead.
 *
 * <p>Container topology:
 * <pre>
 * [test JVM] --(HTTP localhost:mappedPort)--&gt; [schemaregistry:8081]
 *                                                       |
 *                                            PLAINTEXT://kafka:9092
 *                                                       |
 *                                                [kafka:9092]
 * </pre>
 *
 * <p>The Kafka image can be overridden at runtime via the {@code e2e.kafka.image} system property,
 * e.g. {@code -De2e.kafka.image=confluentinc/cp-kafka:8.0.0}. Similarly for
 * {@code e2e.schema.registry.image}. Kafka is configured for KRaft single-node mode using
 * standard {@code KAFKA_*} environment variables understood by the Confluent cp-kafka image;
 * substitute the appropriate variables when using a different image.
 */
abstract class AbstractSchemaRegistryIT {

    private static final Network NETWORK = Network.newNetwork();

    public static final String KAFKA_IMAGE =
            System.getProperty("e2e.kafka.image", "confluentinc/cp-kafka:7.9.0");
    public static final String SCHEMA_REGISTRY_IMAGE =
            System.getProperty("e2e.schema.registry.image", "confluentinc/cp-schema-registry:7.9.0");

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> KAFKA =
            new GenericContainer<>(DockerImageName.parse(KAFKA_IMAGE))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("kafka")
                    .withEnv("KAFKA_NODE_ID", "1")
                    .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
                    .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@kafka:9093")
                    .withEnv("KAFKA_LISTENERS", "PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093")
                    .withEnv("KAFKA_ADVERTISED_LISTENERS", "PLAINTEXT://kafka:9092,CONTROLLER://kafka:9093")
                    .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT")
                    .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
                    .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
                    .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
                    .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
                    .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
                    .withEnv("CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk")
                    .withExposedPorts(9092)
                    .waitingFor(Wait.forListeningPort()
                            .withStartupTimeout(Duration.ofSeconds(30)));

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> SCHEMA_REGISTRY =
            new GenericContainer<>(DockerImageName.parse(SCHEMA_REGISTRY_IMAGE))
                    .withNetwork(NETWORK)
                    .dependsOn(KAFKA)
                    .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schemaregistry")
                    .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
                    .withExposedPorts(8081)
                    .waitingFor(Wait.forHttp("/subjects")
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofSeconds(30)));

    static {
        KAFKA.start();
        SCHEMA_REGISTRY.start();
    }

    protected static String schemaRegistryUrl() {
        return "http://localhost:" + SCHEMA_REGISTRY.getMappedPort(8081);
    }

    protected static SchemaRegistryClient newSchemaRegistryClient() {
        return new CachedSchemaRegistryClient(schemaRegistryUrl(), 100);
    }
}
