package com.github.hpgrahsl.kroxylicious.filters.kryptonite;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import com.github.hpgrahsl.kryptonite.EncryptedField;
import com.github.hpgrahsl.kryptonite.FieldMetaData;
import com.github.hpgrahsl.kryptonite.PayloadMetaData;
import org.objenesis.strategy.StdInstantiatorStrategy;

/**
 * Provides a thread-local {@link Kryo} instance configured for Kryptonite field serialization.
 *
 * <p>The registration order is intentionally identical to {@code KryoInstance} in the
 * {@code kryptonite-serdes-converters} module so that {@code EncryptedField} objects
 * serialized by this filter are byte-for-byte compatible with records encrypted by the
 * Kryptonite Kafka Connect SMT, ksqlDB UDFs, and Flink UDFs:
 * <ol>
 *   <li>{@link FieldMetaData} — registration ID 10</li>
 *   <li>{@link PayloadMetaData} — registration ID 11</li>
 *   <li>{@link EncryptedField} — registration ID 12</li>
 *   <li>{@code byte[]} — registration ID 13</li>
 * </ol>
 *
 * <p>Because {@link Kryo#writeObject} / {@link Kryo#readObject} do NOT write class IDs into
 * the stream (they use the explicitly provided type), and because the field types of
 * {@link EncryptedField} are all concrete ({@link PayloadMetaData}, {@code byte[]}) so Kryo
 * resolves their serializers directly without emitting class IDs, the only ordering constraint
 * that matters for wire compatibility is that the three kryptonite types appear first.
 *
 * <p>Thread safety: one {@link Kryo} instance per thread via {@link ThreadLocal}.
 * {@link Kryo} is NOT thread-safe and must not be shared across threads.
 */
public final class KryptoniteKryo {

    private static final ThreadLocal<Kryo> KRYOS = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setWarnUnregisteredClasses(true);
        kryo.setRegistrationRequired(true);
        kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        // Registration order must match KryoInstance in kryptonite-serdes-converters
        kryo.register(FieldMetaData.class);    // ID 10
        kryo.register(PayloadMetaData.class);  // ID 11
        kryo.register(EncryptedField.class);   // ID 12
        kryo.register(byte[].class);           // ID 13
        return kryo;
    });

    private KryptoniteKryo() {}

    public static Kryo get() {
        return KRYOS.get();
    }
}
