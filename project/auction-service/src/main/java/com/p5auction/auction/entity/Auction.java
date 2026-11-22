package com.p5auction.auction.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="auctions", uniqueConstraints=@UniqueConstraint(columnNames={"sellerId","creationKey"}))
public class Auction {
    @Id public UUID id;
    @Column(nullable=false) public UUID itemId;
    @Column(nullable=false) public UUID sellerId;
    @Column(nullable=false, length=100) public String creationKey;
    @Column(nullable=false) public long startingPrice;
    @Column(nullable=false) public long minimumIncrement;
    @Column(nullable=false) public Instant endsAt;
    @Column(nullable=false) public Instant createdAt;
    @Column(nullable=false, length=20) public String status;
    @Column(length=200) public String failure;
    @Column(length=120) public String itemName;
    @Column(length=4000) public String itemDescription;
    @Column(length=2000) public String imageUrl;
    @Column(nullable=false) public long currentPrice;
    public UUID leadingBidder;
    @Column(nullable=false) public long version;
    protected Auction() {}
    public Auction(UUID seller, UUID item, String key, long start, long increment, Instant end) {
        id=UUID.randomUUID(); sellerId=seller; itemId=item; creationKey=key;
        startingPrice=start; currentPrice=start; minimumIncrement=increment; endsAt=end;
        createdAt=Instant.now(); status="PREPARING";
    }
}
