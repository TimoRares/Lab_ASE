package com.instakill.notification;

import com.instakill.post.domain.PostCreatedEvent;
import com.instakill.user.domain.User;
import com.instakill.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class PostNotificationListenerTest {

    private final UserRepository users = mock(UserRepository.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    private final PostNotificationListener listener = new PostNotificationListener(users, sender);

    @Test
    void sendsUsernamePostIdAndTimestamp() {
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-12T10:15:30Z");
        when(users.findById(authorId)).thenReturn(Optional.of(
                new User(authorId, "alice", "alice@example.com", "hash", now)));

        listener.onPostCreated(new PostCreatedEvent(postId, authorId, now));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(sender).send(anyString(), message.capture());
        assertThat(message.getValue())
                .contains("alice")
                .contains(postId.toString())
                .contains("2026-11-12T10:15:30Z");
    }

    @Test
    void senderFailureDoesNotPropagate() {
        UUID authorId = UUID.randomUUID();
        when(users.findById(authorId)).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("boom")).when(sender).send(anyString(), anyString());

        listener.onPostCreated(new PostCreatedEvent(UUID.randomUUID(), authorId, Instant.now()));
        // daca ajunge aici, exceptia a fost inghitita
    }
}
