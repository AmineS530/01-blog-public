package com.zero1blog.backend.dto;

import lombok.Data;

@Data
public class UploadMediaRequest {
    private String data; // Base64
    private String fileName;
    private String mediaType;
}
