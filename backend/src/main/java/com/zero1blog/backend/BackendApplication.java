package com.zero1blog.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.zero1blog.backend.repository.UserCredentialsRepository;
import com.zero1blog.backend.repository.UserProfileRepository;
import com.zero1blog.backend.repository.UserRepository;

@SpringBootApplication
public class BackendApplication {

	private static final Logger logger = LoggerFactory.getLogger(BackendApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	public CommandLineRunner setupAdmin(
			UserRepository userRepository,
			UserCredentialsRepository userCredentialsRepository,
			UserProfileRepository userProfileRepository,
			PasswordEncoder passwordEncoder) {
		return args -> {
			long userCount = userRepository.count();
			logger.info("Startup: Total users in database: {}", userCount);

			if (userCount > 0) {
				var users = userRepository.findAll();
				// I already have a superadmin so commenting this part for extra security
				// User firstUser = users.get(0);
				// if (firstUser.getRole() != User.Role.SUPER_ADMIN) {
				// 	firstUser.setRole(User.Role.SUPER_ADMIN);
				// 	userRepository.save(firstUser);
				// 	logger.info("Startup: Promoted first user '{}' (ID: {}) to SUPER_ADMIN", firstUser.getUsername(), firstUser.getId());
				// }
				users.forEach(u -> logger.info("User: {}, Role: {}, PublicId: {}", u.getUsername(), u.getRole(), u.getPublicId()));
			}
		};
	}

}

