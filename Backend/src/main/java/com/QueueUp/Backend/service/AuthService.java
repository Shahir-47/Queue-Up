package com.QueueUp.Backend.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.QueueUp.Backend.model.Artist;
import com.QueueUp.Backend.model.Track;
import com.QueueUp.Backend.model.User;
import com.QueueUp.Backend.repository.ArtistRepository;
import com.QueueUp.Backend.repository.TrackRepository;
import com.QueueUp.Backend.repository.UserRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.enums.ModelObjectType;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;
    private final TrackRepository trackRepository;
    private final Cloudinary cloudinary;
    private final SpotifyClientFactory spotifyClientFactory;
    private final OpenAIService openAiService;

    public AuthService(UserRepository userRepository,
                       ArtistRepository artistRepository,
                       TrackRepository trackRepository,
                       Cloudinary cloudinary,
                       SpotifyClientFactory spotifyClientFactory,
                       OpenAIService openAiService) {
        this.userRepository = userRepository;
        this.artistRepository = artistRepository;
        this.trackRepository = trackRepository;
        this.cloudinary = cloudinary;
        this.spotifyClientFactory = spotifyClientFactory;
        this.openAiService = openAiService;
    }

    @Transactional
    public User signup(Map<String, Object> data) {
        String name = (String) data.get("name");
        String email = (String) data.get("email");
        String password = (String) data.get("password");

        Integer age = parseIntSafely(data.get("age"));

        String image = (String) data.get("image");

        @SuppressWarnings("unchecked")
        Map<String, Object> spotifyTokens = (Map<String, Object>) data.get("spotify");

        // 1. Validation
        if (name == null || email == null || password == null || age == null) {
            throw new RuntimeException("All fields are required");
        }
        if (age < 18) throw new RuntimeException("You must be at least 18 years old");
        if (password.length() < 6) throw new RuntimeException("Password must be at least 6 characters");
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        // 2. Create User
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(BCrypt.withDefaults().hashToString(12, password.toCharArray()));
        user.setAge(age);

        // 3. Cloudinary Upload
        if (image != null && image.startsWith("data:image")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> uploadResult = cloudinary.uploader().upload(image, ObjectUtils.emptyMap());
                user.setImage((String) uploadResult.get("secure_url"));
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload profile picture");
            }
        }

        // 4. Save Spotify Tokens
        if (spotifyTokens != null && spotifyTokens.containsKey("access_token")) {
            user.setSpotifyAccessToken((String) spotifyTokens.get("access_token"));
            user.setSpotifyRefreshToken((String) spotifyTokens.get("refresh_token"));

            Integer expiresIn = parseIntSafely(spotifyTokens.get("expires_in"));
            if (expiresIn != null) {
                user.setSpotifyTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            }
        }

        // 5. Save User
        User savedUser = userRepository.save(user);

        // 6. Fetch Spotify Data
        if (savedUser.getSpotifyAccessToken() != null) {
            try {
                fetchAndSaveSpotifyData(savedUser);
            } catch (Exception e) {
                logger.error("Failed to fetch Spotify data for user {}", savedUser.getId(), e);
            }
        }

        // 7. Create Demo Users
        try {
            createDemoUsers(savedUser);
        } catch (Exception e) {
            logger.warn("Failed to create demo users", e);
        }

        // 8. Return USER
        return savedUser;
    }

    public User login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPassword());
        if (!result.verified) throw new RuntimeException("Invalid email or password");

        return user;
    }

    // HELPER METHODS
    private void createDemoUsers(User sourceUser) {
        // Basic check if user has music
        boolean hasMusic = !sourceUser.getTopArtists().isEmpty() || !sourceUser.getTopTracks().isEmpty();
        if (!hasMusic) return;

        // Get a list of user's music for the AI prompt
        List<String> musicSample = sourceUser.getTopArtists().stream()
                .map(Artist::getName).limit(5).toList();

        // Create 2 Bots
        for (int i = 0; i < 2; i++) {
            User bot = new User();

            // 1. Generate Identity via AI
            int ageVariance = (int) (Math.random() * 5) - 2; // -2 to +2 years
            int botAge = Math.max(18, sourceUser.getAge() + ageVariance);

            Map<String, String> profile = openAiService.generateBotProfile(botAge, musicSample);

            bot.setName(profile.getOrDefault("name", "Music Fan"));
            bot.setBio(profile.getOrDefault("bio", "Here for the vibes."));
            bot.setAge(botAge);
            bot.setEmail("bot_" + System.currentTimeMillis() + "_" + i + "@queueup.ai");
            bot.setPassword(BCrypt.withDefaults().hashToString(12, "bot_pass".toCharArray()));

            // 2. Persistent Unique Picture (Download -> Upload to Cloudinary)
            try {
                // Using a timestamp (+i) to ensure we get a fresh face for each bot
                String faceUrl = "https://thispersondoesnotexist.com/?t=" + (System.currentTimeMillis() + i);
                byte[] imageBytes = new RestTemplate().getForObject(faceUrl, byte[].class);

                @SuppressWarnings("unchecked")
                Map<String, Object> uploadResult = cloudinary.uploader().upload(imageBytes,
                        ObjectUtils.asMap("folder", "bot_profiles"));

                bot.setImage((String) uploadResult.get("secure_url"));
            } catch (Exception e) {
                bot.setImage("https://api.dicebear.com/7.x/avataaars/svg?seed=" + i); // Fallback
            }

            // 3. Mark as Bot
            bot.setIsBot(true);

            // 4. Share Music Subsets (Variance: 10 to Max items)
            // We now populate ALL categories, not just Top Artists
            bot.getTopArtists().addAll(getRandomSubset(sourceUser.getTopArtists()));
            bot.getTopTracks().addAll(getRandomSubset(sourceUser.getTopTracks()));
            bot.getSavedTracks().addAll(getRandomSubset(sourceUser.getSavedTracks()));
            bot.getFollowedArtists().addAll(getRandomSubset(sourceUser.getFollowedArtists()));

            // 5. Bot Likes User (So it's a match when User likes them)
            // We need to save the bot first to get an ID
            User savedBot = userRepository.save(bot);

            savedBot.getLikes().add(sourceUser); // Bot likes User
            userRepository.save(savedBot);
        }
    }

    /**
     * Helper to get a random subset of music items.
     * Logic: If user has <= 10 items, take all.
     * Else, take a random number between 10 and Total.
     */
    private <T> List<T> getRandomSubset(Set<T> sourceSet) {
        List<T> list = new ArrayList<>(sourceSet);
        if (list.isEmpty()) return Collections.emptyList();

        Collections.shuffle(list);

        int max = list.size();
        int minLimit = 10;
        int targetCount;

        if (max <= minLimit) {
            targetCount = max;
        } else {
            // Random integer between 10 and max (inclusive)
            targetCount = minLimit + (int)(Math.random() * (max - minLimit + 1));
        }

        return list.subList(0, targetCount);
    }

    private Integer parseIntSafely(Object obj) {
        switch (obj) {
            case null -> {
                return null;
            }
            case Integer i -> {
                return i;
            }
            case String s -> {
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case Number number -> {
                return number.intValue();
            }
            default -> {
            }
        }
        return null;
    }

    private void fetchAndSaveSpotifyData(User user) {
        SpotifyApi client = spotifyClientFactory.getClientForUser(user.getId());

        try {
            // 1. Top Artists (Score: 3)
            var artists = client.getUsersTopArtists().limit(50).time_range("long_term").build().execute();
            for (var item : artists.getItems()) {
                user.getTopArtists().add(saveOrGetArtist(item));
            }

            // 2. Top Tracks (Score: 2)
            var tracks = client.getUsersTopTracks().limit(50).build().execute();
            for (var item : tracks.getItems()) {
                user.getTopTracks().add(saveOrGetTrack(item));
            }

            // 3. Saved Tracks (Score: 1)
            var saved = client.getUsersSavedTracks().limit(50).build().execute();
            for (var item : saved.getItems()) {
                user.getSavedTracks().add(saveOrGetTrack(item.getTrack()));
            }

            // 4. Followed Artists (Score: 1)
            var followed = client.getUsersFollowedArtists(ModelObjectType.ARTIST).limit(50).build().execute();
            for (var item : followed.getItems()) {
                user.getFollowedArtists().add(saveOrGetArtist(item));
            }

            userRepository.save(user);

        } catch (Exception e) {
            throw new RuntimeException("Error communicating with Spotify", e);
        }
    }

    private Artist saveOrGetArtist(se.michaelthelin.spotify.model_objects.specification.Artist spotifyArtist) {
        return artistRepository.findById(spotifyArtist.getId())
                .orElseGet(() -> {
                    Artist newA = new Artist();
                    newA.setSpotifyId(spotifyArtist.getId());
                    newA.setName(spotifyArtist.getName());
                    if (spotifyArtist.getImages() != null && spotifyArtist.getImages().length > 0) {
                        newA.setImageUrl(spotifyArtist.getImages()[0].getUrl());
                    }
                    return artistRepository.save(newA);
                });
    }

    private Track saveOrGetTrack(se.michaelthelin.spotify.model_objects.specification.Track spotifyTrack) {
        return trackRepository.findById(spotifyTrack.getId())
                .orElseGet(() -> {
                    Track newT = new Track();
                    newT.setSpotifyId(spotifyTrack.getId());
                    newT.setName(spotifyTrack.getName());
                    if (spotifyTrack.getAlbum().getImages() != null && spotifyTrack.getAlbum().getImages().length > 0) {
                        newT.setImageUrl(spotifyTrack.getAlbum().getImages()[0].getUrl());
                    }
                    String artistNames = java.util.Arrays.stream(spotifyTrack.getArtists())
                            .map(se.michaelthelin.spotify.model_objects.specification.ArtistSimplified::getName)
                            .reduce((a, b) -> a + ", " + b).orElse("Unknown");
                    newT.setArtistString(artistNames);
                    return trackRepository.save(newT);
                });
    }
}