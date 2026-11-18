package com.p5auction.item.dto;

import jakarta.validation.constraints.*;
import java.util.*;
public final class ItemDtos {
    private ItemDtos() {}
    public record Write(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=4000) String description,
                        @NotBlank @Size(max=2000) String imageUrl) {}
    public record Freeze(@NotNull UUID auctionId, @NotNull UUID sellerId) {}
    public record View(UUID id, String name, String description, String imageUrl, UUID sellerId, UUID frozenBy) {}
    public record PageView(List<View> content, long totalElements, int page, int size) {}
}
