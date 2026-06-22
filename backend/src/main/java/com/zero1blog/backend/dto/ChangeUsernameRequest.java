package com.zero1blog.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangeUsernameRequest {
    @NotBlank(message = "New username is required")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    private String newUsername;
}
