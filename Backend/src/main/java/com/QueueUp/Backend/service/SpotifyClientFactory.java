package com.QueueUp.Backend.service;

import com.QueueUp.Backend.model.User;
import com.QueueUp.Backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;

import java.net.URI;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SpotifyClientFactory {

    @Value("${spotify.client-id}")
    private String clientId;

    @Value("${spotify.client-secret}")
    private String clientSecret;

    @Value("${spotify.redirect-uri}")
    private String redirectUri;

    private final UserRepository userRepository;

    private static final Logger logger = LoggerFactory.getLogger(SpotifyClientFactory.class);

    public SpotifyClientFactory(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public SpotifyApi getClientForUser(Long userId) {
        // Fetch User
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Build Client
        SpotifyApi client = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRedirectUri(URI.create(redirectUri))
                .setAccessToken(user.getSpotifyAccessToken())
                .setRefreshToken(user.getSpotifyRefreshToken())
                .build();

        // Check Expiration & Refresh
        if (isTokenExpired(user.getSpotifyTokenExpiresAt())) {
            try {
                logger.info("Token expired for user {}. Refreshing...", userId);

                AuthorizationCodeCredentials newTokens = client.authorizationCodeRefresh().build().execute();

                client.setAccessToken(newTokens.getAccessToken());
                if (newTokens.getRefreshToken() != null) {
                    client.setRefreshToken(newTokens.getRefreshToken());
                }

                // Update DB
                user.setSpotifyAccessToken(newTokens.getAccessToken());

                // Convert Seconds to LocalDateTime
                LocalDateTime newExpiry = LocalDateTime.now()
                        .plusSeconds(newTokens.getExpiresIn());
                user.setSpotifyTokenExpiresAt(newExpiry);

                if (newTokens.getRefreshToken() != null) {
                    user.setSpotifyRefreshToken(newTokens.getRefreshToken());
                }

                userRepository.save(user);
                System.out.println("Tokens refreshed.");

            } catch (Exception e) {
                logger.error("Failed to refresh Spotify token: {}", e.getMessage());
            }
        }

        return client;
    }

    private boolean isTokenExpired(LocalDateTime expiresAt) {
        if (expiresAt == null) return true;
        return LocalDateTime.now().isAfter(expiresAt);
    }
}