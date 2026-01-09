package com.QueueUp.Backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user")
@Data
@NoArgsConstructor
@AllArgsConstructor

// Prevent infinite recursion in Lombok equals/hashCode
@EqualsAndHashCode(exclude = {"likes", "dislikes", "matches", "topArtists", "topTracks", "savedTracks", "followedArtists"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("_id")
    private Long id;

    // Basic Info
    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    @JsonIgnore
    private String password;

    @Column(nullable = false)
    private Integer age;

    @Column(columnDefinition = "TEXT")
    private String bio = "";

    @Column(columnDefinition = "TEXT")
    private String image = "";

    @Column(columnDefinition = "boolean default false")
    private Boolean isBot = false;

    // Spotify Auth Tokens
    private String spotifyId;

    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String spotifyAccessToken;

    @Column(columnDefinition = "TEXT")
    @JsonIgnore
    private String spotifyRefreshToken;

    private LocalDateTime spotifyTokenExpiresAt;

    // MUSIC DATA
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "user_top_artists",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "artist_id")
    )
    private Set<Artist> topArtists = new HashSet<>();

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "user_top_tracks",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "track_id")
    )
    private Set<Track> topTracks = new HashSet<>();


    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "user_saved_tracks",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "track_id")
    )
    private Set<Track> savedTracks = new HashSet<>();

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "user_followed_artists",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "artist_id")
    )
    private Set<Artist> followedArtists = new HashSet<>();


    // SOCIAL GRAPH

    @JsonIgnore
    @ManyToMany
    @JoinTable(
            name = "user_likes",
            joinColumns = @JoinColumn(name = "liker_id"),
            inverseJoinColumns = @JoinColumn(name = "liked_id")
    )
    private Set<User> likes = new HashSet<>();

    @JsonIgnore
    @ManyToMany
    @JoinTable(
            name = "user_dislikes",
            joinColumns = @JoinColumn(name = "disliker_id"),
            inverseJoinColumns = @JoinColumn(name = "disliked_id")
    )
    private Set<User> dislikes = new HashSet<>();

    @JsonIgnore
    @ManyToMany
    @JoinTable(
            name = "user_matches",
            joinColumns = @JoinColumn(name = "user_a_id"),
            inverseJoinColumns = @JoinColumn(name = "user_b_id")
    )
    private Set<User> matches = new HashSet<>();

    // TIMESTAMPS
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