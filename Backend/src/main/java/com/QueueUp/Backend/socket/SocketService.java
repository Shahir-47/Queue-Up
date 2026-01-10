package com.QueueUp.Backend.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SocketService {

    private static final Logger logger = LoggerFactory.getLogger(SocketService.class);
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SocketService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void addSession(Long userId, WebSocketSession session) {
        userSessions.put(userId, session);

        // 1. Broadcast to everyone else that this user is now Online
        broadcast("userOnline", userId);

        // 2. Send the list of ALL currently online users to this specific user
        sendMessageToUser(userId, "getOnlineUsers", userSessions.keySet());

        logger.info("User connected: {}", userId);
    }

    public void removeSession(WebSocketSession session) {
        // Find the userId associated with this session
        Long userId = userSessions.entrySet().stream()
                .filter(entry -> entry.getValue().equals(session))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (userId != null) {
            userSessions.remove(userId);
            // Broadcast that this user has gone Offline
            broadcast("userOffline", userId);
            logger.info("User disconnected: {}", userId);
        }
    }

    public void sendMessageToUser(Long userId, String eventName, Object payload) {
        WebSocketSession session = userSessions.get(userId);

        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> socketMessage = Map.of(
                        "type", eventName,
                        "payload", payload
                );
                String json = objectMapper.writeValueAsString(socketMessage);
                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                logger.error("Error sending message");
            }
        }
    }

    public void broadcast(String eventName, Object payload) {
        try {
            Map<String, Object> socketMessage = Map.of(
                    "type", eventName,
                    "payload", payload
            );
            String json = objectMapper.writeValueAsString(socketMessage);

            userSessions.values().forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                    }
                } catch (IOException e) {
                    logger.error("Error broadcasting message", e);
                }
            });
        } catch (IOException e) {
            logger.error("Error serializing broadcast message", e);
        }
    }
}