package com.p5auction.notification.service;

import com.p5auction.notification.dto.NotificationDtos.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import tools.jackson.databind.json.JsonMapper;

@Component
@EnableScheduling
public class SubscriptionHub extends TextWebSocketHandler {
    private record Client(WebSocketSession socket, Set<UUID> auctions) {}
    private final Map<String,Client> clients = new ConcurrentHashMap<>();
    private final Map<String,Long> lastPong = new ConcurrentHashMap<>();
    private final JsonMapper json = JsonMapper.builder().build();
    @Override public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(1024);
        var safe = new ConcurrentWebSocketSessionDecorator(session, 3000, 65536);
        clients.put(session.getId(),new Client(safe,ConcurrentHashMap.newKeySet()));
        lastPong.put(session.getId(),System.currentTimeMillis());
    }
    @Override protected void handleTextMessage(WebSocketSession session,TextMessage message) throws Exception {
        Client client=clients.get(session.getId());if(client==null)return;
        try {
            Subscription request=json.readValue(message.getPayload(),Subscription.class);
            if(request.auctionId()==null)throw new IllegalArgumentException();
            if("subscribe".equals(request.action())) {
                if(client.auctions().size()>=32 && !client.auctions().contains(request.auctionId()))throw new IllegalArgumentException();
                client.auctions().add(request.auctionId());
                send(client,new TextMessage(json.writeValueAsString(new Ack("subscribed",request.auctionId()))));
            } else if("unsubscribe".equals(request.action())) client.auctions().remove(request.auctionId());
            else throw new IllegalArgumentException();
        } catch(Exception e) { session.close(CloseStatus.BAD_DATA); }
    }
    public void publish(Event event) {
        var message=new TextMessage(json.writeValueAsString(event));
        for(Client client:clients.values()) if(client.auctions().contains(event.auctionId()))send(client,message);
    }
    private void send(Client client,WebSocketMessage<?> message) {
        try { client.socket().sendMessage(message); }
        catch(Exception e) { discard(client.socket()); }
    }
    private void discard(WebSocketSession session) {
        clients.remove(session.getId());lastPong.remove(session.getId());
        try { session.close(); } catch(IOException ignored) {}
    }
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) { discard(session); }
    @Override public void handleTransportError(WebSocketSession session,Throwable error) { discard(session); }
    @Override protected void handlePongMessage(WebSocketSession session,PongMessage message) {lastPong.put(session.getId(),System.currentTimeMillis());}
    @Scheduled(fixedDelay=20000) public void heartbeat() {
        long now=System.currentTimeMillis();
        for(Client client:clients.values()) {
            if(now-lastPong.getOrDefault(client.socket().getId(),0L)>60000) discard(client.socket());
            else send(client,new PingMessage(ByteBuffer.wrap(new byte[]{1})));
        }
    }
}
