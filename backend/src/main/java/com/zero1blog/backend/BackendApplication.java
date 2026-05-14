package com.zero1blog.backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.UserRepository;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	public CommandLineRunner setupAdmin(UserRepository userRepository) {
		return args -> {
			// Promotes the first user to ADMIN if they exist and are currently a USER.
			userRepository.findAll().stream().findFirst().ifPresent(user -> {
				if (user.getRole() == User.Role.USER) {
					user.setRole(User.Role.ADMIN);
					userRepository.save(user);
					System.out.println("LOG: User '" + user.getUsername() + "' has been promoted to ADMIN.");
				}
			});
		};
	}

}
