package com.QueueUp.Backend.service;

import com.QueueUp.Backend.dto.MatchProfileDto;
import com.QueueUp.Backend.model.Artist;
import com.QueueUp.Backend.model.Track;
import com.QueueUp.Backend.model.User;
import com.QueueUp.Backend.repository.UserRepository;
import com.QueueUp.Backend.socket.SocketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchService {

    private final UserRepository userRepository;
    private final SocketService socketService;
    private final ObjectMapper objectMapper;

    public MatchService(UserRepository userRepository, SocketService socketService, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.socketService = socketService;
        this.objectMapper = objectMapper;
    }

    // SWIPE LOGIC

    @Transactional
    public User swipeRight(Long currentUserId, Long likedUserId) {
        User currentUser = userRepository.findById(currentUserId).orElseThrow();
        User likedUser = userRepository.findById(likedUserId).orElseThrow();

        if (!currentUser.getLikes().contains(likedUser)) {
            currentUser.getLikes().add(likedUser);
            userRepository.save(currentUser);
        }

        if (likedUser.getLikes().contains(currentUser)) {
            currentUser.getMatches().add(likedUser);
            likedUser.getMatches().add(currentUser);
            userRepository.save(currentUser);
            userRepository.save(likedUser);
            notifyMatch(currentUser, likedUser);
        }
        return currentUser;
    }

    private void notifyMatch(User user1, User user2) {
        try {
            Map<String, Object> p1 = new HashMap<>();
            p1.put("_id", user2.getId()); p1.put("name", user2.getName()); p1.put("image", user2.getImage());
            socketService.sendMessageToUser(user1.getId(), "newMatch", objectMapper.writeValueAsString(p1));

            Map<String, Object> p2 = new HashMap<>();
            p2.put("_id", user1.getId()); p2.put("name", user1.getName()); p2.put("image", user1.getImage());
            socketService.sendMessageToUser(user2.getId(), "newMatch", objectMapper.writeValueAsString(p2));
        } catch (Exception e) {}
    }

    @Transactional
    public User swipeLeft(Long currentUserId, Long dislikedUserId) {
        User currentUser = userRepository.findById(currentUserId).orElseThrow();
        User dislikedUser = userRepository.findById(dislikedUserId).orElseThrow();
        if (!currentUser.getDislikes().contains(dislikedUser)) {
            currentUser.getDislikes().add(dislikedUser);
            userRepository.save(currentUser);
        }
        return currentUser;
    }

    // --- SCORING & FETCHING ---

    public List<MatchProfileDto> getUserProfiles(Long currentUserId) {
        User currentUser = userRepository.findById(currentUserId).orElseThrow();

        // Exclude people already interacted with
        List<Long> excludeIds = new ArrayList<>();
        excludeIds.add(currentUserId);
        excludeIds.addAll(currentUser.getLikes().stream().map(User::getId).toList());
        excludeIds.addAll(currentUser.getDislikes().stream().map(User::getId).toList());
        excludeIds.addAll(currentUser.getMatches().stream().map(User::getId).toList());

        List<User> candidates = userRepository.findByIdNotIn(excludeIds);

        // Calculate Scores
        return candidates.stream()
                .map(candidate -> calculateScore(currentUser, candidate))
                .sorted((a, b) -> b.getScore() - a.getScore())
                .collect(Collectors.toList());
    }

    public List<MatchProfileDto> getMatches(Long currentUserId) {
        User currentUser = userRepository.findById(currentUserId).orElseThrow();
        return currentUser.getMatches().stream()
                .map(match -> calculateScore(currentUser, match))
                .sorted((a, b) -> b.getScore() - a.getScore())
                .collect(Collectors.toList());
    }

    private MatchProfileDto calculateScore(User me, User other) {

        List<Artist> commonArtists = findCommonArtists(me.getTopArtists(), other.getTopArtists());
        List<Track> commonTracks = findCommonTracks(me.getTopTracks(), other.getTopTracks());
        List<Track> commonSaved = findCommonTracks(me.getSavedTracks(), other.getSavedTracks());
        List<Artist> commonFollowed = findCommonArtists(me.getFollowedArtists(), other.getFollowedArtists());

        int score = (commonArtists.size() * 3) +
                (commonTracks.size() * 2) +
                (commonSaved.size() * 1) +
                (commonFollowed.size() * 1);

        MatchProfileDto dto = new MatchProfileDto();
        dto.setId(other.getId());
        dto.setName(other.getName());
        dto.setImage(other.getImage());
        dto.setAge(other.getAge());
        dto.setBio(other.getBio());
        dto.setScore(score);

        // Convert to DTOs for Frontend
        dto.setCommonArtists(toArtistDto(commonArtists));
        dto.setCommonTracks(toTrackDto(commonTracks));
        dto.setCommonSaved(toTrackDto(commonSaved));
        dto.setCommonFollowed(toArtistDto(commonFollowed));

        return dto;
    }

    // Helpers for Safe Intersection

    private List<Artist> findCommonArtists(Set<Artist> set1, Set<Artist> set2) {
        if (set1 == null || set2 == null) return new ArrayList<>();
        Set<String> ids1 = set1.stream().map(Artist::getSpotifyId).collect(Collectors.toSet());
        return set2.stream().filter(a -> ids1.contains(a.getSpotifyId())).collect(Collectors.toList());
    }

    private List<Track> findCommonTracks(Set<Track> set1, Set<Track> set2) {
        if (set1 == null || set2 == null) return new ArrayList<>();
        Set<String> ids1 = set1.stream().map(Track::getSpotifyId).collect(Collectors.toSet());
        return set2.stream().filter(t -> ids1.contains(t.getSpotifyId())).collect(Collectors.toList());
    }

    private List<MatchProfileDto.ItemDto> toArtistDto(List<Artist> artists) {
        return artists.stream()
                .map(a -> new MatchProfileDto.ItemDto(a.getSpotifyId(), a.getName(), a.getImageUrl()))
                .collect(Collectors.toList());
    }

    private List<MatchProfileDto.ItemDto> toTrackDto(List<Track> tracks) {
        return tracks.stream()
                .map(t -> new MatchProfileDto.ItemDto(t.getSpotifyId(), t.getName(), t.getImageUrl()))
                .collect(Collectors.toList());
    }
}