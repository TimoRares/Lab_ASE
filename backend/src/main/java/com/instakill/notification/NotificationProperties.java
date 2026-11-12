package com.instakill.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Setari pentru notificarile trimise la ntfy.sh (instakill.notifications.*).
 * Daca {@code topic} este gol, notificarile sunt dezactivate.
 */
@ConfigurationProperties(prefix = "instakill.notifications")
public record NotificationProperties(
        @DefaultValue("https://ntfy.sh") String baseUrl,
        @DefaultValue("") String topic
) {
    public boolean enabled() {
        return topic != null && !topic.isBlank();
    }
}
