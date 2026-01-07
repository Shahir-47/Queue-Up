package com.QueueUp.Backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user") // "User" is a reserved SQL keyword, so we name the table "app_user"
@Data
@NoArgsConstructor
@AllArgsConstructor
// We must exclude these collections from equals/hashCode to prevent infinite memory loops
@EqualsAndHashCode(exclude = {"likes", "dislikes", "matches", "topArtists", "topTracks"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Basic Info ---
    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private Integer age;

    private String bio = "";
    private String image = "";

    // --- Spotify Auth Tokens ---
    // We store the tokens here directly on the user
    private String spotifyId;
    private String spotifyAccessToken;
    private String spotifyRefreshToken;
    private LocalDateTime spotifyTokenExpiresAt;

    // --- RELATIONAL MUSIC DATA ---
    // This creates a "Join Table" called 'user_top_artists' automatically
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "user_top_artists",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "artist_id")
    )
    private Set<Artist> topArtists = new HashSet<>();

    // This creates a "Join Table" called 'user_top_tracks'
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "user_top_tracks",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "track_id")
    )
    private Set<Track> topTracks = new HashSet<>();


    // --- SOCIAL GRAPH  ---
    // Likes
    @ManyToMany
    @JoinTable(
            name = "user_likes",
            joinColumns = @JoinColumn(name = "liker_id"),
            inverseJoinColumns = @JoinColumn(name = "liked_id")
    )
    private Set<User> likes = new HashSet<>();

    // Dislikes
    @ManyToMany
    @JoinTable(
            name = "user_dislikes",
            joinColumns = @JoinColumn(name = "disliker_id"),
            inverseJoinColumns = @JoinColumn(name = "disliked_id")
    )
    private Set<User> dislikes = new HashSet<>();

    // Mutual Likes
    @ManyToMany
    @JoinTable(
            name = "user_matches",
            joinColumns = @JoinColumn(name = "user_a_id"),
            inverseJoinColumns = @JoinColumn(name = "user_b_id")
    )
    private Set<User> matches = new HashSet<>();

    // --- TIMESTAMPS ---
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}