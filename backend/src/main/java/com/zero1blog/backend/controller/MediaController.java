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

    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm", "video/ogg"
    );

    private boolean isValidMagicBytes(String mediaType, byte[] decoded) {
        if (mediaType == null) return false;
        String type = mediaType.toLowerCase();
        
        if (type.equals("image/jpeg") || type.equals("image/jpg")) {
            return decoded.length >= 3 && decoded[0] == (byte) 0xFF && decoded[1] == (byte) 0xD8;
        }
        if (type.equals("image/png")) {
            return decoded.length >= 8 && decoded[0] == (byte) 0x89 && decoded[1] == (byte) 0x50 
                    && decoded[2] == (byte) 0x4E && decoded[3] == (byte) 0x47;
        }
        if (type.equals("image/gif")) {
            return decoded.length >= 3 && decoded[0] == (byte) 'G' && decoded[1] == (byte) 'I' 
                    && decoded[2] == (byte) 'F';
        }
        if (type.equals("image/webp")) {
            return decoded.length >= 12 && decoded[0] == (byte) 'R' && decoded[1] == (byte) 'I' 
                    && decoded[2] == (byte) 'F' && decoded[3] == (byte) 'F'
                    && decoded[8] == (byte) 'W' && decoded[9] == (byte) 'E' 
                    && decoded[10] == (byte) 'B' && decoded[11] == (byte) 'P';
        }
        if (type.equals("video/mp4")) {
            return decoded.length >= 8 && decoded[4] == (byte) 'f' && decoded[5] == (byte) 't' 
                    && decoded[6] == (byte) 'y' && decoded[7] == (byte) 'p';
        }
        if (type.equals("video/webm")) {
            return decoded.length >= 4 && decoded[0] == (byte) 0x1A && decoded[1] == (byte) 0x45 
                    && decoded[2] == (byte) 0xDF && decoded[3] == (byte) 0xA3;
        }
        if (type.equals("video/ogg")) {
            return decoded.length >= 4 && decoded[0] == (byte) 'O' && decoded[1] == (byte) 'g' 
                    && decoded[2] == (byte) 'g' && decoded[3] == (byte) 'S';
        }
        return false;
    }

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

            String mediaType = req.getMediaType();
            if (mediaType == null || !ALLOWED_TYPES.contains(mediaType.toLowerCase())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unsupported media type: " + mediaType));
            }

            if (!isValidMagicBytes(mediaType, decoded)) {
                return ResponseEntity.badRequest().body(Map.of("error", "File content does not match declared type"));
            }

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
