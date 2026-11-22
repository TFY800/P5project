package com.p5auction.item.controller;

import com.p5auction.item.dto.ItemDtos.*;
import com.p5auction.item.service.ItemService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
public class ItemController {
    private final ItemService items;
    public ItemController(ItemService items) { this.items = items; }
    @PostMapping("/api/items") @ResponseStatus(HttpStatus.CREATED)
    public View create(@AuthenticationPrincipal Jwt jwt, @RequestHeader(value="Idempotency-Key", required=false) String key, @Valid @RequestBody Write request) { return items.create(UUID.fromString(jwt.getSubject()),key==null?UUID.randomUUID().toString():key,request); }
    @PutMapping("/api/items/{id}")
    public View update(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Write request) { return items.update(id,UUID.fromString(jwt.getSubject()),request); }
    @GetMapping("/api/items/{id}") public View get(@PathVariable UUID id) { return items.get(id); }
    @GetMapping("/api/items") public PageView list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return items.list(null,page,size); }
    @GetMapping("/api/items/mine") public PageView mine(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return items.list(UUID.fromString(jwt.getSubject()),page,size); }
    @PostMapping("/internal/items/{id}/freeze") public View freeze(@PathVariable UUID id,@Valid @RequestBody Freeze request) { return items.freeze(id,request); }
}
