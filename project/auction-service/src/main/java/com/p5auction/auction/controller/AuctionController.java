package com.p5auction.auction.controller;

import com.p5auction.auction.dto.AuctionDtos.*;
import com.p5auction.auction.service.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auctions")
public class AuctionController {
    private final AuctionService service;
    private final AuctionTransactions tx;
    public AuctionController(AuctionService service,AuctionTransactions tx) { this.service=service;this.tx=tx; }
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public View create(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Create request) {
        return service.create(UUID.fromString(jwt.getSubject()),key,request);
    }
    @PostMapping("/{id}/bids") @ResponseStatus(HttpStatus.CREATED)
    public BidView bid(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody PlaceBid request) {
        AuctionService.key(key);return tx.bid(id,UUID.fromString(jwt.getSubject()),key,request.amount());
    }
    @GetMapping("/{id}") public Snapshot snapshot(@PathVariable UUID id) { return tx.snapshot(id); }
    @GetMapping("/{id}/bids") public BidPage history(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {return tx.history(id,page,size);}
    @GetMapping public PageView list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        if(q.length()>120)throw new IllegalArgumentException("Search too long");return tx.list(null,"all",q,page,size);
    }
    @GetMapping({"/mine","/participated","/won"}) public PageView personal(@AuthenticationPrincipal Jwt jwt,jakarta.servlet.http.HttpServletRequest request,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        String path=request.getRequestURI();return tx.list(UUID.fromString(jwt.getSubject()),path.substring(path.lastIndexOf('/')+1),"",page,size);
    }
}
