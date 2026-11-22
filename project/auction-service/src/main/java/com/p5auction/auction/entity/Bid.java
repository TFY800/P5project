package com.p5auction.auction.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="bids", uniqueConstraints=@UniqueConstraint(columnNames={"auctionId","bidderId","idempotencyKey"}))
public class Bid {
    @Id public UUID id;
    @Column(nullable=false) public UUID auctionId;
    @Column(nullable=false) public UUID bidderId;
    @Column(nullable=false) public long amount;
    @Column(nullable=false, length=100) public String idempotencyKey;
    @Column(nullable=false) public Instant createdAt;
    @Column(nullable=false) public long version;
    protected Bid() {}
    public Bid(Auction auction, UUID bidder, long amount, String key) {
        id=UUID.randomUUID();auctionId=auction.id;bidderId=bidder;this.amount=amount;
        idempotencyKey=key;createdAt=Instant.now();version=auction.version;
    }
}
