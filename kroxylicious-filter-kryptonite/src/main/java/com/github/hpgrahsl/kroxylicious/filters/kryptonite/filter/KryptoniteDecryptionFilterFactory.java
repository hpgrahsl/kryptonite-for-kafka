package com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.KryptoniteFilterConfig;
import io.kroxylicious.proxy.filter.FilterDispatchExecutor;
import io.kroxylicious.proxy.filter.FilterFactoryContext;
import io.kroxylicious.proxy.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Plugin(configType = KryptoniteFilterConfig.class)
public class KryptoniteDecryptionFilterFactory extends AbstractKryptoniteFilterFactory {

    private static final Logger LOG = LoggerFactory.getLogger(KryptoniteDecryptionFilterFactory.class);

    @Override
    protected String defaultKeyId(KryptoniteFilterConfig config) {
        return "";
    }

    @Override
    public KryptoniteDecryptionFilter createFilter(FilterFactoryContext context, KryptoniteFilterConfig config) {
        LOG.debug("Creating KryptoniteDecryptionFilter for new connection (shared processor and resolver)");
        FilterDispatchExecutor filterDispatchExecutor = context.filterDispatchExecutor();
        return new KryptoniteDecryptionFilter(processor, resolver, filterBlockingExecutor, filterDispatchExecutor);
    }
}
