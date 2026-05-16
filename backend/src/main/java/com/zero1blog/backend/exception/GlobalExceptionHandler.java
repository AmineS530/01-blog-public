package com.zero1blog.backend.exception;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("Exception occurred: {}", ex.getMessage());
        
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = ex.getMessage();

        if (message != null) {
            String lowerMsg = message.toLowerCase();
            if (lowerMsg.contains("not found")) {
                status = HttpStatus.NOT_FOUND;
            } else if (lowerMsg.contains("invalid credentials") || lowerMsg.contains("banned") || lowerMsg.contains("unauthorized")) {
                status = HttpStatus.UNAUTHORIZED;
            } else if (lowerMsg.contains("already in use") || lowerMsg.contains("already taken")) {
                status = HttpStatus.BAD_REQUEST;
            }
        }

        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "error", message != null ? message : "Internal Server Error",
                        "status", status.value(),
                        "timestamp", LocalDateTime.now().toString()
                ));
    }
}