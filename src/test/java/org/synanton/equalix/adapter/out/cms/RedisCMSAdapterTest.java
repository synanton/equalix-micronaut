package org.synanton.equalix.adapter.out.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lettuce.core.KeyValue;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.synanton.equalix.config.properties.QueueProperties;
import org.synanton.equalix.config.properties.QueueProperties.CmsProperties;

/**
 * Micronaut port of the oracle's {@code RedisCMSAdapterTest}. The adapter under test uses
 * Lettuce instead of Spring Data Redis, so the doubles are Lettuce connections/commands.
 * Same behaviors and key layout: {@code test:cms:v2} hash, {@code :total} counter,
 * min-across-rows reads, Lua add, rebuild semantics, no-fallback failure mode.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisCMSAdapterTest {

    private static final String NAMESPACE = "test:cms";
    private static final String HASH_KEY = "test:cms:v2";
    private static final String TOTAL_KEY = "test:cms:v2:total";

    @Mock
    StatefulRedisConnection<String, String> redisConnection;
    @Mock
    RedisCommands<String, String> commands;

    RedisCMSAdapter adapter;

    @BeforeEach
    void setUp() {
        QueueProperties props = new QueueProperties();
        CmsProperties cms = new QueueProperties.CmsProperties();
        cms.setWidth(1024);
        cms.setDepth(3);
        cms.setMode("redis");
        QueueProperties.CmsProperties.RedisProperties redis =
            new QueueProperties.CmsProperties.RedisProperties();
        redis.setKeyNamespace(NAMESPACE);
        redis.setFallbackToLocal(false);
        cms.setRedis(redis);
        props.setCms(cms);

        when(redisConnection.sync()).thenReturn(commands);

        adapter = new RedisCMSAdapter(props, redisConnection);
    }

    // --- estimateCount ---

    @Test
    void shouldReturnZeroForUnknownKey() {
        when(commands.hmget(eq(HASH_KEY), any(String[].class)))
            .thenReturn(List.of(KeyValue.empty("r0:c0"), KeyValue.empty("r1:c0"), KeyValue.empty("r2:c0")));

        assertThat(adapter.estimateCount("unknown")).isEqualTo(0L);
    }

    @Test
    void shouldReturnMinimumAcrossRows() {
        when(commands.hmget(eq(HASH_KEY), any(String[].class)))
            .thenReturn(List.of(KeyValue.just("r0:c0", "5"), KeyValue.just("r1:c0", "3"),
                KeyValue.just("r2:c0", "7")));

        assertThat(adapter.estimateCount("tenant-a")).isEqualTo(3L);
    }

    @Test
    void shouldReturnZeroWhenAllCellsNegative() {
        when(commands.hmget(eq(HASH_KEY), any(String[].class)))
            .thenReturn(List.of(KeyValue.just("r0:c0", "-2"), KeyValue.just("r1:c0", "-1"),
                KeyValue.just("r2:c0", "-3")));

        assertThat(adapter.estimateCount("tenant-a")).isEqualTo(0L);
    }

    @Test
    void shouldReturnZeroWhenMinCellIsNegative() {
        when(commands.hmget(eq(HASH_KEY), any(String[].class)))
            .thenReturn(List.of(KeyValue.just("r0:c0", "4"), KeyValue.just("r1:c0", "-1"),
                KeyValue.just("r2:c0", "2")));

        assertThat(adapter.estimateCount("tenant-a")).isEqualTo(0L);
    }

    @Test
    void shouldReturnZeroWhenSomeCellsNull() {
        when(commands.hmget(eq(HASH_KEY), any(String[].class)))
            .thenReturn(List.of(KeyValue.empty("r0:c0"), KeyValue.just("r1:c0", "5"),
                KeyValue.empty("r2:c0")));

        // null treated as 0, min is 0
        assertThat(adapter.estimateCount("tenant-a")).isEqualTo(0L);
    }

    // --- add ---

    @Test
    void shouldExecuteLuaScriptWithCorrectArgCount() {
        adapter.add("tenant-a", 1L);

        // depth=3: KEYS = [hash, total], ARGV = [delta, field_r0, field_r1, field_r2]
        verify(commands).eval(any(String.class), eq(ScriptOutputType.INTEGER),
            eq(new String[]{HASH_KEY, TOTAL_KEY}),
            any(String.class), any(String.class), any(String.class), any(String.class));
    }

    @Test
    void shouldExecuteLuaScriptOnDecrement() {
        adapter.add("tenant-a", -1L);

        // negative delta is forwarded to Redis (Redis handles HINCRBY with negative values)
        verify(commands).eval(any(String.class), eq(ScriptOutputType.INTEGER),
            any(String[].class), any(String[].class));
    }

    // --- totalInFlight ---

    @Test
    void shouldReturnTotalInFlight() {
        when(commands.get(TOTAL_KEY)).thenReturn("42");

        assertThat(adapter.totalInFlight()).isEqualTo(42L);
    }

    @Test
    void shouldReturnZeroWhenTotalKeyAbsent() {
        when(commands.get(TOTAL_KEY)).thenReturn(null);

        assertThat(adapter.totalInFlight()).isEqualTo(0L);
    }

    @Test
    void shouldReturnZeroWhenTotalNegative() {
        // Negative total can theoretically occur from missed increments after a Redis restart
        when(commands.get(TOTAL_KEY)).thenReturn("-5");

        assertThat(adapter.totalInFlight()).isEqualTo(0L);
    }

    // --- rebuild ---

    @Test
    void shouldDeleteHashAndRepopulateOnRebuild() {
        adapter.rebuild(Map.of("tenant-a", 3, "tenant-b", 1));

        verify(commands).del(HASH_KEY);
        // depth=3 rows per key: tenant-a x3, tenant-b x3
        verify(commands, times(6))
            .hincrby(eq(HASH_KEY), anyString(), anyLong());
        verify(commands).set(eq(TOTAL_KEY), anyString());
    }

    @Test
    void shouldSetCorrectTotalOnRebuild() {
        adapter.rebuild(Map.of("tenant-a", 3, "tenant-b", 2));

        // total = 3 + 2 = 5
        verify(commands).set(TOTAL_KEY, "5");
    }

    @Test
    void shouldDeleteHashButSkipRepopulateForEmptySnapshot() {
        adapter.rebuild(Map.of());

        verify(commands).del(HASH_KEY);
        verify(commands).set(TOTAL_KEY, "0");
        // no cell writes for an empty snapshot
        verify(commands, never()).hincrby(anyString(), anyString(), anyLong());
    }

    // --- fallback ---

    @Test
    void shouldReturnZeroOnEstimateCountRedisFailureWithNoFallback() {
        when(commands.hmget(anyString(), any(String[].class)))
            .thenThrow(new RuntimeException("Redis down"));

        assertThat(adapter.estimateCount("tenant-a")).isEqualTo(0L);
    }

    @Test
    void shouldNotThrowOnAddRedisFailureWithNoFallback() {
        doThrow(new RuntimeException("Redis down")).when(commands)
            .eval(any(String.class), eq(ScriptOutputType.INTEGER),
                any(String[].class), any(String[].class));

        // must not propagate the exception
        adapter.add("tenant-a", 1L);
    }
}
