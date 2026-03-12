package com.github.hpgrahsl.kroxylicious.filters.kryptonite.e2e;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Base class for fully containerized Kroxylicious e2e tests.
 *
 * <p>
 * Container topology:
 * 
 * <pre>
 *
 * [Test JVM]
 *   ├── KafkaProducer/Consumer → localhost:9192        (proxy bootstrap, FIXED port)
 *   ├── BypassConsumer         → localhost:29092        (Kafka EXTERNAL listener, FIXED port)
 *   └── SR client              → http://localhost:&lt;mapped&gt;  (direct SR, random port)
 *
 * [Docker Network]
 *   ├── kafka:9092          (PLAINTEXT — used by proxy and SR internally)
 *   ├── kafka:29092         (EXTERNAL — fixed-bound to host:29092 for test JVM direct access)
 *   ├── schema-registry:8081
 *   └── proxy:9192 / :9193  (Kroxylicious portIdentifiesNode routing)
 *         upstream  → kafka:9092  (PLAINTEXT, internal)
 *         SR URL    → http://schema-registry:8081
 * </pre>
 *
 * <p>
 * <strong>Kafka dual-listener setup:</strong> Kafka exposes two listeners so
 * both the proxy
 * (internal Docker network) and the test JVM (host) can reach it:
 * <ul>
 * <li>{@code PLAINTEXT://kafka:9092} — used by proxy upstream and Schema
 * Registry</li>
 * <li>{@code EXTERNAL://localhost:29092} — fixed-bound to host port 29092; used
 * by the
 * test JVM for topic creation and the bypass consumer in
 * {@code encryptedAtRest}</li>
 * </ul>
 *
 * <p>
 * <strong>Proxy fixed ports:</strong> With
 * {@code bootstrapAddress: localhost:9192} and
 * broker node ID 1, the proxy binds port 9192 (bootstrap) and port 9193 (broker
 * 1,
 * bootstrapPort + nodeId). Both are fixed-bound to the same host port numbers.
 * Port 9193
 * is bound lazily after the proxy connects to Kafka and discovers broker IDs,
 * so after
 * {@code PROXY.start()} we explicitly wait for port 9193 to become connectable
 * before
 * any test runs.
 *
 * <p>
 * Ports 9192, 9193, and 29092 must not be in use on the host when running these
 * tests.
 *
 * <p>
 * Tests are skipped unless {@code -De2e.tests=true} is passed.
 */
@SuppressWarnings("resource")
abstract class AbstractKroxyliciousBaseIT {

    protected static final int KAFKA_INTERNAL_PORT = 9092;
    protected static final int KAFKA_EXTERNAL_PORT = 29092;
    protected static final int SCHEMA_REGISTRY_PORT = 8081;
    protected static final int PROXY_BOOTSTRAP_PORT = 9192;
    protected static final int PROXY_BROKER_PORT = 9193;

    protected static final String KAFKA_IMAGE = System.getProperty("e2e.kafka.image", "confluentinc/cp-kafka:7.9.0");
    protected static final String SR_IMAGE = System.getProperty("e2e.schema.registry.image",
            "confluentinc/cp-schema-registry:7.9.0");
    protected static final String KROXYLICIOUS_IMAGE = System.getProperty("e2e.kroxylicious.image",
            "quay.io/kroxylicious/kroxylicious:0.19.0");

    protected static final Network NETWORK = Network.newNetwork();

    @Container
    protected static final GenericContainer<?> KAFKA = new GenericContainer<>(DockerImageName.parse(KAFKA_IMAGE))
            .withNetwork(NETWORK)
            .withNetworkAliases("kafka")
            .withEnv("KAFKA_NODE_ID", "1")
            .withEnv("KAFKA_PROCESS_ROLES", "broker,controller")
            .withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@kafka:9093")
            .withEnv("KAFKA_LISTENERS",
                    "PLAINTEXT://kafka:" + KAFKA_INTERNAL_PORT
                            + ",CONTROLLER://:9093,EXTERNAL://0.0.0.0:"
                            + KAFKA_EXTERNAL_PORT)
            .withEnv("KAFKA_ADVERTISED_LISTENERS",
                    "PLAINTEXT://kafka:" + KAFKA_INTERNAL_PORT + ",EXTERNAL://localhost:"
                            + KAFKA_EXTERNAL_PORT)
            .withEnv("KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                    "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT,EXTERNAL:PLAINTEXT")
            .withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "PLAINTEXT")
            .withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER")
            .withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
            .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
            .withEnv("CLUSTER_ID", "MkU3OEVBNTcwNTJENDM2Qk")
            .withCreateContainerCmdModifier(cmd -> {
                HostConfig hc = cmd.getHostConfig() != null ? cmd.getHostConfig()
                        : HostConfig.newHostConfig();
                Ports portBindings = new Ports();
                portBindings.bind(ExposedPort.tcp(KAFKA_EXTERNAL_PORT),
                        Ports.Binding.bindPort(KAFKA_EXTERNAL_PORT));
                cmd.withHostConfig(hc.withPortBindings(portBindings));
            })
            // .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("e2e.kafka")))
            .waitingFor(Wait.forLogMessage(".*Kafka Server started.*\\n", 1)
                    .withStartupTimeout(Duration.ofSeconds(30)));

    @Container
    protected static final GenericContainer<?> SCHEMA_REGISTRY = new GenericContainer<>(
            DockerImageName.parse(SR_IMAGE))
            .withNetwork(NETWORK)
            .withNetworkAliases("schema-registry")
            .dependsOn(KAFKA)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:" + SCHEMA_REGISTRY_PORT)
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS",
                    "PLAINTEXT://kafka:" + KAFKA_INTERNAL_PORT)
            .withCreateContainerCmdModifier(cmd -> {
                HostConfig hc = cmd.getHostConfig() != null ? cmd.getHostConfig()
                        : HostConfig.newHostConfig();
                Ports portBindings = new Ports();
                portBindings.bind(ExposedPort.tcp(SCHEMA_REGISTRY_PORT),
                        Ports.Binding.bindPort(SCHEMA_REGISTRY_PORT));
                cmd.withHostConfig(hc.withPortBindings(portBindings));
            })
            // .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("e2e.schema-registry")))
            .waitingFor(
                    // Wait.forListeningPorts(SCHEMA_REGISTRY_PORT)
                    Wait.forHttp("/subjects")
                            .forPort(SCHEMA_REGISTRY_PORT)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofSeconds(30)));

    /**
     * Bootstrap address for Kafka clients routed through the Kroxylicious proxy.
     */
    protected static String kroxyliciousBootstrap() {
        return "localhost:" + PROXY_BOOTSTRAP_PORT;
    }

    /**
     * Bootstrap address for Kafka clients connecting directly to Kafka (bypass
     * proxy).
     * Uses the EXTERNAL listener ({@value #KAFKA_EXTERNAL_PORT}) which Kafka
     * advertises
     * as {@code localhost:29092} — resolvable from the test JVM on the host.
     */
    protected static String kafkaBootstrap() {
        return "localhost:" + KAFKA_EXTERNAL_PORT;
    }

    /** HTTP URL of the Schema Registry (direct, not through proxy). */
    protected static String schemaRegistryURL() {
        return "http://localhost:" + SCHEMA_REGISTRY.getMappedPort(SCHEMA_REGISTRY_PORT);
    }

    protected static void createTopic(String topic) throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", kafkaBootstrap()))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                    .all().get(30, TimeUnit.SECONDS);
        }
    }

    // /** Confluent SR wire format: magic byte (0x00) + 4-byte big-endian schema ID + payload. */
    // protected static byte[] toWireBytes(int schemaId, byte[] payload) {
    //     ByteBuffer buf = ByteBuffer.allocate(5 + payload.length);
    //     buf.put((byte) 0x00);
    //     buf.putInt(schemaId);
    //     buf.put(payload);
    //     return buf.array();
    // }

    // /** Strips the 5-byte Confluent SR wire prefix and returns the raw Avro payload. */
    // protected static byte[] stripWirePrefix(byte[] wireBytes) {
    //     byte[] payload = new byte[wireBytes.length - 5];
    //     System.arraycopy(wireBytes, 5, payload, 0, payload.length);
    //     return payload;
    // }

}
