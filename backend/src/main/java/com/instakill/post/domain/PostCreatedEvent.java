package com.instakill.post.domain;

import java.time.Instant;
import java.util.UUID;

/** Publicat dupa ce o postare noua a fost creata. */
public record PostCreatedEvent(UUID postId, UUID authorId, Instant occurredAt) {
    public PostCreatedEvent {
        if (postId == null || authorId == null || occurredAt == null) {
            throw new IllegalArgumentException("postId, authorId and occurredAt must not be null");
        }
    }
}
