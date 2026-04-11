package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.github.hpgrahsl.kryptonite.Kryptonite;
import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
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
 * {@link #createFilter}.
 */
abstract class AbstractKryptoniteFilterFactory
        implements FilterFactory<KryptoniteFilterConfig, KryptoniteFilterConfig> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractKryptoniteFilterFactory.class);
    private static final int DEFAULT_BLOCKING_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors());

    protected ExecutorService filterBlockingExecutor;
    protected Kryptonite kryptonite;
    protected RecordValueProcessor processor;
    protected TopicFieldConfigResolver resolver;

    @Override
    public abstract Filter createFilter(FilterFactoryContext context, KryptoniteFilterConfig config);

    @Override
    public KryptoniteFilterConfig initialize(FilterFactoryContext context, KryptoniteFilterConfig config) {
        Plugins.requireConfig(this, config);
        config.validate();
        int poolSize = config.getBlockingPoolSize() > 0 ? config.getBlockingPoolSize() : DEFAULT_BLOCKING_POOL_SIZE;
        filterBlockingExecutor = Executors.newFixedThreadPool(poolSize);
        kryptonite = Kryptonite.createFromConfig(config.toKryptoniteConfigMap());
        processor = createProcessor(kryptonite, config);
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

    private static RecordValueProcessor createProcessor(Kryptonite kryptonite, KryptoniteFilterConfig config) {
        return switch (config.getRecordFormat()) {
            case JSON -> new PlainJsonRecordProcessor(kryptonite, config);
            case JSON_SR -> new JsonSchemaRegistryRecordProcessor(kryptonite,
                    createAdapter(createSrClient(config), config.getSchemaMode()), config);
            case AVRO -> new AvroSchemaRegistryRecordProcessor(kryptonite,
                    createAdapter(createSrClient(config), config.getSchemaMode()), config);
            case PROTOBUF -> throw new IllegalArgumentException("PROTOBUF record format is not yet supported");
        };
    }

    private static SchemaRegistryClient createSrClient(KryptoniteFilterConfig config) {
        return new CachedSchemaRegistryClient(
                List.of(config.getSchemaRegistryUrl()), 100,
                List.of(new JsonSchemaProvider(), new AvroSchemaProvider()),
                config.getSchemaRegistryConfig());
    }

    private static SchemaRegistryAdapter createAdapter(SchemaRegistryClient srClient, SchemaMode schemaMode) {
        return schemaMode == SchemaMode.STATIC
                ? new DefaultStaticSchemaRegistryAdapter(srClient)
                : new DefaultDynamicSchemaRegistryAdapter(srClient);
    }
}
