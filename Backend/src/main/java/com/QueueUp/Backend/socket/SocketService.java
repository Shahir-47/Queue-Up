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
        logger.info("User connected: {}", userId);
    }

    public void removeSession(WebSocketSession session) {
        userSessions.values().remove(session);
        logger.info("User disconnected");
    }

    public void sendMessageToUser(Long userId, String eventName, Object payload) {
        WebSocketSession session = userSessions.get(userId);

        if (session != null && session.isOpen()) {
            try {
                // Wrap the event and payload in a proper Map
                Map<String, Object> socketMessage = Map.of(
                        "type", eventName,
                        "payload", payload
                );

                // Convert the WHOLE thing to JSON safely
                String json = objectMapper.writeValueAsString(socketMessage);

                session.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                logger.error("Error sending message");
            }
        }
        }

    // Broadcast to EVERYONE
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