package com.QueueUp.Backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Track {

    @Id
    @EqualsAndHashCode.Include
    private String spotifyId;

    private String name;
    private String artistString;
    private String imageUrl;
}