package com.github.hpgrahsl.kryptonite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link Clock} whose time can be advanced manually — for use in tests only.
 */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private final AtomicLong epochMs;

    public MutableClock() {
        this(Clock.systemUTC());
    }

    public MutableClock(Clock base) {
        this.zone = base.getZone();
        this.epochMs = new AtomicLong(base.millis());
    }

    public void advance(Duration duration) {
        epochMs.addAndGet(duration.toMillis());
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(epochMs.get());
    }

}
