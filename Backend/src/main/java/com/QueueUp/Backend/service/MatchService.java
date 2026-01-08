package com.QueueUp.Backend.service;

import com.QueueUp.Backend.dto.MatchProfileDto;
import com.QueueUp.Backend.model.Artist;
import com.QueueUp.Backend.model.Track;
import com.QueueUp.Backend.model.User;
import com.QueueUp.Backend.repository.UserRepository;
import com.QueueUp.Backend.socket.SocketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MatchService {

    private static final Logger logger = LoggerFactory.getLogger(MatchService.class);

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
    public void swipeRight(Long currentUserId, Long likedUserId) {
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
    }

    private void notifyMatch(User user1, User user2) {
        try {
            sendMatchNotification(user1, user2);
            sendMatchNotification(user2, user1);
        } catch (Exception e) {
            logger.error("Failed to send match notification via socket", e);
        }
    }

    private void sendMatchNotification(User recipient, User matchData) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("_id", matchData.getId());
        payload.put("name", matchData.getName());
        payload.put("image", matchData.getImage());

        socketService.sendMessageToUser(
                recipient.getId(),
                "newMatch",
                objectMapper.writeValueAsString(payload)
        );
    }

    @Transactional
    public void swipeLeft(Long currentUserId, Long dislikedUserId) {
        User currentUser = userRepository.findById(currentUserId).orElseThrow();
        User dislikedUser = userRepository.findById(dislikedUserId).orElseThrow();
        if (!currentUser.getDislikes().contains(dislikedUser)) {
            currentUser.getDislikes().add(dislikedUser);
            userRepository.save(currentUser);
        }
    }

    // SCORING AND FETCHING
    public List<MatchProfileDto> getUserProfiles(Long currentUserId) {
        User currentUser = userRepository.findById(currentUserId).orElseThrow();

        List<Long> excludeIds = new ArrayList<>();
        excludeIds.add(currentUserId);
        excludeIds.addAll(currentUser.getLikes().stream().map(User::getId).toList());
        excludeIds.addAll(currentUser.getDislikes().stream().map(User::getId).toList());
        excludeIds.addAll(currentUser.getMatches().stream().map(User::getId).toList());

        List<User> candidates = userRepository.findByIdNotIn(excludeIds);

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
        List<Artist> commonArtists = findCommonItems(me.getTopArtists(), other.getTopArtists(), Artist::getSpotifyId);
        List<Track> commonTracks = findCommonItems(me.getTopTracks(), other.getTopTracks(), Track::getSpotifyId);
        List<Track> commonSaved = findCommonItems(me.getSavedTracks(), other.getSavedTracks(), Track::getSpotifyId);
        List<Artist> commonFollowed = findCommonItems(me.getFollowedArtists(), other.getFollowedArtists(), Artist::getSpotifyId);

        int score = (commonArtists.size() * 3) +
                (commonTracks.size() * 2) +
                (commonSaved.size()) +
                (commonFollowed.size());

        MatchProfileDto dto = new MatchProfileDto();
        dto.setId(other.getId());
        dto.setName(other.getName());
        dto.setImage(other.getImage());
        dto.setAge(other.getAge());
        dto.setBio(other.getBio());
        dto.setScore(score);

        dto.setCommonArtists(toDtoList(commonArtists, Artist::getSpotifyId, Artist::getName, Artist::getImageUrl));
        dto.setCommonTracks(toDtoList(commonTracks, Track::getSpotifyId, Track::getName, Track::getImageUrl));
        dto.setCommonSaved(toDtoList(commonSaved, Track::getSpotifyId, Track::getName, Track::getImageUrl));
        dto.setCommonFollowed(toDtoList(commonFollowed, Artist::getSpotifyId, Artist::getName, Artist::getImageUrl));

        return dto;
    }

    // HELPERS
    // Generic method to find common items between two sets based on a unique ID.
    private <T> List<T> findCommonItems(Set<T> set1, Set<T> set2, Function<T, String> idExtractor) {
        if (set1 == null || set2 == null) return new ArrayList<>();
        Set<String> ids1 = set1.stream().map(idExtractor).collect(Collectors.toSet());
        return set2.stream()
                .filter(item -> ids1.contains(idExtractor.apply(item)))
                .collect(Collectors.toList());
    }

     // Generic method to convert any list of music items into frontend DTOs.
    private <T> List<MatchProfileDto.ItemDto> toDtoList(List<T> items,
                                                        Function<T, String> idMapper,
                                                        Function<T, String> nameMapper,
                                                        Function<T, String> imageMapper) {
        return items.stream()
                .map(item -> new MatchProfileDto.ItemDto(
                        idMapper.apply(item),
                        nameMapper.apply(item),
                        imageMapper.apply(item)))
                .collect(Collectors.toList());
    }
}