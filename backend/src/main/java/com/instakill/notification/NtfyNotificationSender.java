package com.instakill.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Trimite notificari catre ntfy.sh printr-un POST simplu: corpul este mesajul,
 * iar titlul merge in header-ul {@code Title}. Apelul se face de pe thread-ul
 * asincron al listener-ului, deci nu blocheaza request-ul care a creat postarea.
 */
@Component
public class NtfyNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NtfyNotificationSender.class);

    private final NotificationProperties properties;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public NtfyNotificationSender(NotificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(String title, String message) {
        if (!properties.enabled()) {
            log.debug("Notificari dezactivate (instakill.notifications.topic este gol)");
            return;
        }
        try {
            String base = properties.baseUrl().replaceAll("/+$", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/" + properties.topic().trim()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Title", title)
                    .header("Tags", "memo")
                    .POST(HttpRequest.BodyPublishers.ofString(message, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                log.warn("ntfy a raspuns cu status {}", response.statusCode());
            } else {
                log.info("Notificare trimisa la ntfy (topic={})", properties.topic());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Trimiterea notificarii a fost intrerupta");
        } catch (IOException | RuntimeException e) {
            // O notificare esuata nu trebuie sa afecteze niciodata crearea postarii
            log.warn("Nu s-a putut trimite notificarea: {}", e.toString());
        }
    }
}
