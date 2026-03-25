package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.RecordFormat;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.AvroSchemaRegistryRecordProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.JsonSchemaRegistryRecordProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.PlainJsonRecordProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.RecordValueProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.routing.TopicFieldConfigResolver;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.ConfluentSchemaRegistryAdapter;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.kroxylicious.proxy.filter.FilterDispatchExecutor;
import io.kroxylicious.proxy.filter.FilterFactory;
import io.kroxylicious.proxy.filter.FilterFactoryContext;
import io.kroxylicious.proxy.plugin.Plugin;
import io.kroxylicious.proxy.plugin.Plugins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link FilterFactory} for the Kryptonite field-level decryption filter.
 *
 * <p>The {@code @Plugin(configType = KryptoniteFilterConfig.class)} annotation tells
 * Kroxylicious how to deserialize the YAML {@code config:} block.
 * Reference in proxy YAML: {@code type: KryptoniteDecryptionFilterFactory}.
 *
 * <p>The decrypt-side factory creates a separate {@link ConfluentSchemaRegistryAdapter}
 * instance with its own cache (not shared with the encrypt-side adapter), as specified
 * in the design: encrypt and decrypt adapters are separate instances.
 */
@Plugin(configType = KryptoniteFilterConfig.class)
public class KryptoniteDecryptionFilterFactory
        implements FilterFactory<KryptoniteFilterConfig, KryptoniteFilterConfig> {

    private static final Logger LOG = LoggerFactory.getLogger(KryptoniteDecryptionFilterFactory.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_BLOCKING_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors());

    private ExecutorService filterBlockingExecutor;

    @Override
    public KryptoniteFilterConfig initialize(FilterFactoryContext context, KryptoniteFilterConfig config) {
        Plugins.requireConfig(this, config);
        int poolSize = config.getBlockingPoolSize() > 0 ? config.getBlockingPoolSize() : DEFAULT_BLOCKING_POOL_SIZE;
        filterBlockingExecutor = Executors.newFixedThreadPool(poolSize);
        LOG.info("KryptoniteDecryptionFilterFactory initialized with blockingPoolSize={}", poolSize);
        return config;
    }

    @Override
    public KryptoniteDecryptionFilter createFilter(FilterFactoryContext context, KryptoniteFilterConfig config) {
        LOG.info("Creating KryptoniteDecryptionFilter with schemaRegistryUrl={} recordFormat={} schemaMode={}",
                config.getSchemaRegistryUrl(), config.getRecordFormat(), config.getSchemaMode());

        FilterDispatchExecutor filterDispatchExecutor = context.filterDispatchExecutor();
        Kryptonite kryptonite = Kryptonite.createFromConfig(toConfigMap(config));
        RecordValueProcessor processor = createProcessor(kryptonite, config);
        TopicFieldConfigResolver resolver = new TopicFieldConfigResolver(config.getTopicFieldConfigs());
        return new KryptoniteDecryptionFilter(processor, resolver, filterBlockingExecutor, filterDispatchExecutor);
    }

    @Override
    public void close(KryptoniteFilterConfig initializationData) {
        if (filterBlockingExecutor != null) {
            filterBlockingExecutor.shutdown();
        }
    }

    private static RecordValueProcessor createProcessor(Kryptonite kryptonite, KryptoniteFilterConfig config) {
        RecordFormat format = config.getRecordFormat() != null ? config.getRecordFormat() : RecordFormat.JSON_SR;
        String serdeType = config.getSerdeType();
        return switch (format) {
            case JSON -> new PlainJsonRecordProcessor(kryptonite, serdeType, "");
            case JSON_SR -> {
                SchemaRegistryClient srClient = new CachedSchemaRegistryClient(
                        List.of(config.getSchemaRegistryUrl()), 100,
                        List.of(new JsonSchemaProvider()), config.getSchemaRegistryConfig());
                SchemaRegistryAdapter adapter = new ConfluentSchemaRegistryAdapter(srClient);
                yield new JsonSchemaRegistryRecordProcessor(kryptonite, adapter, serdeType, "");
            }
            case AVRO -> {
                SchemaRegistryClient srClient = new CachedSchemaRegistryClient(
                        List.of(config.getSchemaRegistryUrl()), 100,
                        List.of(new JsonSchemaProvider(), new AvroSchemaProvider()),
                        config.getSchemaRegistryConfig());
                SchemaRegistryAdapter adapter = new ConfluentSchemaRegistryAdapter(srClient);
                yield new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, serdeType, "");
            }
            default -> throw new IllegalArgumentException("Unsupported recordFormat for decryption: " + format);
        };
    }

    private static Map<String, String> toConfigMap(KryptoniteFilterConfig cfg) {
        Map<String, String> config = new HashMap<>();
        config.put("key_source", cfg.getKeySource());
        config.put("cipher_algorithm", cfg.getCipherAlgorithm());
        config.put("kms_type", cfg.getKmsType());
        config.put("kms_config", cfg.getKmsConfig());
        config.put("kek_type", cfg.getKekType());
        config.put("kek_uri", cfg.getKekUri());
        config.put("kek_config", cfg.getKekConfig());

        try {
            if (cfg.getCipherDataKeys() != null) {
                config.put("cipher_data_keys", MAPPER.writeValueAsString(cfg.getCipherDataKeys()));
            } else {
                config.put("cipher_data_keys", "[]");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize cipher_data_keys to JSON", e);
        }

        return config;
    }
}
