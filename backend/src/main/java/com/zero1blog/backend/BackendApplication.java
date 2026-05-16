package com.zero1blog.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.UserRepository;

@SpringBootApplication
public class BackendApplication {

	private static final Logger logger = LoggerFactory.getLogger(BackendApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	public CommandLineRunner setupAdmin(UserRepository userRepository) {
		return args -> {
			long userCount = userRepository.count();
			logger.info("Startup: Total users in database: {}", userCount);

			if (userCount > 0) {
				var users = userRepository.findAll();
				users.forEach(u -> logger.info("User: {}, Role: {}, PublicId: {}", u.getUsername(), u.getRole(), u.getPublicId()));

				boolean hasAdmin = users.stream()
						.anyMatch(u -> u.getRole() == User.Role.ADMIN);
				
				if (!hasAdmin) {
					User user = users.get(0);
					user.setRole(User.Role.ADMIN);
					userRepository.save(user);
					logger.info("Admin Safeguard: No ADMIN found. User '{}' has been promoted to ADMIN.", user.getUsername());
				}
			}
		};
	}

}

