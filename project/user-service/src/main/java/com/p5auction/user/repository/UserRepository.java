package com.p5auction.user.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.p5auction.user.entity.UserAccount;
public interface UserRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmail(String email);
}
