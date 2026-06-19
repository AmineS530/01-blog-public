package com.zero1blog.backend.controller;

import java.util.Base64;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.zero1blog.backend.dto.UploadMediaRequest;
import com.zero1blog.backend.exception.ResourceNotFoundException;
import com.zero1blog.backend.model.Media;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.MediaRepository;
import com.zero1blog.backend.repository.UserRepository;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final Cloudinary cloudinary;
    private final MediaRepository mediaRepository;
    private final UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Value("${spring.servlet.multipart.max-file-size:10MB}")
    private String maxFileSize;

    public MediaController(Cloudinary cloudinary, MediaRepository mediaRepository, UserRepository userRepository) {
        this.cloudinary = cloudinary;
        this.mediaRepository = mediaRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadMedia(@RequestBody UploadMediaRequest req, Authentication authentication) {
        try {
            User uploader = userRepository.findByPublicId(authentication.getName())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            byte[] decoded = Base64.getDecoder().decode(
                    req.getData().contains(",") ? req.getData().split(",", 2)[1] : req.getData());

            long maxBytes = org.springframework.util.unit.DataSize.parse(maxFileSize).toBytes();
            if (decoded.length > maxBytes) {
                return ResponseEntity.badRequest().body(Map.of("error", "File size exceeds limit of " + maxFileSize));
            }

            // Upload directly to Cloudinary
            Map<?, ?> uploadResult = cloudinary.uploader().upload(decoded, ObjectUtils.emptyMap());
            String secureUrl = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");

            Media media = Media.builder()
                    .url(secureUrl)
                    .mediaType(req.getMediaType())
                    .fileName(publicId)
                    .uploader(uploader)
                    .size((long) decoded.length)
                    .build();
            mediaRepository.save(media);

            return ResponseEntity.ok(Map.of("url", secureUrl, "mediaType", req.getMediaType(), "size", decoded.length));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload to Cloudinary: " + e.getMessage()));
        }
    }
}
