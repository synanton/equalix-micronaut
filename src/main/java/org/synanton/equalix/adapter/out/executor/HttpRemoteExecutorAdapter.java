package org.synanton.equalix.adapter.out.executor;

import jakarta.inject.Singleton;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.synanton.equalix.config.properties.ExecutorProperties;
import org.synanton.equalix.domain.port.out.RemoteExecutorPort;
import org.synanton.equalix.domain.service.DispatchAckService;

/**
 * Sends task payloads to the remote executor via HTTP.
 * Fire-and-forget: completion is reported back via the TaskCompletionController webhook.
 *
 * <p>Micronaut port: plain JDK {@link HttpClient} with {@code sendAsync} preserves the oracle's
 * non-blocking semantics (the dispatch transaction never waits for the executor) without coupling
 * the wire format to any framework client.
 */
@Slf4j
@Singleton
public class HttpRemoteExecutorAdapter implements RemoteExecutorPort {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final long readTimeoutMs;
    private final DispatchAckService dispatchAckService;

    public HttpRemoteExecutorAdapter(ExecutorProperties executorProperties, DispatchAckService dispatchAckService) {
        this.httpClient = HttpClient.newBuilder()
            // HTTP/1.1, no h2c upgrade dance: stub/simple executors speak plain HTTP/1.1,
            // and a failed upgrade round-trip would silently drop a fire-and-forget send.
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(executorProperties.getConnectTimeoutMs()))
            .build();
        this.baseUrl = executorProperties.getBaseUrl();
        this.readTimeoutMs = executorProperties.getReadTimeoutMs();
        this.dispatchAckService = dispatchAckService;
    }

    @Override
    public void send(UUID taskId, byte[] payload, @Nullable byte[] previousResult) {
        byte[] body = buildBody(taskId, payload, previousResult);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/tasks/" + taskId + "/execute"))
            .timeout(Duration.ofMillis(readTimeoutMs))
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
            .whenComplete((response, error) -> {
                if (error != null) {
                    log.error("Failed to send task {} to remote executor: {}", taskId, error.getMessage());
                    return;
                }
                int status = response.statusCode();
                log.debug("Task {} accepted by remote executor: {}", taskId, status);
                if (status >= 200 && status < 300) {
                    try {
                        dispatchAckService.markCommitted(taskId);
                    } catch (RuntimeException ex) {
                        log.error("Failed to mark task {} committed: {}", taskId, ex.getMessage());
                    }
                }
            });
    }

    private byte[] buildBody(UUID taskId, byte[] payload, @Nullable byte[] previousResult) {
        // Simple concatenation: first 16 bytes = taskId UUID, next 4 bytes = payload length, then payload,
        // then 4 bytes = previousResult length (0 if null), then previousResult
        int prevLen = previousResult != null ? previousResult.length : 0;
        byte[] body = new byte[16 + 4 + payload.length + 4 + prevLen];
        writeUuid(taskId, body, 0);
        writeInt(payload.length, body, 16);
        System.arraycopy(payload, 0, body, 20, payload.length);
        writeInt(prevLen, body, 20 + payload.length);
        if (previousResult != null) {
            System.arraycopy(previousResult, 0, body, 24 + payload.length, prevLen);
        }
        return body;
    }

    private void writeUuid(UUID uuid, byte[] dest, int offset) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int ii = 0; ii < 8; ii++) {
            dest[offset + ii] = (byte) (msb >>> (56 - 8 * ii));
            dest[offset + 8 + ii] = (byte) (lsb >>> (56 - 8 * ii));
        }
    }

    private void writeInt(int value, byte[] dest, int offset) {
        dest[offset] = (byte) (value >>> 24);
        dest[offset + 1] = (byte) (value >>> 16);
        dest[offset + 2] = (byte) (value >>> 8);
        dest[offset + 3] = (byte) value;
    }
}
