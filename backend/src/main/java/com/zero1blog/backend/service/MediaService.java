package com.zero1blog.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.zero1blog.backend.model.Media;
import com.zero1blog.backend.repository.MediaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final Cloudinary cloudinary;
    private final MediaRepository mediaRepository;

    public MediaService(Cloudinary cloudinary, MediaRepository mediaRepository) {
        this.cloudinary = cloudinary;
        this.mediaRepository = mediaRepository;
    }

    /**
     * Finds a Media entry by its URL, deletes the asset from Cloudinary,
     * and deletes the corresponding database row.
     *
     * @param mediaUrl the secure URL of the media asset.
     */
    @Transactional
    public void cleanupMedia(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isEmpty()) {
            return;
        }

        Optional<Media> mediaOpt = mediaRepository.findByUrl(mediaUrl);
        if (mediaOpt.isEmpty()) {
            log.warn("No Media database entry found for URL: {}", mediaUrl);
            return;
        }

        Media media = mediaOpt.get();
        String publicId = media.getFileName();

        String resourceType = media.getResourceType();
        if (resourceType == null) {
            resourceType = "image";
        }

        try {
            // Call destroy with publicId and resource_type mapping
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", resourceType));
            // Also delete the corresponding Media row from the database after a successful Cloudinary destroy call
            mediaRepository.delete(media);
        } catch (Exception e) {
            log.warn("Failed to delete media asset from Cloudinary for URL: {}. Error: {}", mediaUrl, e.getMessage(), e);
        }
    }
}
