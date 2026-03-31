package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.RecordFormat;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.SchemaMode;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.AvroSchemaRegistryRecordProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.JsonSchemaRegistryRecordProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.PlainJsonRecordProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.processor.RecordValueProcessor;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.routing.TopicFieldConfigResolver;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.DefaultDynamicSchemaRegistryAdapter;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.DefaultStaticSchemaRegistryAdapter;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde.SchemaRegistryAdapter;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.kroxylicious.proxy.filter.Filter;
import io.kroxylicious.proxy.filter.FilterFactory;
import io.kroxylicious.proxy.filter.FilterFactoryContext;
import io.kroxylicious.proxy.plugin.Plugins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared base for {@link KryptoniteEncryptionFilterFactory} and {@link KryptoniteDecryptionFilterFactory}.
 *
 * <p>Holds all shared state ({@link Kryptonite}, {@link RecordValueProcessor},
 * {@link TopicFieldConfigResolver}, blocking executor) and implements the common
 * {@link #initialize} and {@link #close} lifecycle. Subclasses only need to implement
 * {@link #createFilter} and {@link #defaultKeyId}.
 */
abstract class AbstractKryptoniteFilterFactory
        implements FilterFactory<KryptoniteFilterConfig, KryptoniteFilterConfig> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractKryptoniteFilterFactory.class);
    private static final int DEFAULT_BLOCKING_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors());

    protected ExecutorService filterBlockingExecutor;
    protected Kryptonite kryptonite;
    protected RecordValueProcessor processor;
    protected TopicFieldConfigResolver resolver;

    /**
     * Returns the default key identifier to pass to the record processor.
     * Encryption factories return the configured {@code cipherDataKeyIdentifier};
     * decryption factories return {@code ""} (key ID is read from the encrypted envelope).
     */
    protected abstract String defaultKeyId(KryptoniteFilterConfig config);

    @Override
    public abstract Filter createFilter(FilterFactoryContext context, KryptoniteFilterConfig config);

    @Override
    public KryptoniteFilterConfig initialize(FilterFactoryContext context, KryptoniteFilterConfig config) {
        Plugins.requireConfig(this, config);
        int poolSize = config.getBlockingPoolSize() > 0 ? config.getBlockingPoolSize() : DEFAULT_BLOCKING_POOL_SIZE;
        filterBlockingExecutor = Executors.newFixedThreadPool(poolSize);
        kryptonite = Kryptonite.createFromConfig(config.toKryptoniteConfigMap());
        processor = createProcessor(kryptonite, config, defaultKeyId(config));
        resolver = new TopicFieldConfigResolver(config.getTopicFieldConfigs());
        LOG.info("{} initialized with blockingPoolSize={} recordFormat={} schemaMode={}",
                getClass().getSimpleName(), poolSize, config.getRecordFormat(), config.getSchemaMode());
        return config;
    }

    @Override
    public void close(KryptoniteFilterConfig initializationData) {
        if (filterBlockingExecutor != null) {
            filterBlockingExecutor.shutdown();
        }
        if (kryptonite != null) {
            kryptonite.close();
        }
    }

    private static RecordValueProcessor createProcessor(Kryptonite kryptonite, KryptoniteFilterConfig config, String defaultKeyId) {
        RecordFormat format = config.getRecordFormat() != null ? config.getRecordFormat() : RecordFormat.JSON_SR;
        String serdeType = config.getSerdeType();
        return switch (format) {
            case JSON -> new PlainJsonRecordProcessor(kryptonite, serdeType, defaultKeyId);
            case JSON_SR -> {
                SchemaRegistryClient srClient = new CachedSchemaRegistryClient(
                        List.of(config.getSchemaRegistryUrl()), 100,
                        List.of(new JsonSchemaProvider()), config.getSchemaRegistryConfig());
                SchemaRegistryAdapter adapter = config.getSchemaMode() == SchemaMode.STATIC
                        ? new DefaultStaticSchemaRegistryAdapter(srClient)
                        : new DefaultDynamicSchemaRegistryAdapter(srClient);
                yield new JsonSchemaRegistryRecordProcessor(kryptonite, adapter, serdeType, defaultKeyId);
            }
            case AVRO -> {
                SchemaRegistryClient srClient = new CachedSchemaRegistryClient(
                        List.of(config.getSchemaRegistryUrl()), 100,
                        List.of(new JsonSchemaProvider(), new AvroSchemaProvider()),
                        config.getSchemaRegistryConfig());
                SchemaRegistryAdapter adapter = config.getSchemaMode() == SchemaMode.STATIC
                        ? new DefaultStaticSchemaRegistryAdapter(srClient)
                        : new DefaultDynamicSchemaRegistryAdapter(srClient);
                yield new AvroSchemaRegistryRecordProcessor(kryptonite, adapter, serdeType, defaultKeyId);
            }
            default -> throw new IllegalArgumentException("Unsupported recordFormat: " + format);
        };
    }
}
