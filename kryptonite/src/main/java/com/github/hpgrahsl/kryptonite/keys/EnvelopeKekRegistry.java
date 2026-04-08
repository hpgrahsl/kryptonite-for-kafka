/*
 * Copyright (c) 2021. Hans-Peter Grahsl (grahslhp@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.hpgrahsl.kryptonite.keys;

import com.github.hpgrahsl.kryptonite.KryptoniteException;
import com.github.hpgrahsl.kryptonite.config.ConfigurationException;
import com.github.hpgrahsl.kryptonite.config.EnvelopeKekConfig;
import com.github.hpgrahsl.kryptonite.kms.EnvelopeKekEncryption;
import com.github.hpgrahsl.kryptonite.kms.EnvelopeKekEncryptionProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import static java.lang.System.Logger.Level.DEBUG;

/**
 * Registry for envelope KEK entries.
 *
 * <p>Maps {@code identifier → EnvelopeKekEncryption}. Populated at construction time
 * from a list of {@link EnvelopeKekConfig} entries parsed from {@code envelope_kek_configs}.
 * Each entry is resolved to a concrete {@link EnvelopeKekEncryption} via
 * {@link EnvelopeKekEncryptionProvider} ServiceLoader dispatch on {@code kek_type}.
 *
 * <p>Parallel to {@code AbstractKeyVault}: keyset-based algorithms use
 * {@code KeyVault.readKeysetHandle(keyId)}; KMS-backed envelope algorithms use
 * {@link #get(String)} to obtain the KMS-backed wrap/unwrap primitive.
 */
public class EnvelopeKekRegistry {

    private static final System.Logger LOG = System.getLogger(EnvelopeKekRegistry.class.getName());

    private final Map<String, EnvelopeKekEncryption> registry;

    /**
     * Constructs a registry directly from a pre-built map.
     * Intended for unit testing — bypasses ServiceLoader provider resolution.
     */
    public EnvelopeKekRegistry(Map<String, EnvelopeKekEncryption> entries) {
        this.registry = Collections.unmodifiableMap(new HashMap<>(entries));
        LOG.log(DEBUG, "EnvelopeKekRegistry: created from pre-built map with {0} KEK(s)", registry.size());
    }

    public EnvelopeKekRegistry(List<EnvelopeKekConfig> configs) {
        Map<String, EnvelopeKekEncryption> map = new HashMap<>();
        for (EnvelopeKekConfig cfg : configs) {
            validateEntry(cfg);
            LOG.log(DEBUG, "EnvelopeKekRegistry: resolving provider for kekType=''{0}'' identifier=''{1}''",
                cfg.getKekType(), cfg.getIdentifier());
            EnvelopeKekEncryptionProvider provider = ServiceLoader
                .load(EnvelopeKekEncryptionProvider.class, EnvelopeKekEncryptionProvider.class.getClassLoader())
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> p.kekType().equals(cfg.getKekType()))
                .findFirst()
                .orElseThrow(() -> new ConfigurationException(
                    "no EnvelopeKekEncryptionProvider found for kek_type '" + cfg.getKekType()
                        + "' — add the corresponding kryptonite KMS module to the classpath"));
            map.put(cfg.getIdentifier(), provider.createEnvelopeKekEncryption(cfg.getKekUri(), cfg.getKekConfig()));
        }
        this.registry = Collections.unmodifiableMap(map);
        LOG.log(DEBUG, "EnvelopeKekRegistry: registered {0} KEK(s)", registry.size());
    }

    /**
     * Returns the {@link EnvelopeKekEncryption} for the given identifier.
     *
     * @throws KryptoniteException if no entry is registered for that identifier
     */
    public EnvelopeKekEncryption get(String identifier) {
        EnvelopeKekEncryption kek = registry.get(identifier);
        if (kek == null) {
            throw new KryptoniteException(
                "no envelope KEK found for identifier '" + identifier
                    + "' - check envelope_kek_configs");
        }
        return kek;
    }

    public Set<String> identifiers() {
        return registry.keySet();
    }

    public int size() {
        return registry.size();
    }

    private static void validateEntry(EnvelopeKekConfig cfg) {
        if (cfg.getIdentifier() == null || cfg.getIdentifier().isBlank()) {
            throw new ConfigurationException("envelope_kek_configs entry has blank or missing 'identifier'");
        }
        if (cfg.getKekType() == null || cfg.getKekType().isBlank()) {
            throw new ConfigurationException(
                "envelope_kek_configs entry '" + cfg.getIdentifier() + "' has blank or missing 'kek_type'");
        }
        if (cfg.getKekUri() == null || cfg.getKekUri().isBlank()) {
            throw new ConfigurationException(
                "envelope_kek_configs entry '" + cfg.getIdentifier() + "' has blank or missing 'kek_uri'");
        }
    }

}
