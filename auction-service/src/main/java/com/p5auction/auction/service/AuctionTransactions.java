package com.p5auction.auction.service;

import com.p5auction.auction.dto.AuctionDtos.*;
import com.p5auction.auction.entity.*;
import com.p5auction.auction.repository.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuctionTransactions {
    private final AuctionRepository auctions;
    private final BidRepository bids;
    private final OutboxRepository outbox;
    private final JsonMapper json = JsonMapper.builder().build();
    public AuctionTransactions(AuctionRepository auctions,BidRepository bids,OutboxRepository outbox) {
        this.auctions=auctions;this.bids=bids;this.outbox=outbox;
    }
    @Transactional
    public Auction reserve(UUID seller,String key,Create request) {
        var existing=auctions.findBySellerIdAndCreationKey(seller,key);
        if(existing.isPresent()) {
            Auction a=existing.get();
            if(!a.itemId.equals(request.itemId()) || a.startingPrice!=request.startingPrice()
                || a.minimumIncrement!=request.minimumIncrement() || !a.endsAt.equals(request.endsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key already used for different auction details");
            return a;
        }
        if(!request.endsAt().isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"End time must be in the future");
        return auctions.saveAndFlush(new Auction(seller,request.itemId(),key,request.startingPrice(),request.minimumIncrement(),request.endsAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
    }
    @Transactional
    public void activate(UUID id,ItemView item) {
        Auction a=locked(id);
        if(!a.status.equals("PREPARING")) return;
        if(!a.itemId.equals(item.id()) || !a.sellerId.equals(item.sellerId()) || !a.id.equals(item.frozenBy()))
            throw new IllegalStateException("Unexpected item freeze response");
        a.itemName=item.name();a.itemDescription=item.description();a.imageUrl=item.imageUrl();a.status="ACTIVE";
        if(!a.endsAt.isAfter(Instant.now())) endLocked(a);
    }
    @Transactional
    public void fail(UUID id,String reason) {
        Auction a=locked(id);if(a.status.equals("PREPARING")) {a.status="FAILED";a.failure=reason;}
    }
    @Transactional
    public BidView bid(UUID id,UUID bidder,String key,long amount) {
        // Both bidding and finalization acquire this same row lock before checking server time.
        Auction a=locked(id);
        var previous=bids.findByAuctionIdAndBidderIdAndIdempotencyKey(id,bidder,key);
        if(previous.isPresent()) {
            if(previous.get().amount!=amount) throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key already used for a different amount");
            return bidView(previous.get());
        }
        if(!a.status.equals("ACTIVE") || !a.endsAt.isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Auction is not accepting bids");
        if(a.sellerId.equals(bidder)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Sellers cannot bid on their own auctions");
        long minimum=a.leadingBidder==null?a.startingPrice:a.currentPrice+a.minimumIncrement;
        if(amount<minimum) throw new ResponseStatusException(HttpStatus.CONFLICT,"Bid must be at least "+minimum+" HUF");
        a.currentPrice=amount;a.leadingBidder=bidder;a.version++;
        Bid bid=bids.save(new Bid(a,bidder,amount,key));
        event(a,"price_updated");
        return bidView(bid);
    }
    @Transactional
    public void end(UUID id) {
        Auction a=locked(id);
        if(a.status.equals("ACTIVE") && !a.endsAt.isAfter(Instant.now())) endLocked(a);
    }
    private void endLocked(Auction a) { a.status="ENDED";a.version++;event(a,"auction_ended"); }
    private void event(Auction a,String type) {
        UUID eventId=UUID.randomUUID();
        String payload=json.writeValueAsString(new Event(eventId,a.id,a.version,type,a.currentPrice,a.status,a.status.equals("ENDED")?a.leadingBidder:null));
        outbox.save(new OutboxEvent(eventId,payload));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Snapshot snapshot(UUID id) {
        Auction a=auctions.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Auction not found"));
        var page=bids.findByAuctionIdOrderByVersionDesc(id,PageRequest.of(0,50));
        return new Snapshot(view(a),page.stream().map(AuctionTransactions::bidView).toList(),page.getTotalElements());
    }
    public BidPage history(UUID id,int page,int size) {
        if(!auctions.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Auction not found");
        var paging=PageRequest.of(Math.max(0,page),Math.clamp(size,1,100));
        var result=bids.findByAuctionIdOrderByVersionDesc(id,paging);
        return new BidPage(result.stream().map(AuctionTransactions::bidView).toList(),result.getTotalElements(),paging.getPageNumber(),paging.getPageSize());
    }
    public PageView list(UUID user,String mode,String query,int page,int size) {
        var paging=PageRequest.of(Math.max(0,page),Math.clamp(size,1,50),Sort.by(Sort.Direction.DESC,"createdAt"));
        Page<Auction> result=switch(mode) {
            case "mine" -> auctions.findBySellerId(user,paging);
            case "participated" -> auctions.participated(user,paging);
            case "won" -> auctions.findByStatusAndLeadingBidder("ENDED",user,paging);
            default -> auctions.browse(query,paging);
        };
        return new PageView(result.stream().map(AuctionTransactions::view).toList(),result.getTotalElements(),paging.getPageNumber(),paging.getPageSize());
    }
    private Auction locked(UUID id) { return auctions.lock(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Auction not found")); }
    public static View view(Auction a) {
        return new View(a.id,a.itemId,a.sellerId,a.itemName,a.itemDescription,a.imageUrl,a.startingPrice,a.minimumIncrement,
            a.currentPrice,a.leadingBidder==null?a.startingPrice:a.currentPrice+a.minimumIncrement,a.leadingBidder,a.status,a.failure,a.endsAt,a.version,Instant.now());
    }
    private static BidView bidView(Bid b) { return new BidView(b.id,b.bidderId,b.amount,b.createdAt,b.version); }
}
