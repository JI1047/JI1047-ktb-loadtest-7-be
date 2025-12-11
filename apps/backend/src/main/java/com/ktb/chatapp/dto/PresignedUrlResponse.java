package com.ktb.chatapp.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class PresignedUrlResponse {
    private boolean success;
    private String uploadUrl;
    private Map<String, String> headers;
    private FileResponse file;
    private Instant expiresAt;
}
