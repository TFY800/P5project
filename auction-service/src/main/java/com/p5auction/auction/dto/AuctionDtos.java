package com.p5auction.auction.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
public final class AuctionDtos {
    private AuctionDtos() {}
    public record Create(@NotNull UUID itemId, @Min(1) @Max(1000000000000L) long startingPrice,
                         @Min(1) @Max(1000000000000L) long minimumIncrement, @NotNull Instant endsAt) {}
    public record PlaceBid(@Min(1) @Max(1000000000000L) long amount) {}
    public record ItemView(UUID id,String name,String description,String imageUrl,UUID sellerId,UUID frozenBy) {}
    public record Freeze(UUID auctionId,UUID sellerId) {}
    public record BidView(UUID id,UUID bidderId,long amount,Instant createdAt,long version) {}
    public record View(UUID id,UUID itemId,UUID sellerId,String name,String description,String imageUrl,
                       long startingPrice,long minimumIncrement,long currentPrice,long nextMinimum,
                       UUID leadingBidder,String status,String failure,Instant endsAt,long version,Instant serverTime) {}
    public record PageView(List<View> content,long totalElements,int page,int size) {}
    public record BidPage(List<BidView> content,long totalElements,int page,int size) {}
    public record Snapshot(View auction,List<BidView> bids,long bidCount) {}
    public record Event(UUID eventId,UUID auctionId,long version,String type,long price,String status,UUID winnerId) {}
}
