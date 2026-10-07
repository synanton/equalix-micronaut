package org.synanton.equalix.adapter.in.kafka;

import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.Topic;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.synanton.equalix.domain.port.in.TaskIngestionPort;

/**
 * Consumes raw task messages from Kafka and hands them off to the ingestion port.
 * Offsets commit after successful processing (at-least-once); a thrown exception leaves the
 * offset uncommitted for redelivery. Empty payloads are dropped (committed, same as the oracle).
 */
@Slf4j
@Singleton
@Requires(property = "kafka.consumers.default.bootstrap-servers")
@KafkaListener(groupId = "${kafka.consumers.default.group-id:equalix-ingestion}")
public class TaskIngestionKafkaConsumer {

    @Inject
    public TaskIngestionKafkaConsumer(TaskIngestionPort ingestionPort) {
        this.ingestionPort = ingestionPort;
    }

    private final TaskIngestionPort ingestionPort;

    @Topic("${app.kafka.topics.ingestion:equalix-tasks}")
    public void consume(@KafkaKey @Nullable String key, @Nullable byte[] payload) {
        try {
            String fairnessKey = key != null ? key : "default";

            if (payload == null || payload.length == 0) {
                log.warn("Received empty payload from Kafka fairnessKey={}", fairnessKey);
                return;
            }

            ingestionPort.createTask(fairnessKey, new BigDecimal("1.0"), payload, false, null, null, false);

            log.debug("Ingested Kafka task for fairnessKey={}", fairnessKey);
        } catch (Exception ex) {
            log.error("Failed to process Kafka message fairnessKey={}: {}", key, ex.getMessage(), ex);
            throw ex;
        }
    }
}
