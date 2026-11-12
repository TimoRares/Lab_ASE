package com.instakill.notification;

import com.instakill.post.domain.PostCreatedEvent;
import com.instakill.user.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reactioneaza la crearea unei postari si trimite o notificare, asincron
 * (pe executorul "notificationExecutor"), dupa commit-ul tranzactiei.
 */
@Component
public class PostNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PostNotificationListener.class);

    private final UserRepository users;
    private final NotificationSender sender;

    public PostNotificationListener(UserRepository users, NotificationSender sender) {
        this.users = users;
        this.sender = sender;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPostCreated(PostCreatedEvent event) {
        try {
            String username = users.findById(event.authorId())
                    .map(user -> user.username())
                    .orElse("unknown");
            sender.send("Instakill: new post", format(username, event));
        } catch (RuntimeException e) {
            log.warn("Notificarea pentru postarea {} a esuat: {}", event.postId(), e.toString());
        }
    }

    static String format(String username, PostCreatedEvent event) {
        return "User: " + username + "\n"
                + "Post ID: " + event.postId() + "\n"
                + "Time: " + event.occurredAt();
    }
}
