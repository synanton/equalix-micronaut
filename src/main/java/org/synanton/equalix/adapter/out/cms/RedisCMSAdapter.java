package org.synanton.equalix.adapter.out.cms;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.synanton.equalix.config.properties.QueueProperties.CmsProperties;
import org.synanton.equalix.config.properties.QueueProperties;
import org.synanton.equalix.domain.port.out.CMSProviderPort;

/**
 * CMSProviderPort backed by a shared Redis hash matrix.
 * All instances read and write the same sketch, providing a consistent global in-flight view.
 *
 * Storage layout: one Redis hash at {@code keyNamespace:v{layout}}, fields named {@code r{row}:c{col}}.
 * A separate key {@code keyNamespace:v{layout}:total} tracks the net sum of all add() deltas. The layout version
 * ({@link CmsKeyHasher#LAYOUT_VERSION}) is part of the key, so instances with different cell layouts never write
 * into each other's sketch during a rolling deploy. The previous layout lived at {@code keyNamespace} itself and
 * can be deleted once every instance runs this version.
 *
 * add() uses a Lua script so all d cell increments and the total counter update are atomic
 * within a single Redis round-trip - no application-side locking required.
 *
 * <p>Micronaut port: Lettuce synchronous commands replace Spring Data Redis. Same keys, same script, same fallback.
 */
@Slf4j
public class RedisCMSAdapter implements CMSProviderPort {

    // KEYS[1]=cms hash key, KEYS[2]=total counter key
    // ARGV[1]=delta, ARGV[2..depth+1]=field names for each row
    private static final String ADD_SCRIPT = """
            local hash      = KEYS[1]
            local total_key = KEYS[2]
            local delta     = tonumber(ARGV[1])
            for i = 2, #ARGV do
                redis.call('HINCRBY', hash, ARGV[i], delta)
            end
            redis.call('INCRBY', total_key, delta)
            return 1
            """;

    private final int width;
    private final int depth;
    private final String hashKey;
    private final String totalKey;
    private final CmsKeyHasher hasher;
    private final boolean fallbackToLocal;
    private final StatefulRedisConnection<String, String> redisConnection;
    /** Warm fallback - only non-null when fallbackToLocal=true. */
    private final CountMinSketchAdapter localFallback;

    public RedisCMSAdapter(QueueProperties props, StatefulRedisConnection<String, String> redisConnection) {
        CmsProperties cms = props.getCms();
        this.width = cms.getWidth();
        this.depth = cms.getDepth();
        this.hashKey = cms.getRedis().getKeyNamespace() + ":v" + CmsKeyHasher.LAYOUT_VERSION;
        this.totalKey = hashKey + ":total";
        this.hasher = new CmsKeyHasher(width, depth);
        this.fallbackToLocal = cms.getRedis().isFallbackToLocal();
        this.redisConnection = redisConnection;
        this.localFallback = fallbackToLocal ? new CountMinSketchAdapter(props) : null;
        log.info("RedisCMSAdapter initialized: hashKey={}, width={}, depth={}, fallback={}",
            hashKey, width, depth, fallbackToLocal);
    }

    @Override
    public void add(String key, long delta) {
        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            String[] keys = {hashKey, totalKey};
            String[] argv = new String[1 + depth];
            argv[0] = String.valueOf(delta);
            int[] cells = hasher.cells(key);
            for (int row = 0; row < depth; row++) {
                argv[1 + row] = fieldName(row, cells[row]);
            }
            commands.eval(ADD_SCRIPT, ScriptOutputType.INTEGER, keys, argv);
        } catch (Exception e) {
            log.warn("Redis CMS add failed for key='{}': {}", key, e.getMessage());
            if (fallbackToLocal) {
                localFallback.add(key, delta);
            }
        }
    }

    @Override
    public long estimateCount(String key) {
        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            String[] fields = new String[depth];
            int[] cells = hasher.cells(key);
            for (int row = 0; row < depth; row++) {
                fields[row] = fieldName(row, cells[row]);
            }
            List<io.lettuce.core.KeyValue<String, String>> values = commands.hmget(hashKey, fields);
            long min = Long.MAX_VALUE;
            for (io.lettuce.core.KeyValue<String, String> kv : values) {
                long v = !kv.hasValue() ? 0L : Long.parseLong(kv.getValue());
                if (v < min) {
                    min = v;
                }
            }
            return Math.max(0L, min == Long.MAX_VALUE ? 0L : min);
        } catch (Exception e) {
            log.warn("Redis CMS estimateCount failed for key='{}': {}", key, e.getMessage());
            return fallbackToLocal ? localFallback.estimateCount(key) : 0L;
        }
    }

    /**
     * Resets the Redis hash and repopulates it from the snapshot.
     * Called by the Watchdog after reconciling client_counts with actual DB state;
     * because the hash is shared, one Watchdog run rebuilds the sketch for all instances at once.
     */
    @Override
    public void rebuild(Map<String, Integer> snapshot) {
        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            commands.del(hashKey);
            if (!snapshot.isEmpty()) {
                snapshot.forEach((fairnessKey, count) -> {
                    int[] cells = hasher.cells(fairnessKey);
                    for (int row = 0; row < depth; row++) {
                        commands.hincrby(hashKey, fieldName(row, cells[row]), count);
                    }
                });
            }
            long total = snapshot.values().stream().mapToLong(Integer::longValue).sum();
            commands.set(totalKey, String.valueOf(total));
            log.info("Redis CMS rebuilt from {} snapshot entries; total={}", snapshot.size(), total);
        } catch (Exception e) {
            log.warn("Redis CMS rebuild failed: {}", e.getMessage());
            if (fallbackToLocal) {
                localFallback.rebuild(snapshot);
            }
        }
    }

    @Override
    public long totalInFlight() {
        try {
            String val = redisConnection.sync().get(totalKey);
            return val == null ? 0L : Math.max(0L, Long.parseLong(val));
        } catch (Exception e) {
            log.warn("Redis CMS totalInFlight failed: {}", e.getMessage());
            return fallbackToLocal ? localFallback.totalInFlight() : 0L;
        }
    }

    private String fieldName(int row, int col) {
        return "r" + row + ":c" + col;
    }
}
