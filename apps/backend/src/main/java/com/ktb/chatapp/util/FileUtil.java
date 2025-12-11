package com.ktb.chatapp.util;

import com.ktb.chatapp.dto.FileUploadRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class FileUtil {

    private static final Map<String, List<String>> ALLOWED_TYPES = Map.ofEntries(
            Map.entry("image/jpeg", Arrays.asList("jpg", "jpeg")),
            Map.entry("image/png", Arrays.asList("png")),
            Map.entry("image/gif", Arrays.asList("gif")),
            Map.entry("image/webp", Arrays.asList("webp")),
            Map.entry("video/mp4", Arrays.asList("mp4")),
            Map.entry("video/webm", Arrays.asList("webm")),
            Map.entry("video/quicktime", Arrays.asList("mov")),
            Map.entry("audio/mpeg", Arrays.asList("mp3")),
            Map.entry("audio/wav", Arrays.asList("wav")),
            Map.entry("audio/ogg", Arrays.asList("ogg")),
            Map.entry("application/pdf", Arrays.asList("pdf")),
            Map.entry("application/msword", Arrays.asList("doc")),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Arrays.asList("docx"))
    );

    private static final Map<String, Long> FILE_SIZE_LIMITS = Map.of(
            "image", 10L * 1024 * 1024,
            "video", 50L * 1024 * 1024,
            "audio", 20L * 1024 * 1024,
            "application", 20L * 1024 * 1024
    );

    private static final SecureRandom secureRandom = new SecureRandom();

    private FileUtil() {
    }

    public static void validateMetadata(FileUploadRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("파일 정보가 제공되지 않았습니다.");
        }

        String originalFilename = request.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("파일명이 올바르지 않습니다.");
        }

        int filenameBytes = originalFilename.getBytes(StandardCharsets.UTF_8).length;
        if (filenameBytes > 255) {
            throw new IllegalArgumentException("파일명이 너무 깁니다.");
        }

        String contentType = request.getContentType();
        if (contentType == null || !ALLOWED_TYPES.containsKey(contentType)) {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다.");
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        List<String> allowedExtensions = ALLOWED_TYPES.get(contentType);
        if (allowedExtensions == null || !allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException(getFileType(contentType) + " 확장자가 올바르지 않습니다.");
        }

        if (request.getSize() <= 0) {
            throw new IllegalArgumentException("파일 크기가 올바르지 않습니다.");
        }

        String type = contentType.split("/")[0];
        long limit = FILE_SIZE_LIMITS.getOrDefault(type, FILE_SIZE_LIMITS.get("application"));
        if (request.getSize() > limit) {
            int limitInMB = (int) (limit / 1024 / 1024);
            throw new IllegalArgumentException(getFileType(contentType) + " 파일은 " + limitInMB + "MB를 초과할 수 없습니다.");
        }
    }

    private static String getFileType(String mimetype) {
        if (mimetype == null) {
            return "파일";
        }
        String type = mimetype.split("/")[0];
        return switch (type) {
            case "image" -> "이미지";
            case "video" -> "동영상";
            case "audio" -> "오디오";
            case "application" -> "문서";
            default -> "파일";
        };
    }

    public static String getFileExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }

        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDot + 1);
    }

    public static String generateSafeFileName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return generateRandomFileName("file");
        }

        String extension = getFileExtension(originalFilename);
        long timestamp = Instant.now().toEpochMilli();
        byte[] randomBytes = new byte[8];
        secureRandom.nextBytes(randomBytes);
        String randomHex = bytesToHex(randomBytes);

        if (!extension.isEmpty()) {
            return String.format("%d_%s.%s", timestamp, randomHex, extension);
        }
        return String.format("%d_%s", timestamp, randomHex);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String generateRandomFileName(String prefix) {
        long timestamp = Instant.now().toEpochMilli();
        int random = secureRandom.nextInt(10000);
        return String.format("%s_%d_%04d", prefix, timestamp, random);
    }

    public static String normalizeOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        String cleaned = originalFilename.replaceAll("[/\\\\]", "");
        return java.text.Normalizer.normalize(cleaned, java.text.Normalizer.Form.NFC);
    }
}
