package com.QueueUp.Backend.socket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Objects;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final SocketService socketService;
    private final ObjectMapper objectMapper;

    public WebSocketHandler(SocketService socketService, ObjectMapper objectMapper) {
        this.socketService = socketService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        Long userId = getUserIdFromSession(session);

        if (userId != null) {
            socketService.addSession(userId, session);
        } else {
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) {
        try {
            // 1. Parse incoming JSON
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText();

            // 2. Check for "typing" event
            if ("typing".equals(type)) {
                Long receiverId = node.path("payload").path("receiverId").asLong();
                Long senderId = getUserIdFromSession(session);

                if (senderId != null) {
                    // 3. Relay to receiver via SocketService
                    socketService.sendMessageToUser(receiverId, "typing", Map.of("senderId", senderId));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        socketService.removeSession(session);
    }

    private Long getUserIdFromSession(WebSocketSession session) {
        try {
            String query = Objects.requireNonNull(session.getUri()).getQuery();
            return Long.valueOf(Objects.requireNonNull(UriComponentsBuilder.fromUriString("?" + query)
                    .build()
                    .getQueryParams()
                    .getFirst("userId")));
        } catch (Exception e) {
            return null;
        }
    }
}