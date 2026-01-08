package com.QueueUp.Backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String key; // S3 Key

    @Column(nullable = false)
    private String name; // Original filename

    @Column(nullable = false)
    private String ext;  // Extension

    // media type
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttachmentCategory category;

    // The link back to the Message
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    @JsonIgnore
    private Message message;
}