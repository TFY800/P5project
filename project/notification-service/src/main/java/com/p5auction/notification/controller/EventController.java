package com.p5auction.notification.controller;

import com.p5auction.notification.dto.NotificationDtos.Event;
import com.p5auction.notification.service.SubscriptionHub;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
public class EventController {
    private final SubscriptionHub hub;
    public EventController(SubscriptionHub hub) {this.hub=hub;}
    @PostMapping("/internal/events") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receive(@Valid @RequestBody Event event) {hub.publish(event);}
}
