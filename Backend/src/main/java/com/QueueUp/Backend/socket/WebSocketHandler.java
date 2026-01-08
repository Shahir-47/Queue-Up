package com.QueueUp.Backend.socket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final SocketService socketService;

    public WebSocketHandler(SocketService socketService) {
        this.socketService = socketService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Extract userId from query params
        Long userId = getUserIdFromSession(session);

        if (userId != null) {
            socketService.addSession(userId, session);
        } else {
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        socketService.removeSession(session);
    }

    private Long getUserIdFromSession(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            return Long.valueOf(UriComponentsBuilder.fromUriString("?" + query)
                    .build()
                    .getQueryParams()
                    .getFirst("userId"));
        } catch (Exception e) {
            return null;
        }
    }
}