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

    public MessageService(MessageRepository messageRepository,
                          UserRepository userRepository,
                          Cloudinary cloudinary,
                          SocketService socketService) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.cloudinary = cloudinary;
        this.socketService = socketService;
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
                }
                else if (attInput.getData() != null && attInput.getData().startsWith("data:")) {
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

        // PREPARE SOCKET PAYLOAD
        Map<String, Object> socketPayload = new HashMap<>();
        socketPayload.put("_id", savedMessage.getId());
        socketPayload.put("content", savedMessage.getContent());
        socketPayload.put("senderId", sender.getId());
        socketPayload.put("receiverId", receiver.getId());
        socketPayload.put("createdAt", savedMessage.getCreatedAt().toString());
        socketPayload.put("attachments", savedMessage.getAttachments());

        // Send
        socketService.sendMessageToUser(receiver.getId(), "newMessage", socketPayload);

        return savedMessage;
    }

    public List<Message> getConversation(Long currentUserId, Long otherUserId) {
        return messageRepository.findConversation(currentUserId, otherUserId);
    }
}