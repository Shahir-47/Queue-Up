package com.QueueUp.Backend.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.QueueUp.Backend.model.User;
import com.QueueUp.Backend.repository.UserRepository;
import com.QueueUp.Backend.utils.JwtUtils;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final Cloudinary cloudinary;

    public AuthService(UserRepository userRepository, JwtUtils jwtUtils, Cloudinary cloudinary) {
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
        this.cloudinary = cloudinary;
    }

    public String signup(Map<String, Object> data) {
        String name = (String) data.get("name");
        String email = (String) data.get("email");
        String password = (String) data.get("password");
        Integer age = (Integer) data.get("age");
        String image = (String) data.get("image");

        // 1. Validation
        if (name == null || email == null || password == null || age == null) {
            throw new RuntimeException("All fields are required");
        }
        if (age < 18) throw new RuntimeException("You must be at least 18 years old");
        if (password.length() < 6) throw new RuntimeException("Password must be at least 6 characters");
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already exists");
        }

        // 2. Hash Password
        String hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray());

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(hashedPassword);
        user.setAge(age);

        // 3. Handle Cloudinary Image
        if (image != null && image.startsWith("data:image")) {
            try {
                Map uploadResult = cloudinary.uploader().upload(image, ObjectUtils.emptyMap());
                user.setImage((String) uploadResult.get("secure_url"));
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload profile picture");
            }
        }

        // 4. Save User
        User savedUser = userRepository.save(user);

        // 5. Generate Token
        return jwtUtils.generateToken(savedUser.getId());
    }

    public String login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        // Verify Password
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), user.getPassword());
        if (!result.verified) {
            throw new RuntimeException("Invalid email or password");
        }

        return jwtUtils.generateToken(user.getId());
    }
}