package com.p5auction.auction.repository;

import com.p5auction.auction.entity.OutboxEvent;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface OutboxRepository extends JpaRepository<OutboxEvent,UUID> {
    @Query(value="select * from outbox where not delivered and next_attempt <= now() order by created_at limit 5 for update skip locked",nativeQuery=true)
    List<OutboxEvent> pending();
}
