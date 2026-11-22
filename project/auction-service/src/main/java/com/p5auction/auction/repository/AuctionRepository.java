package com.p5auction.auction.repository;

import com.p5auction.auction.entity.Auction;
import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface AuctionRepository extends JpaRepository<Auction,UUID> {
    Optional<Auction> findBySellerIdAndCreationKey(UUID sellerId,String creationKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id=:id") Optional<Auction> lock(@Param("id") UUID id);
    List<Auction> findTop20ByStatusOrderByCreatedAtAsc(String status);
    List<Auction> findTop50ByStatusAndEndsAtBefore(String status,Instant now);
    @Query("select a from Auction a where a.status in ('ACTIVE','ENDED') and lower(a.itemName) like lower(concat('%',:q,'%'))")
    Page<Auction> browse(@Param("q") String q,Pageable page);
    Page<Auction> findBySellerId(UUID sellerId, Pageable page);
    @Query("select a from Auction a where exists (select b.id from Bid b where b.auctionId=a.id and b.bidderId=:user)")
    Page<Auction> participated(@Param("user") UUID user, Pageable page);
    Page<Auction> findByStatusAndLeadingBidder(String status, UUID user, Pageable page);
}
