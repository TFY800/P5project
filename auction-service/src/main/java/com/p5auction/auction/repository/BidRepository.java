package com.p5auction.auction.repository;

import com.p5auction.auction.entity.Bid;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BidRepository extends JpaRepository<Bid,UUID> {
    Optional<Bid> findByAuctionIdAndBidderIdAndIdempotencyKey(UUID auction,UUID bidder,String key);
    Page<Bid> findByAuctionIdOrderByVersionDesc(UUID auction, Pageable page);
}
