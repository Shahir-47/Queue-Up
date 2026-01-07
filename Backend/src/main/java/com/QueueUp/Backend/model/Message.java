package com.QueueUp.Backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Link to the User who sent it
    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    // Link to the User who received it
    @ManyToOne
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    // TEXT allows for long messages
    @Column(columnDefinition = "TEXT")
    private String content;

    // One Message can have Many Attachments
    // "mappedBy" tells Hibernate the relationship is owned by the Attachment side
    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Attachment> attachments = new ArrayList<>();

    // Link Previews are just simple strings, so we use ElementCollection
    @ElementCollection
    @CollectionTable(name = "message_link_previews", joinColumns = @JoinColumn(name = "message_id"))
    @Column(name = "preview_url")
    private List<String> linkPreviews = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
}