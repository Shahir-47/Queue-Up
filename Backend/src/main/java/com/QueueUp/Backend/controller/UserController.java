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
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> updateData,
                                           jakarta.servlet.http.HttpServletRequest request) { // <--- Add request param
        try {
            // RETRIEVE REAL ID FROM MIDDLEWARE
            Long currentUserId = (Long) request.getAttribute("userId");

            // Safety check
            if (currentUserId == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "User ID missing"));
            }

            User updatedUser = userService.updateProfile(currentUserId, updateData);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "user", updatedUser
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}