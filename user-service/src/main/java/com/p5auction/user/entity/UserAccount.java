package com.p5auction.user.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id public UUID id;
    @Column(nullable = false, unique = true, length = 254) public String email;
    @Column(nullable = false, length = 80) public String displayName;
    @Column(nullable = false, length = 100) public String passwordHash;
    protected UserAccount() {}
    public UserAccount(String email, String displayName, String passwordHash) {
        this.id = UUID.randomUUID(); this.email = email; this.displayName = displayName; this.passwordHash = passwordHash;
    }
}
