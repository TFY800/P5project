package com.p5auction.item.entity;

import jakarta.persistence.*;
import java.util.UUID;
@Entity
@Table(name="items")
public class Item {
    @Id public UUID id;
    @Column(nullable=false, length=120) public String name;
    @Column(nullable=false, length=4000) public String description;
    @Column(nullable=false, length=2000) public String imageUrl;
    @Column(nullable=false) public UUID sellerId;
    public UUID frozenBy;
    @Column(length=100) public String creationKey;
    protected Item() {}
    public Item(UUID sellerId) { this.id = UUID.randomUUID(); this.sellerId = sellerId; }
}
