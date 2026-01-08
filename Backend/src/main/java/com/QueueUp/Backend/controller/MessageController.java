package com.QueueUp.Backend.controller;

import com.QueueUp.Backend.dto.SendMessageDto;
import com.QueueUp.Backend.model.Message;
import com.QueueUp.Backend.service.MessageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody SendMessageDto request, HttpServletRequest httpRequest) {
        try {
            Long senderId = (Long) httpRequest.getAttribute("userId");
            Message newMessage = messageService.sendMessage(senderId, request);
            return ResponseEntity.status(201).body(Map.of("success", true, "message", newMessage));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/conversation/{userId}")
    public ResponseEntity<?> getConversation(@PathVariable Long userId, HttpServletRequest httpRequest) {
        try {
            Long currentUserId = (Long) httpRequest.getAttribute("userId");
            List<Message> messages = messageService.getConversation(currentUserId, userId);

            return ResponseEntity.ok(Map.of("success", true, "messages", messages));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Internal server error"));
        }
    }
}