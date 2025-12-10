package com.ktb.chatapp.dto;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PresignedUrlResponse {
    private boolean success;
    private String uploadUrl;
    private Map<String, String> headers;
    private FileResponse file;
    private Instant expiresAt;
}
