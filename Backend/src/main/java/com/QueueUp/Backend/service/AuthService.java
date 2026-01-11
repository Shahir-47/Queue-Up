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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.enums.ModelObjectType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;
    private final TrackRepository trackRepository;
    private final Cloudinary cloudinary;
    private final SpotifyClientFactory spotifyClientFactory;
    private final SocketService socketService;
    private final TransactionTemplate transactionTemplate;
    private final Random random = new Random();

    // LOCAL DATA FOR BOTS
    private static final List<String> MALE_NAMES = List.of(
            "James", "John", "Robert", "Michael", "William", "David", "Richard", "Joseph", "Thomas", "Charles",
            "Daniel", "Matthew", "Anthony", "Donald", "Mark", "Paul", "Steven", "Andrew", "Kenneth", "Joshua"
    );

    private static final List<String> FEMALE_NAMES = List.of(
            "Mary", "Patricia", "Jennifer", "Linda", "Elizabeth", "Barbara", "Susan", "Jessica", "Sarah", "Karen",
            "Lisa", "Nancy", "Betty", "Margaret", "Sandra", "Ashley", "Kimberly", "Emily", "Donna", "Michelle"
    );

    private static final List<String> LAST_NAMES = List.of(
            "Smith", "Johnson", "Williams", "Jones", "Brown", "Davis", "Miller", "Wilson", "Moore", "Taylor",
            "Anderson", "Thomas", "Jackson", "White", "Harris", "Martin", "Thompson", "Garcia", "Martinez", "Robinson"
    );

    private static final List<String> GENERIC_BIOS = List.of(
            "Music is my escape",
            "Always looking for new vibes",
            "Concert addict",
            "Bass head",
            "Here for the music",
            "Spotify wrapped was embarrassing",
            "Musician / Dreamer",
            "Vinyl collector",
            "Just listen.",
            "In search of the perfect playlist"
    );

    public AuthService(UserRepository userRepository,
                       ArtistRepository artistRepository,
                       TrackRepository trackRepository,
                       Cloudinary cloudinary,
                       SpotifyClientFactory spotifyClientFactory,
                       SocketService socketService,
                       PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.artistRepository = artistRepository;
        this.trackRepository = trackRepository;
        this.cloudinary = cloudinary;
        this.spotifyClientFactory = spotifyClientFactory;
        this.socketService = socketService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
        // We register a hook to run this ONLY after the database transaction successfully commits.
        Long userId = savedUser.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        createDemoUsers(userId);
                    } catch (Exception e) {
                        logger.warn("Failed to create demo users", e);
                    }
                });
            }
        });

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
    private void createDemoUsers(Long sourceUserId) {
        // 1. Check eligibility
        Boolean hasMusic = transactionTemplate.execute(status -> {
            User u = userRepository.findById(sourceUserId).orElse(null);
            if (u == null) return false;
            return !u.getTopArtists().isEmpty() || !u.getTopTracks().isEmpty();
        });

        if (Boolean.FALSE.equals(hasMusic)) return;

        // 2. Create exactly 1 Male and 1 Female
        List<Boolean> genders = List.of(true, false);

        for (boolean isMale : genders) {
            // Execute creation in its own transaction
            Long newBotId = transactionTemplate.execute(status -> createSingleBot(sourceUserId, isMale));

            // 3. Broadcast
            if (newBotId != null) {
                try {
                    Thread.sleep(300);
                    socketService.broadcast("newUserProfile", Map.of("newUserId", newBotId));
                } catch (Exception e) {
                    logger.warn("Error broadcasting new user", e);
                }
            }
        }
    }

    /**
     * Creates a single bot using local data generation and external image API.
     */
    private Long createSingleBot(Long sourceUserId, boolean isMale) {
        User sourceUser = userRepository.findById(sourceUserId).orElse(null);
        if (sourceUser == null) return null;

        User bot = new User();

        // 1. Generate Local Identity based on passed gender
        String firstName = isMale ?
                MALE_NAMES.get(random.nextInt(MALE_NAMES.size())) :
                FEMALE_NAMES.get(random.nextInt(FEMALE_NAMES.size()));

        String lastName = LAST_NAMES.get(random.nextInt(LAST_NAMES.size()));
        String fullName = firstName + " " + lastName;

        // Generate Age
        int age = 19 + random.nextInt(32); // 19 to 50

        bot.setName(fullName);
        bot.setAge(age);

        // Construct unique email
        String sanitizedName = firstName.toLowerCase() + "." + lastName.toLowerCase();
        bot.setEmail(System.currentTimeMillis() + "_" + sanitizedName + "@example.com");

        // Bio
        String randomBio = GENERIC_BIOS.get(random.nextInt(GENERIC_BIOS.size()));
        bot.setBio(randomBio);

        bot.setPassword(BCrypt.withDefaults().hashToString(12, "bot_pass".toCharArray()));
        bot.setIsBot(true);

        // 2. Image Handling
        try {
            String genderParam = isMale ? "male" : "female";
            String ageParam = getAgeRangeParam(age);

            byte[] croppedImageBytes = fetchAndCropBotImage(genderParam, ageParam, "white");

            if (croppedImageBytes != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> uploadResult = cloudinary.uploader().upload(croppedImageBytes,
                        ObjectUtils.asMap("folder", "bot_profiles"));
                bot.setImage((String) uploadResult.get("secure_url"));
            } else {
                // Set default if fetching fails
                bot.setImage("https://via.placeholder.com/300?text=" + firstName.charAt(0));
            }
        } catch (Exception e) {
            logger.error("Failed to generate bot image", e);
            bot.setImage("https://via.placeholder.com/300?text=" + firstName.charAt(0));
        }

        // 3. Share Music Subsets
        bot.getTopArtists().addAll(getRandomSubset(sourceUser.getTopArtists()));
        bot.getTopTracks().addAll(getRandomSubset(sourceUser.getTopTracks()));
        bot.getSavedTracks().addAll(getRandomSubset(sourceUser.getSavedTracks()));
        bot.getFollowedArtists().addAll(getRandomSubset(sourceUser.getFollowedArtists()));

        // 4. Save Bot and Link
        User savedBot = userRepository.save(bot);
        savedBot.getLikes().add(sourceUser);
        userRepository.save(savedBot);

        return savedBot.getId();
    }

    /**
     * Fetches image from this-person-does-not-exist.com, downloads it,
     * and crops 11% from top and bottom to remove watermarks.
     */
    private byte[] fetchAndCropBotImage(String gender, String ageRange, String ethnicity) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            long time = System.currentTimeMillis();

            // 1. Get the source URL
            String metaUrl = String.format(
                    "https://this-person-does-not-exist.com/new?time=%d&gender=%s&age=%s&etnic=%s",
                    time, gender, ageRange, ethnicity
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    metaUrl, HttpMethod.GET, entity, Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body == null || body.get("src") == null) return null;

            String relativeSrc = (String) body.get("src");
            String fullImageUrl = "https://this-person-does-not-exist.com" + relativeSrc;

            // 2. Download Image for Processing
            URL url = new URL(fullImageUrl);
            BufferedImage originalImage = ImageIO.read(url);

            if (originalImage == null) return null;

            // 3. Crop 13% from Top and Bottom
            int height = originalImage.getHeight();
            int width = originalImage.getWidth();

            int cropAmount = (int) (height * 0.11); // 11%
            int newHeight = height - (2 * cropAmount); // Remove top and bottom

            // Ensure we have valid dimensions
            if (newHeight <= 0) return null;

            BufferedImage croppedImage = originalImage.getSubimage(0, cropAmount, width, newHeight);

            // 4. Convert back to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(croppedImage, "jpg", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            logger.warn("Error fetching/cropping bot image", e);
            return null;
        }
    }

    private String getAgeRangeParam(int age) {
        if (age <= 18) return "12-18";
        if (age <= 25) return "19-25";
        if (age <= 35) return "26-35";
        if (age <= 50) return "35-50";
        return "50";
    }

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
            // 1. Top Artists (score: 3)
            var artists = client.getUsersTopArtists().limit(50).time_range("long_term").build().execute();
            for (var item : artists.getItems()) {
                user.getTopArtists().add(saveOrGetArtist(item));
            }

            // 2. Top Tracks (score: 2)
            var tracks = client.getUsersTopTracks().limit(50).build().execute();
            for (var item : tracks.getItems()) {
                user.getTopTracks().add(saveOrGetTrack(item));
            }

            // 3. Saved Tracks (score: 1)
            var saved = client.getUsersSavedTracks().limit(50).build().execute();
            for (var item : saved.getItems()) {
                user.getSavedTracks().add(saveOrGetTrack(item.getTrack()));
            }

            // 4. Followed Artists (score: 1)
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