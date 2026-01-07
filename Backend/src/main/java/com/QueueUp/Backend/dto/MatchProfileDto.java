package com.QueueUp.Backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class MatchProfileDto {
    private Long id;
    private String name;
    private String image;
    private Integer age;
    private String bio;
    private int score;

    // We return simple lists of objects for the frontend
    private List<ItemDto> commonArtists;
    private List<ItemDto> commonTracks;

    @Data
    public static class ItemDto {
        private String id;   // Spotify ID
        private String name; // Human readable name
        private String imageUrl;

        public ItemDto(String id, String name, String imageUrl) {
            this.id = id;
            this.name = name;
            this.imageUrl = imageUrl;
        }
    }
}