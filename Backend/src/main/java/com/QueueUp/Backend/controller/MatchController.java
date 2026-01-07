package com.QueueUp.Backend.controller;

import com.QueueUp.Backend.dto.MatchProfileDto;
import com.QueueUp.Backend.service.MatchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PostMapping("/swipe-right/{likedUserId}")
    public ResponseEntity<?> swipeRight(@PathVariable Long likedUserId, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        matchService.swipeRight(currentUserId, likedUserId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/swipe-left/{dislikedUserId}")
    public ResponseEntity<?> swipeLeft(@PathVariable Long dislikedUserId, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        matchService.swipeLeft(currentUserId, dislikedUserId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/user-profiles")
    public ResponseEntity<?> getUserProfiles(HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        List<MatchProfileDto> profiles = matchService.getUserProfiles(currentUserId);
        return ResponseEntity.ok(Map.of("success", true, "users", profiles));
    }

    @GetMapping
    public ResponseEntity<?> getMatches(HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        List<MatchProfileDto> matches = matchService.getMatches(currentUserId);
        return ResponseEntity.ok(Map.of("success", true, "matches", matches));
    }
}