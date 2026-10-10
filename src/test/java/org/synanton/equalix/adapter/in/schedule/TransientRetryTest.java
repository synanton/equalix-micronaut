package org.synanton.equalix.adapter.in.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.exception.LockAcquisitionException;
import org.junit.jupiter.api.Test;

class TransientRetryTest {

    @Test
    void retriesLockFailuresAndSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        TransientRetry.run("test", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new LockAcquisitionException("deadlock", null);
            }
        });

        assertThat(calls).hasValue(3);
    }

    @Test
    void propagatesAfterMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> TransientRetry.run("test", () -> {
            calls.incrementAndGet();
            throw new LockAcquisitionException("deadlock", null);
        })).isInstanceOf(LockAcquisitionException.class);
        assertThat(calls).hasValue(3);
    }

    @Test
    void doesNotRetryBusinessFailures() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> TransientRetry.run("test", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(calls).hasValue(1);
    }
}
