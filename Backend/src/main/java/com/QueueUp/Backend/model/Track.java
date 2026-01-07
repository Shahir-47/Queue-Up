package com.QueueUp.Backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Track {

    @Id
    private String spotifyId;

    private String name;
    private String artistString; // e.g., "Drake, Future"
    private String imageUrl;
}