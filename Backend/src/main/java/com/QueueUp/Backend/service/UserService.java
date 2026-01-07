package com.QueueUp.Backend.service;

import com.QueueUp.Backend.model.User;
import com.QueueUp.Backend.repository.UserRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final Cloudinary cloudinary;

    // Dependency Injection
    public UserService(UserRepository userRepository, Cloudinary cloudinary) {
        this.userRepository = userRepository;
        this.cloudinary = cloudinary;
    }

    public User updateProfile(Long userId, Map<String, Object> updateData) {
        // 1. Find the User
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Handle Image Upload
        if (updateData.containsKey("image")) {
            String imageBase64 = (String) updateData.get("image");

            // Check if it's a base64 string
            if (imageBase64 != null && imageBase64.startsWith("data:image")) {
                try {
                    Map uploadResult = cloudinary.uploader().upload(imageBase64, ObjectUtils.emptyMap());

                    String secureUrl = (String) uploadResult.get("secure_url");
                    user.setImage(secureUrl);
                } catch (IOException e) {
                    throw new RuntimeException("Error uploading image to Cloudinary", e);
                }
            }
        }

        // 3. Update other fields (Dynamic updates)
        // In Java, we usually update specific fields explicitly for type safety
        if (updateData.containsKey("name")) user.setName((String) updateData.get("name"));
        if (updateData.containsKey("bio")) user.setBio((String) updateData.get("bio"));
        if (updateData.containsKey("age")) user.setAge((Integer) updateData.get("age"));

        return userRepository.save(user);
    }
}