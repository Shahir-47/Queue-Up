package com.QueueUp.Backend.controller;

import com.QueueUp.Backend.service.SpotifyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/auth/spotify")
public class SpotifyAuthController {

    private final SpotifyService spotifyService;
    private final ObjectMapper objectMapper;
    private final Set<String> pendingStates = ConcurrentHashMap.newKeySet();
    private static final Logger logger = LoggerFactory.getLogger(SpotifyAuthController.class);

    public SpotifyAuthController(SpotifyService spotifyService, ObjectMapper objectMapper) {
        this.spotifyService = spotifyService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/login")
    public ResponseEntity<?> spotifyLogin() {
        String state = UUID.randomUUID().toString();
        pendingStates.add(state);

        String authorizeURL = spotifyService.getAuthorizationUrl(state);

        return ResponseEntity.ok(Map.of("url", authorizeURL));
    }

    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String spotifyCallback(@RequestParam(required = false) String code,
                                  @RequestParam(required = false) String state) {

        if (state == null || !pendingStates.contains(state)) {
            return generateErrorHtml("Invalid or expired state");
        }
        pendingStates.remove(state);

        if (code == null) {
            return "<script>window.close()</script>";
        }

        try {
            Map<String, Object> tokenData = spotifyService.exchangeCodeForToken(code);

            // Convert the raw Map to JSON string
            String payloadJson = objectMapper.writeValueAsString(tokenData);

            return """
                <script>
                  window.opener.postMessage(
                    { type: "spotify", payload: %s },
                    "*"
                  );
                  window.close();
                </script>
            """.formatted(payloadJson);

        } catch (Exception e) {
            logger.error("Spotify callback failed", e);
            return generateErrorHtml("Authentication failed. Please try again.");
        }
    }

    private String generateErrorHtml(String errorMessage) {
        return """
            <script>
              window.opener.postMessage(
                { type: "spotify-error", error: "%s" },
                window.location.origin
              );
              window.close();
            </script>
        """.formatted(errorMessage);
    }
}