package com.ktb.chatapp.dto;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PresignedFileResponse {
    private boolean success;
    private String url;
    private Map<String, String> headers;
    private String filename;
    private String contentType;
    private Instant expiresAt;
    private boolean inline;
}
