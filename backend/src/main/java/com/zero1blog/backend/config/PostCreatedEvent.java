package com.zero1blog.backend.config;

import com.zero1blog.backend.dto.PostResponse;
import org.springframework.context.ApplicationEvent;

public class PostCreatedEvent extends ApplicationEvent {

    private final PostResponse post;

    public PostCreatedEvent(Object source, PostResponse post) {
        super(source);
        this.post = post;
    }

    public PostResponse getPost() {
        return post;
    }
}
