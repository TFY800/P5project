package com.p5auction.auction.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="outbox")
public class OutboxEvent {
    @Id public UUID id;
    @Column(nullable=false, columnDefinition="text") public String payload;
    @Column(nullable=false) public Instant createdAt;
    @Column(nullable=false) public Instant nextAttempt;
    @Column(nullable=false) public boolean delivered;
    @Column(nullable=false) public int attempts;
    protected OutboxEvent() {}
    public OutboxEvent(UUID id, String payload) {
        this.id=id;this.payload=payload;createdAt=Instant.now();nextAttempt=createdAt;
    }
}
