package com.QueueUp.Backend;

import com.QueueUp.Backend.model.User;
import com.QueueUp.Backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	// This runs every time server starts
	@Bean
	CommandLineRunner run(UserRepository userRepository) {
		return args -> {
			if (userRepository.count() == 0) {
				User dummyUser = new User();
				dummyUser.setName("Test User");
				dummyUser.setEmail("test@example.com");
				dummyUser.setPassword("hashedpassword");
				dummyUser.setAge(25);
				userRepository.save(dummyUser);
				System.out.println("✅ Created dummy user with ID: " + dummyUser.getId());
			}
		};
	}
}