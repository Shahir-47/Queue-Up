package com.QueueUp.Backend.service;

import com.QueueUp.Backend.dto.MatchProfileDto;
import com.QueueUp.Backend.model.Artist;
import com.QueueUp.Backend.model.Track;
import com.QueueUp.Backend.model.User;
import com.QueueUp.Backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchService {

    private final UserRepository userRepository;

    public MatchService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // --- SWIPE LOGIC ---

    @Transactional // Ensures DB integrity if something fails halfway
    public User swipeRight(Long currentUserId, Long likedUserId) {
        User currentUser = userRepository.findById(currentUserId).orElseThrow();
        User likedUser = userRepository.findById(likedUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Add to likes if not present
        if (!currentUser.getLikes().contains(likedUser)) {
            currentUser.getLikes().add(likedUser);
            userRepository.save(currentUser);
        }

        // Check for Match (Mutual Like)
        if (likedUser.getLikes().contains(currentUser)) {
            // It's a match!
            currentUser.getMatches().add(likedUser);
            likedUser.getMatches().add(currentUser);

            // Save both
            userRepository.save(currentUser);
            userRepository.save(likedUser);

            // TODO: Socket.IO notification will go here later
            // We are skipping it for now to keep the migration simple
            System.out.println("MATCH! " + currentUser.getName() + " <-> " + likedUser.getName());
        }

        return currentUser;
    }

    @Transactional
    public User swipeLeft(Long currentUserId, Long dislikedUserId) {
        User currentUser = userRepository.findById(currentUserId).orElseThrow();
        User dislikedUser = userRepository.findById(dislikedUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!currentUser.getDislikes().contains(dislikedUser)) {
            currentUser.getDislikes().add(dislikedUser);
            userRepository.save(currentUser);
        }
        return currentUser;
    }

    // --- SCORING ALGORITHM ---

    public List<MatchProfileDto> getUserProfiles(Long currentUserId) {
        User currentUser = userRepository.findById(currentUserId).orElseThrow();

        // 1. Build Exclusion List (Me + Likes + Dislikes + Matches)
        List<Long> excludeIds = new ArrayList<>();
        excludeIds.add(currentUserId);
        excludeIds.addAll(currentUser.getLikes().stream().map(User::getId).toList());
        excludeIds.addAll(currentUser.getDislikes().stream().map(User::getId).toList());
        excludeIds.addAll(currentUser.getMatches().stream().map(User::getId).toList());

        // 2. Fetch Candidates
        List<User> candidates = userRepository.findByIdNotIn(excludeIds);

        // 3. Score & Transform
        return candidates.stream()
                .map(candidate -> calculateScore(currentUser, candidate))
                .sorted((a, b) -> b.getScore() - a.getScore()) // Sort Descending
                .collect(Collectors.toList());
    }

    public List<MatchProfileDto> getMatches(Long currentUserId) {
        User currentUser = userRepository.findById(currentUserId).orElseThrow();

        // Just score the people I already matched with
        return currentUser.getMatches().stream()
                .map(match -> calculateScore(currentUser, match))
                .sorted((a, b) -> b.getScore() - a.getScore())
                .collect(Collectors.toList());
    }

    // --- HELPER: The Weighted Algorithm ---
    private MatchProfileDto calculateScore(User me, User other) {
        // Find Intersections (Common Items)

        // 1. Common Artists
        Set<Artist> commonArtists = new HashSet<>(me.getTopArtists());
        commonArtists.retainAll(other.getTopArtists()); // Keep only shared items

        // 2. Common Tracks
        Set<Track> commonTracks = new HashSet<>(me.getTopTracks());
        commonTracks.retainAll(other.getTopTracks());

        // Calculate Score
        int score = (commonArtists.size() * 3) + (commonTracks.size() * 2);

        // Build DTO
        MatchProfileDto dto = new MatchProfileDto();
        dto.setId(other.getId());
        dto.setName(other.getName());
        dto.setImage(other.getImage());
        dto.setAge(other.getAge());
        dto.setBio(other.getBio());
        dto.setScore(score);

        // Map Entities to simple ItemDto
        dto.setCommonArtists(commonArtists.stream()
                .map(a -> new MatchProfileDto.ItemDto(a.getSpotifyId(), a.getName(), a.getImageUrl()))
                .collect(Collectors.toList()));

        dto.setCommonTracks(commonTracks.stream()
                .map(t -> new MatchProfileDto.ItemDto(t.getSpotifyId(), t.getName(), t.getImageUrl()))
                .collect(Collectors.toList()));

        return dto;
    }
}