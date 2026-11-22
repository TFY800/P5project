package com.p5auction.auction.service;

import com.p5auction.auction.repository.OutboxRepository;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class OutboxDispatcher {
    private final OutboxRepository events;
    private final RestClient http;
    private final String notificationUrl;
    private final String key;
    public OutboxDispatcher(OutboxRepository events,RestClient http,@Value("${app.notification-url}") String url,
                            @Value("${app.internal-secret}") String key) {
        this.events=events;this.http=http;this.notificationUrl=url;this.key=key;
    }
    @Transactional
    public void deliver() {
        // SKIP LOCKED allows another dispatcher without double-claiming; crashes may still cause duplicates.
        for(var event:events.pending()) {
            try {
                http.post().uri(notificationUrl+"/internal/events").header("X-Internal-Key",key)
                    .contentType(MediaType.APPLICATION_JSON).body(event.payload).retrieve().toBodilessEntity();
                event.delivered=true;
            } catch(Exception e) {
                event.attempts=Math.min(event.attempts+1,1000000);
                event.nextAttempt=Instant.now().plusSeconds(Math.min(60,1L<<Math.min(event.attempts,6)));
            }
        }
    }
}
