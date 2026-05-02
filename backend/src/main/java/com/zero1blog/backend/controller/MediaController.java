package com.zero1blog.backend.controller;

import com.zero1blog.backend.dto.UploadMediaRequest;
import com.zero1blog.backend.model.Media;
import com.zero1blog.backend.model.User;
import com.zero1blog.backend.repository.MediaRepository;
import com.zero1blog.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    @Value("${app.upload.dir:/home/amines/Desktop/01-blog/backend/uploads/}")
    private String uploadDir;

    private final MediaRepository mediaRepository;
    private final UserRepository userRepository;

    public MediaController(MediaRepository mediaRepository, UserRepository userRepository) {
        this.mediaRepository = mediaRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadMedia(@RequestBody UploadMediaRequest req, Authentication authentication) {
        try {
            User uploader = userRepository.findByPublicId(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            String originalName = req.getFileName();
            String ext = originalName.contains(".") ?
                    originalName.substring(originalName.lastIndexOf(".")) : "";
            String filename = UUID.randomUUID().toString() + ext;

            byte[] decoded = Base64.getDecoder().decode(
                    req.getData().contains(",") ? req.getData().split(",", 2)[1] : req.getData());

            File file = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(decoded);
            }

            String url = "/api/media/files/" + filename;

            Media media = Media.builder()
                    .url(url)
                    .mediaType(req.getMediaType())
                    .fileName(filename)
                    .uploader(uploader)
                    .build();
            mediaRepository.save(media);

            return ResponseEntity.ok(Map.of("url", url, "mediaType", req.getMediaType()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload media: " + e.getMessage()));
        }
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<?> getFile(@PathVariable String filename) {
        Path filePath = Paths.get(uploadDir).resolve(filename);
        File file = filePath.toFile();
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        // Simplified file serving
        return ResponseEntity.ok().body(new org.springframework.core.io.FileSystemResource(file));
    }
}
