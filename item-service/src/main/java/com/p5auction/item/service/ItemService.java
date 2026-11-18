package com.p5auction.item.service;

import com.p5auction.item.dto.ItemDtos.*;
import com.p5auction.item.entity.Item;
import com.p5auction.item.repository.ItemRepository;
import java.net.URI;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItemService {
    private final ItemRepository items;
    public ItemService(ItemRepository items) { this.items = items; }
    @Transactional
    public View create(UUID owner, String key, Write request) {
        if (!key.matches("[A-Za-z0-9_-]{8,100}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key");
        var previous=items.findBySellerIdAndCreationKey(owner,key);
        if(previous.isPresent()) {
            Item i=previous.get();
            if(!i.name.equals(request.name().strip()) || !i.description.equals(request.description().strip()) || !i.imageUrl.equals(request.imageUrl()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used for different item details");
            return view(i);
        }
        Item item = new Item(owner); item.creationKey=key; apply(item, request); return view(items.saveAndFlush(item));
    }
    @Transactional
    public View update(UUID id, UUID owner, Write request) {
        Item item = locked(id);
        if (!item.sellerId.equals(owner)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the seller can edit this item");
        if (item.frozenBy != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is frozen for an auction");
        apply(item, request); return view(item);
    }
    @Transactional
    public View freeze(UUID id, Freeze request) {
        Item item = locked(id);
        if (!item.sellerId.equals(request.sellerId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the seller can create its auction");
        if (item.frozenBy != null && !item.frozenBy.equals(request.auctionId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item already belongs to another auction");
        item.frozenBy = request.auctionId(); return view(item);
    }
    public View get(UUID id) { return view(items.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"))); }
    public PageView list(UUID seller, int page, int size) {
        var paging = PageRequest.of(Math.max(0,page), Math.clamp(size,1,50), Sort.by("id"));
        var result = seller == null ? items.findAll(paging) : items.findBySellerId(seller,paging);
        return new PageView(result.stream().map(ItemService::view).toList(), result.getTotalElements(), paging.getPageNumber(), paging.getPageSize());
    }
    private Item locked(UUID id) { return items.lock(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found")); }
    private static void apply(Item item, Write request) {
        URI image = URI.create(request.imageUrl());
        if (!("http".equalsIgnoreCase(image.getScheme()) || "https".equalsIgnoreCase(image.getScheme())) || image.getHost() == null || image.getUserInfo() != null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image URL must be an HTTP or HTTPS URL without credentials");
        item.name = request.name().strip(); item.description = request.description().strip(); item.imageUrl = image.toString();
    }
    private static View view(Item i) { return new View(i.id,i.name,i.description,i.imageUrl,i.sellerId,i.frozenBy); }
}
