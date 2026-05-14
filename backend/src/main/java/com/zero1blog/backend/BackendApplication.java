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
			// TEMPORARY: Promotes the first user to ADMIN if they exist and are currently a USER.
			userRepository.findAll().stream().findFirst().ifPresent(user -> {
				if (user.getRole() == User.Role.USER) {
					user.setRole(User.Role.ADMIN);
					userRepository.save(user);
					logger.info("Admin Setup: User '{}' has been promoted to ADMIN.", user.getUsername());
				}
			});
		};
	}

}

