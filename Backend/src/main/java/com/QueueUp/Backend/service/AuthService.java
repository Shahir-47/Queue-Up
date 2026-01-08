package com.QueueUp.Backend.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.QueueUp.Backend.model.Artist;
import com.QueueUp.Backend.model.Track;
import com.QueueUp.Backend.model.User;
import com.QueueUp.Backend.repository.ArtistRepository;
import com.QueueUp.Backend.repository.TrackRepository;
import com.QueueUp.Backend.repository.UserRepository;
import com.QueueUp.Backend.socket.SocketService;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.enums.ModelObjectType;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;
    private final TrackRepository trackRepository;
    private final Cloudinary cloudinary;
    private final SpotifyClientFactory spotifyClientFactory;
    private final SocketService socketService;

    public AuthService(UserRepository userRepository,
                       ArtistRepository artistRepository,
                       TrackRepository trackRepository,
                       Cloudinary cloudinary,
                       SpotifyClientFactory spotifyClientFactory,
                       SocketService socketService) {
        this.userRepository = userRepository;
        this.artistRepository = artistRepository;
        this.trackRepository = trackRepository;
        this.cloudinary = cloudinary;
        this.spotifyClientFactory = spotifyClientFactory;
        this.socketService = socketService;
    }

    @Transactional
    public User signup(Map<String, Object> data) {
        String name = (String) data.get("name");
        String email = (String) data.get("email");
        String password = (String) data.get("password");

        Integer age = parseIntSafely(data.get("age"));

        String image = (String) data.get("image");
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
                Map uploadResult = cloudinary.uploader().upload(image, ObjectUtils.emptyMap());
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

        // 6. Fetch Spotify Data (Top, Saved, Followed)
        if (savedUser.getSpotifyAccessToken() != null) {
            try {
                fetchAndSaveSpotifyData(savedUser);
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Warning: Failed to fetch initial Spotify data");
            }
        }

        // 7. Create Demo Users (Populate Matches)
        try {
            createDemoUsers(savedUser);
        } catch (Exception e) {
            System.err.println("Warning: Failed to create demo users: " + e.getMessage());
        }

        // 8. Broadcast
        socketService.broadcast("newUserProfile", "{\"newUserId\": " + savedUser.getId() + "}");

        // 9. Return USER
        return savedUser;
    }

    public User login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPassword());
        if (!result.verified) throw new RuntimeException("Invalid email or password");

        return user;
    }

    // --- HELPER METHODS ---

    /**
     * Creates 5 fake users who share the same music taste as the signed-up user.
     * This ensures the "Potential Matches" list is populated and high-scoring.
     */
    private void createDemoUsers(User sourceUser) {
        // Only create demo users if the source user actually has music data.
        boolean hasMusicData = !sourceUser.getTopArtists().isEmpty() ||
                !sourceUser.getTopTracks().isEmpty() ||
                !sourceUser.getSavedTracks().isEmpty() ||
                !sourceUser.getFollowedArtists().isEmpty();

        if (!hasMusicData) {
            System.out.println("⚠️ Skipping demo user creation: Source user has no music data.");
            return;
        }

        String[] names = {"Riley", "Casey", "Jamie", "Morgan", "Quinn"};
        String[] bios = {
                "Music is my escape 🎧",
                "Always looking for new concert buddies 🎸",
                "Vibing to the same beat.",
                "Let's share playlists!",
                "Coffee and Vinyls ☕"
        };

        for (int i = 0; i < names.length; i++) {
            User demo = new User();

            // Basic Info
            demo.setName(names[i]);
            // Ensure email is unique by appending source ID and timestamp
            demo.setEmail(names[i].toLowerCase() + "_" + sourceUser.getId() + "_" + System.currentTimeMillis() + "@demo.com");
            demo.setPassword(BCrypt.withDefaults().hashToString(12, "password".toCharArray()));
            // Make age roughly close to the user's age
            demo.setAge(sourceUser.getAge() + (i % 5) - 2);
            demo.setBio(bios[i]);

            // Random Avatar
            demo.setImage("https://api.dicebear.com/7.x/miniavs/svg?seed=" + (sourceUser.getId() + i + 100));

            // Copy Music Data into separate collections
            demo.getTopArtists().addAll(sourceUser.getTopArtists());
            demo.getTopTracks().addAll(sourceUser.getTopTracks());
            demo.getSavedTracks().addAll(sourceUser.getSavedTracks());
            demo.getFollowedArtists().addAll(sourceUser.getFollowedArtists());

            // Save to DB
            userRepository.save(demo);
        }
        System.out.println("✅ Created 5 demo users matching " + sourceUser.getName());
    }

    private Integer parseIntSafely(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (obj instanceof Number) return ((Number) obj).intValue();
        return null;
    }

    private void fetchAndSaveSpotifyData(User user) {
        SpotifyApi client = spotifyClientFactory.getClientForUser(user.getId());

        try {
            // 1. Top Artists (Score: 3)
            var artists = client.getUsersTopArtists().limit(10).time_range("long_term").build().execute();
            for (var item : artists.getItems()) {
                user.getTopArtists().add(saveOrGetArtist(item));
            }

            // 2. Top Tracks (Score: 2)
            var tracks = client.getUsersTopTracks().limit(10).build().execute();
            for (var item : tracks.getItems()) {
                user.getTopTracks().add(saveOrGetTrack(item));
            }

            // 3. Saved Tracks (Score: 1)
            var saved = client.getUsersSavedTracks().limit(10).build().execute();
            for (var item : saved.getItems()) {
                user.getSavedTracks().add(saveOrGetTrack(item.getTrack()));
            }

            // 4. Followed Artists (Score: 1)
            var followed = client.getUsersFollowedArtists(ModelObjectType.ARTIST).limit(10).build().execute();
            for (var item : followed.getItems()) {
                user.getFollowedArtists().add(saveOrGetArtist(item));
            }

            System.out.println("--- SPRING BOOT SPOTIFY FETCH ---");

            System.out.print("Top Artists: ");
            user.getTopArtists().forEach(a -> System.out.print(a.getName() + " (" + a.getSpotifyId() + "), "));
            System.out.println();

            System.out.print("Top Tracks: ");
            user.getTopTracks().forEach(t -> System.out.print(t.getName() + " (" + t.getSpotifyId() + "), "));
            System.out.println();

            System.out.println("---------------------------------");

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