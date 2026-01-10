package com.QueueUp.Backend.service;

import com.QueueUp.Backend.dto.SendMessageDto;
import com.QueueUp.Backend.model.*;
import com.QueueUp.Backend.repository.MessageRepository;
import com.QueueUp.Backend.repository.UserRepository;
import com.QueueUp.Backend.socket.SocketService;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final Cloudinary cloudinary;
    private final SocketService socketService;
    private final OpenAIService openAiService;

    public MessageService(MessageRepository messageRepository,
                          UserRepository userRepository,
                          Cloudinary cloudinary,
                          SocketService socketService, OpenAIService openAiService) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.cloudinary = cloudinary;
        this.socketService = socketService;
        this.openAiService = openAiService;
    }

    @Transactional
    public Message sendMessage(Long senderId, SendMessageDto request) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        User receiver = userRepository.findById(request.getReceiverId())
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        // Create Message Entity
        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(request.getContent());

        if (request.getPreviewUrls() != null) {
            message.setLinkPreviews(request.getPreviewUrls());
        }

        // Process Attachments
        if (request.getAttachments() != null) {
            for (SendMessageDto.AttachmentInput attInput : request.getAttachments()) {
                Attachment attachment = new Attachment();
                attachment.setName(attInput.getName());
                attachment.setExt(attInput.getExt());
                attachment.setCategory(attInput.getCategory());
                attachment.setKey(attInput.getKey() != null ? attInput.getKey() : "");

                // If URL exists use it, else if Base64 exists upload it
                if (attInput.getUrl() != null && !attInput.getUrl().isEmpty()) {
                    attachment.setUrl(attInput.getUrl());
                } else if (attInput.getData() != null && attInput.getData().startsWith("data:")) {
                    // Cloudinary Upload
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> uploadRes = cloudinary.uploader().upload(attInput.getData(), ObjectUtils.asMap(
                                "folder", "chat_attachments",
                                "resource_type", "auto"
                        ));
                        attachment.setUrl((String) uploadRes.get("secure_url"));
                        attachment.setKey((String) uploadRes.get("public_id"));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to upload attachment", e);
                    }
                }

                // Link attachment to message
                attachment.setMessage(message);
                message.getAttachments().add(attachment);
            }
        }

        // Save to DB
        Message savedMessage = messageRepository.save(message);

        // Notify Receiver via Socket
        sendSocketNotification(savedMessage);

        // if replying to a bot
        if (Boolean.TRUE.equals(receiver.getIsBot())) {
            triggerBotReply(receiver, sender);
        }

        return savedMessage;
    }

    // Helper to handle the bot's response asynchronously
    private void triggerBotReply(User bot, User realUser) {
        new Thread(() -> {
            try {
                Map<String, Object> typingPayload = new HashMap<>();
                typingPayload.put("senderId", bot.getId());

                // 1. Determine how long the bot should "fake think" (3-6 seconds)
                long totalDelay = 3000 + (long) (Math.random() * 3000);
                long elapsed = 0;

                // 2. Loop to send "typing" pulses every 2 seconds
                // This prevents the frontend bubble (which has a 3s timeout) from disappearing
                while (elapsed < totalDelay) {
                    socketService.sendMessageToUser(realUser.getId(), "typing", typingPayload);

                    // Sleep for 2 seconds or whatever is left
                    long sleepTime = Math.min(2000, totalDelay - elapsed);
                    Thread.sleep(sleepTime);
                    elapsed += sleepTime;
                }

                // 3. Send one final pulse right before calling OpenAI
                // This keeps the bubble up during the API generation time
                socketService.sendMessageToUser(realUser.getId(), "typing", typingPayload);

                // 4. Fetch conversation history for context
                List<Message> history = messageRepository.findConversation(realUser.getId(), bot.getId());

                // Limit to last 10 for AI context window
                if (history.size() > 10) {
                    history = history.subList(history.size() - 10, history.size());
                }

                // 5. Generate Reply
                String replyContent = openAiService.generateChatReply(bot.getName(), bot.getBio(), history);

                // 6. Save Bot Message
                Message botMsg = new Message();
                botMsg.setSender(bot);
                botMsg.setReceiver(realUser);
                botMsg.setContent(replyContent);
                botMsg.setCreatedAt(java.time.LocalDateTime.now());

                Message savedBotMsg = messageRepository.save(botMsg);

                // 7. Send Socket Event
                sendSocketNotification(savedBotMsg);

            } catch (Exception ignored) {
            }
        }).start();
    }

    // NEW HELPER METHOD TO REMOVE DUPLICATION
    private void sendSocketNotification(Message message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("_id", message.getId());
        payload.put("content", message.getContent());
        payload.put("senderId", message.getSender().getId());
        payload.put("receiverId", message.getReceiver().getId());
        payload.put("createdAt", message.getCreatedAt().toString());
        payload.put("attachments", message.getAttachments());

        socketService.sendMessageToUser(message.getReceiver().getId(), "newMessage", payload);
    }

    public List<Message> getConversation(Long currentUserId, Long otherUserId) {
        return messageRepository.findConversation(currentUserId, otherUserId);
    }
}