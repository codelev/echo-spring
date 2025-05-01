package echo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RestControllerTest {
    private static final String BODY = "5mb.bin";
    private static final String BODY_SHA1 = "ef13a5226129bcaf68d3456c8ec0bd0f38894c53";
    private static final int REQUESTS = 100;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(REQUESTS);
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    @Value("${server.port}")
    private int port;
    @Value("${spring.application.name}")
    private String appName;
    private static String url;

    @BeforeAll
    void setup() {
        url = String.format("http://localhost:%d%s", port, "/rest/echo");
    }

    @Test
    void get() throws Exception {
        String contentType = "application/json";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", contentType)
                .build();
        sequential(request, contentType);
        parallel(request, contentType);
    }

    @Test
    void postBinary() throws Exception {
        String contentType = "application/octet-stream";
        byte[] body = RestControllerTest.class.getClassLoader().getResourceAsStream(BODY).readAllBytes();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        sequential(request, contentType);
        parallel(request, contentType);
    }

    private void sequential(HttpRequest request, String contentType) throws Exception {
        AtomicLongArray statsMicro = new AtomicLongArray(REQUESTS);
        for (int i = 0; i < REQUESTS; i++) {
            long start = System.nanoTime();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            long end = System.nanoTime();
            assertEquals(200, response.statusCode());
            if (contentType.equals("application/octet-stream")) {
                assertTrue(response.body().contains(BODY_SHA1));
            }
            statsMicro.set(i, (end - start) / 1_000);
        }
        report(statsMicro, request, contentType, "Sequential");
    }

    private void parallel(HttpRequest request, String contentType) throws Exception {
        AtomicLongArray statsMicro = new AtomicLongArray(REQUESTS);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    long start = System.nanoTime();
                    HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                    long end = System.nanoTime();
                    assertEquals(200, response.statusCode());
                    statsMicro.set(index, (end - start) / 1_000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, EXECUTOR);
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        report(statsMicro, request, contentType, "Parallel");
    }

    private void report(AtomicLongArray statsMicro, HttpRequest request, String contentType, String title) {
        double longestMicro = IntStream.range(0, statsMicro.length())
                .mapToLong(statsMicro::get)
                .max()
                .orElseThrow();
        double shortestMicro = IntStream.range(0, statsMicro.length())
                .mapToLong(statsMicro::get)
                .min()
                .orElseThrow();
        double averageMicro = IntStream.range(0, statsMicro.length())
                .mapToLong(statsMicro::get)
                .average()
                .orElseThrow();

        long bodySize = request.bodyPublisher()
                .map(HttpRequest.BodyPublisher::contentLength)
                .orElse(0L);

        StringBuilder result = new StringBuilder();
        result.append(String.format("### %s requests: %s%n%n", title, appName));
        result.append(String.format("`%s %s %s %dMb` x %d%n%n", request.method(), request.uri().toString(), contentType, bodySize / 1_000_000, statsMicro.length()));
        result.append("| Metric               | Value         |\n");
        result.append("|----------------------|---------------|\n");
        result.append(String.format("| Longest response     | %.2f ms       |%n", longestMicro / 1_000));
        result.append(String.format("| Shortest response    | %.2f ms       |%n", shortestMicro / 1_000));
        result.append(String.format("| Average response     | %.2f ms       |%n", averageMicro / 1_000));
        System.out.println(result);
    }
}