package com.QueueUp.Backend.dto;

import com.QueueUp.Backend.model.AttachmentCategory;
import lombok.Data;
import java.util.List;

@Data
public class SendMessageDto {
    private String content;
    private Long receiverId;
    private List<AttachmentInput> attachments;
    private List<String> previewUrls;

    @Data
    public static class AttachmentInput {
        private String url;
        private String data;
        private String key;
        private String name;
        private String ext;
        private AttachmentCategory category;
    }
}