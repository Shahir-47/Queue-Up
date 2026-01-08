package com.QueueUp.Backend.controller;

import com.QueueUp.Backend.model.User;
import com.QueueUp.Backend.repository.UserRepository;
import com.QueueUp.Backend.service.AuthService;
import com.QueueUp.Backend.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    public AuthController(AuthService authService, UserRepository userRepository, JwtUtils jwtUtils) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        try {
            User user = authService.signup(body);

            String token = jwtUtils.generateToken(user.getId());
            setJwtCookie(response, token);

            return ResponseEntity.status(201).body(Map.of(
                    "success", true,
                    "user", user
            ));
        } catch (Exception e) {
            logger.error("Signup failed for email: {}", body.get("email"), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletResponse response) {
        try {
            User user = authService.login(body.get("email"), body.get("password"));

            String token = jwtUtils.generateToken(user.getId());
            setJwtCookie(response, token);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "user", user
            ));
        } catch (Exception e) {
            logger.warn("Login failed for email: {}", body.get("email"));
            return ResponseEntity.status(401).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(jakarta.servlet.http.HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Not authenticated"));
        }

        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(Map.of("success", true, "user", user)))
                .orElse(ResponseEntity.status(401).build());
    }

    private void setJwtCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days

        if ("prod".equals(activeProfile)) {
            cookie.setSecure(true);
            cookie.setAttribute("SameSite", "None");
        } else {
            cookie.setSecure(false);
            cookie.setAttribute("SameSite", "Lax");
        }

        response.addCookie(cookie);
    }
}