package com.QueueUp.Backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Artist {

    @Id
    private String spotifyId; // We use the real Spotify ID as the database Primary Key

    private String name;
    private String imageUrl;
}