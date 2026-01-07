package com.QueueUp.Backend.controller;

import com.QueueUp.Backend.model.User;
import com.QueueUp.Backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> updateData) {
        try {
            // TODO: Retrieve real User ID from SecurityContext (JWT) later
            // For now, we simulate a logged-in user with ID 1
            Long currentUserId = 1L;

            User updatedUser = userService.updateProfile(currentUserId, updateData);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "user", updatedUser
            ));

        } catch (Exception e) {
            e.printStackTrace();
            // FIX: Changed .json() to .body()
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}