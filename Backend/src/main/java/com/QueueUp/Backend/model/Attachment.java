package com.QueueUp.Backend.model;

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
    private String ext;  // Extension (jpg, pdf)

    // Using the Enum we created in Step 1
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttachmentCategory category;

    // The link back to the Message
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id")
    @ToString.Exclude // Prevents logging errors
    private Message message;
}