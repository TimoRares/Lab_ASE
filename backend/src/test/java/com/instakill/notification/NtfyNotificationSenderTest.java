package com.instakill.notification;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NtfyNotificationSenderTest {

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> title = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            title.set(exchange.getRequestHeaders().getFirst("Title"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void postsMessageToConfiguredTopic() {
        NtfyNotificationSender sender = new NtfyNotificationSender(new NotificationProperties(baseUrl(), "my-topic"));

        sender.send("Instakill: new post", "User: alice");

        assertThat(path.get()).isEqualTo("/my-topic");
        assertThat(title.get()).isEqualTo("Instakill: new post");
        assertThat(body.get()).isEqualTo("User: alice");
    }

    @Test
    void doesNothingWhenTopicIsBlank() {
        NtfyNotificationSender sender = new NtfyNotificationSender(new NotificationProperties(baseUrl(), ""));

        sender.send("t", "m");

        assertThat(path.get()).isNull();
    }

    @Test
    void neverThrowsWhenServiceIsUnreachable() {
        server.stop(0);
        NtfyNotificationSender sender = new NtfyNotificationSender(new NotificationProperties(baseUrl(), "topic"));

        assertThatCode(() -> sender.send("t", "m")).doesNotThrowAnyException();
    }
}
