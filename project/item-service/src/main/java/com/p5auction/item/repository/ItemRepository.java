package com.p5auction.item.repository;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import com.p5auction.item.entity.Item;
public interface ItemRepository extends JpaRepository<Item, UUID> {
    Optional<Item> findBySellerIdAndCreationKey(UUID sellerId, String creationKey);
    Page<Item> findBySellerId(UUID sellerId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id")
    Optional<Item> lock(@Param("id") UUID id);
}
