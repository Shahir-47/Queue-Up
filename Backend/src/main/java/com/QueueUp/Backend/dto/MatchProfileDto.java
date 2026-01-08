package com.QueueUp.Backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchProfileDto {
    @JsonProperty("_id")
    private Long id;

    private String name;
    private String image;
    private Integer age;
    private String bio;
    private int score;

    private List<ItemDto> commonArtists;
    private List<ItemDto> commonTracks;
    private List<ItemDto> commonSaved;
    private List<ItemDto> commonFollowed;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDto {
        private String id;
        private String name;
        private String imageUrl;
    }
}