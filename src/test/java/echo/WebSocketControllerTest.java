package echo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WebSocketControllerTest {
    private static final StandardWebSocketClient CLIENT = new StandardWebSocketClient();
    @Value("${server.port}")
    private int port;
    private static String url;

    @BeforeAll
    void setup() {
        url = String.format("ws://localhost:%d%s", port, "/ws/echo");
    }

    @Test
    void text() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        String message = String.valueOf(System.currentTimeMillis());
        WebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                session.sendMessage(new TextMessage(message));
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                future.complete(message.getPayload());
                session.close();
            }
        };
        WebSocketSession session = CLIENT.doHandshake(handler, url).get();
        String response = future.get(1, TimeUnit.SECONDS);
        session.close();

        assertEquals(message, response);
    }
}
