package com.p5auction.auction.service;

import com.p5auction.auction.dto.AuctionDtos.*;
import com.p5auction.auction.entity.Auction;
import com.p5auction.auction.repository.AuctionRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuctionService {
    private final AuctionTransactions tx;
    private final AuctionRepository auctions;
    private final RestClient http;
    private final String itemUrl;
    private final String internalKey;
    public AuctionService(AuctionTransactions tx,AuctionRepository auctions,RestClient http,
                          @Value("${app.item-url}") String itemUrl,@Value("${app.internal-secret}") String internalKey) {
        this.tx=tx;this.auctions=auctions;this.http=http;this.itemUrl=itemUrl;this.internalKey=internalKey;
    }
    public View create(UUID seller,String key,Create request) {
        key(key);
        Auction a;
        try { a=tx.reserve(seller,key,request); }
        catch(DataIntegrityViolationException e) {
            if(auctions.findBySellerIdAndCreationKey(seller,key).isEmpty())
                throw new ResponseStatusException(HttpStatus.CONFLICT,"This item already has an auction");
            a=tx.reserve(seller,key,request);
        }
        if(a.status.equals("PREPARING")) resume(a);
        return tx.snapshot(a.id).auction();
    }
    // A committed PREPARING row is the recovery record. Never unfreeze after an ambiguous timeout.
    public void resume(Auction a) {
        try {
            ItemView item=http.post().uri(itemUrl+"/internal/items/{id}/freeze",a.itemId)
                .header("X-Internal-Key",internalKey).body(new Freeze(a.id,a.sellerId)).retrieve().body(ItemView.class);
            if(item==null) return;
            tx.activate(a.id,item);
        } catch(HttpClientErrorException e) {
            int status=e.getStatusCode().value();
            // Authentication/configuration failures remain retryable; only definitive business rejection fails creation.
            if(status==403 || status==404 || status==409) tx.fail(a.id,"Item unavailable, not owned by you, or already frozen");
        } catch(RestClientException e) { /* Background recovery retries the same auction ID. */ }
    }
    public static void key(String key) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,100}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key must be 8–100 letters, digits, hyphens or underscores");
    }
}
