package org.synanton.equalix.adapter.out.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.synanton.equalix.config.properties.ExecutorProperties;
import org.synanton.equalix.domain.service.DispatchAckService;

/**
 * Micronaut port of the oracle's {@code HttpRemoteExecutorAdapterTest}. The adapter under test
 * now uses the JDK HTTP client instead of Spring's {@code WebClient}, so the stub is a real
 * (in-JDK) HTTP server instead of an exchange function. Same behaviors: path, method,
 * content type, envelope layout, ack-on-2xx, silence on 5xx/network failure.
 */
class HttpRemoteExecutorAdapterTest {

    private DispatchAckService dispatchAckService;
    private HttpServer stub;
    private final BlockingQueue<RecordedRequest> requests = new ArrayBlockingQueue<>(16);
    private final AtomicInteger status = new AtomicInteger(200);

    private record RecordedRequest(String method, String path, String contentType, byte[] body) {
    }

    @BeforeEach
    void setUp() throws Exception {
        dispatchAckService = mock(DispatchAckService.class);
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            requests.offer(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                body));
            byte[] empty = new byte[0];
            exchange.sendResponseHeaders(status.get(), empty.length);
            exchange.getResponseBody().write(empty);
            exchange.close();
        });
        stub.start();
    }

    @AfterEach
    void tearDown() {
        stub.stop(0);
    }

    private HttpRemoteExecutorAdapter adapter() {
        ExecutorProperties props = new ExecutorProperties();
        props.setBaseUrl("http://127.0.0.1:" + stub.getAddress().getPort());
        props.setConnectTimeoutMs(2000);
        props.setReadTimeoutMs(5000);
        return new HttpRemoteExecutorAdapter(props, dispatchAckService);
    }

    @Test
    void shouldPostToCorrectPathWithBinaryContentType() throws Exception {
        HttpRemoteExecutorAdapter adapter = adapter();
        UUID id = UUID.fromString("00000000-0000-0000-0000-00000000000a");

        adapter.send(id, new byte[]{1, 2, 3}, null);

        RecordedRequest request = requests.poll(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.path()).isEqualTo("/tasks/" + id + "/execute");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.contentType()).startsWith("application/octet-stream");
        verify(dispatchAckService, timeout(1000)).markCommitted(id);
    }

    @Test
    void shouldNotThrowWhenExecutorReturns5xx() throws Exception {
        status.set(500);
        HttpRemoteExecutorAdapter adapter = adapter();

        UUID id = UUID.randomUUID();
        assertThatCode(() -> adapter.send(id, new byte[]{1}, null))
            .doesNotThrowAnyException();
        assertThat(requests.poll(2, TimeUnit.SECONDS)).isNotNull();
        verifyNoInteractions(dispatchAckService);
    }

    @Test
    void shouldNotThrowWhenExecutorConnectionFails() {
        stub.stop(0);
        HttpRemoteExecutorAdapter adapter = adapter();

        assertThatCode(() -> adapter.send(UUID.randomUUID(), new byte[]{1}, null))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldEncodeEnvelopeWithTaskIdPayloadAndPreviousResult() throws Exception {
        HttpRemoteExecutorAdapter adapter = adapter();
        adapter.send(UUID.randomUUID(), new byte[]{1, 2, 3}, new byte[]{7, 8});

        RecordedRequest request = requests.poll(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        // 16 (uuid) + 4 (payload len) + 3 (payload) + 4 (prev len) + 2 (prev) = 29
        assertThat(request.body()).hasSize(29);
    }

    @Test
    void shouldDecodeEnvelopeLayout() throws Exception {
        HttpRemoteExecutorAdapter adapter = adapter();
        UUID id = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        byte[] payload = {1, 2, 3};
        byte[] prev = {7, 8};
        adapter.send(id, payload, prev);

        RecordedRequest request = requests.poll(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        byte[] body = request.body();
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (body[i] & 0xFF);
            lsb = (lsb << 8) | (body[8 + i] & 0xFF);
        }
        assertThat(new UUID(msb, lsb)).isEqualTo(id);
        int payloadLen = ((body[16] & 0xFF) << 24) | ((body[17] & 0xFF) << 16)
            | ((body[18] & 0xFF) << 8) | (body[19] & 0xFF);
        assertThat(payloadLen).isEqualTo(3);
        assertThat(new String(body, 20, 3, StandardCharsets.ISO_8859_1))
            .isEqualTo(new String(payload, StandardCharsets.ISO_8859_1));
        int prevLen = ((body[23] & 0xFF) << 24) | ((body[24] & 0xFF) << 16)
            | ((body[25] & 0xFF) << 8) | (body[26] & 0xFF);
        assertThat(prevLen).isEqualTo(2);
    }
}
