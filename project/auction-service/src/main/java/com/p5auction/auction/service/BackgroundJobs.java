package com.p5auction.auction.service;

import com.p5auction.auction.repository.AuctionRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
@Component
@EnableScheduling
public class BackgroundJobs {
    private final AuctionRepository auctions;
    private final AuctionService service;
    private final AuctionTransactions tx;
    private final OutboxDispatcher dispatcher;
    public BackgroundJobs(AuctionRepository auctions,AuctionService service,AuctionTransactions tx,OutboxDispatcher dispatcher) {
        this.auctions=auctions;this.service=service;this.tx=tx;this.dispatcher=dispatcher;
    }
    @Scheduled(fixedDelay=1000) public void closeExpired() {
        for(var a:auctions.findTop50ByStatusAndEndsAtBefore("ACTIVE",Instant.now())) tx.end(a.id);
    }
    @Scheduled(fixedDelay=3000) public void recoverCreations() {
        for(var a:auctions.findTop20ByStatusOrderByCreatedAtAsc("PREPARING")) service.resume(a);
    }
    @Scheduled(fixedDelay=500) public void deliverEvents() { dispatcher.deliver(); }
}
