package com.p5auction.notification.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;
public final class NotificationDtos {
    private NotificationDtos() {}
    public record Event(@NotNull UUID eventId, @NotNull UUID auctionId, @Min(1) long version,
                        @NotBlank @Pattern(regexp="price_updated|auction_ended") String type,
                        @Min(1) long price, @NotBlank @Pattern(regexp="ACTIVE|ENDED") String status, UUID winnerId) {}
    public record Subscription(String action, UUID auctionId) {}
    public record Ack(String type, UUID auctionId) {}
}
