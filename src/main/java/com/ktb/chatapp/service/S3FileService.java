package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.FileResponse;
import com.ktb.chatapp.dto.FileUploadRequest;
import com.ktb.chatapp.dto.PresignedFileResponse;
import com.ktb.chatapp.dto.PresignedUrlResponse;
import com.ktb.chatapp.model.File;
import com.ktb.chatapp.model.FileCategory;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.util.FileUtil;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3FileService implements FileService {

    private final FileRepository fileRepository;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.prefix:uploads}")
    private String basePrefix;

    @Value("${cloud.aws.s3.presign.expiration-seconds:900}")
    private long presignExpirationSeconds;

    @Override
    public PresignedUrlResponse uploadFile(FileUploadRequest request, String uploaderId, FileCategory category) {
        FileUtil.validateMetadata(request);

        String safeFilename = FileUtil.generateSafeFileName(request.getOriginalFilename());
        String normalizedOriginal = FileUtil.normalizeOriginalFilename(request.getOriginalFilename());
        String objectKey = buildObjectKey(uploaderId, category, safeFilename);

        File fileEntity = File.builder()
                .filename(safeFilename)
                .originalname(normalizedOriginal)
                .mimetype(request.getContentType())
                .size(request.getSize())
                .objectKey(objectKey)
                .user(uploaderId)
                .category(category)
                .uploadDate(LocalDateTime.now())
                .build();

        fileRepository.save(fileEntity);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
//                .contentType(request.getContentType())
//                .contentLength(request.getSize())
                .build();

        PresignedPutObjectRequest presignedPut = s3Presigner.presignPutObject(builder -> builder
                .signatureDuration(Duration.ofSeconds(presignExpirationSeconds))
                .putObjectRequest(putRequest));

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", request.getContentType());

        return PresignedUrlResponse.builder()
                .success(true)
                .uploadUrl(presignedPut.url().toString())
                .headers(headers)
                .file(FileResponse.from(fileEntity))
                .expiresAt(Instant.now().plusSeconds(presignExpirationSeconds))
                .build();
    }

    @Override
    public PresignedFileResponse loadFileAsResource(String fileName, String requesterId, boolean inline) {
        File fileEntity = fileRepository.findByFilename(fileName)
                .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다."));

        verifyAccess(fileEntity, requesterId);

        String contentDisposition = buildContentDisposition(fileEntity, inline);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(fileEntity.getObjectKey())
                .responseContentType(fileEntity.getMimetype())
                .responseContentDisposition(contentDisposition)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(builder -> builder
                .signatureDuration(Duration.ofSeconds(presignExpirationSeconds))
                .getObjectRequest(getObjectRequest));

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", fileEntity.getMimetype());

        return PresignedFileResponse.builder()
                .success(true)
                .url(presigned.url().toString())
                .headers(headers)
                .filename(fileEntity.getFilename())
                .contentType(fileEntity.getMimetype())
                .expiresAt(Instant.now().plusSeconds(presignExpirationSeconds))
                .inline(inline)
                .build();
    }

    @Override
    public boolean deleteFile(String fileId, String requesterId) {
        File fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다."));

        if (!fileEntity.getUser().equals(requesterId)) {
            throw new RuntimeException("파일을 삭제할 권한이 없습니다.");
        }

        deleteFromS3(fileEntity.getObjectKey());
        fileRepository.delete(fileEntity);
        return true;
    }

    @Override
    public boolean deleteFileByPath(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return false;
        }

        fileRepository.findByFilename(extractFilename(objectKey))
                .ifPresent(fileRepository::delete);

        deleteFromS3(objectKey);
        return true;
    }

    private void verifyAccess(File fileEntity, String requesterId) {
        if (FileCategory.PROFILE.equals(fileEntity.getCategory())) {
            if (!fileEntity.getUser().equals(requesterId)) {
                throw new RuntimeException("프로필 이미지에 접근할 권한이 없습니다.");
            }
            return;
        }

        Message message = messageRepository.findByFileId(fileEntity.getId())
                .orElseThrow(() -> new RuntimeException("파일과 연결된 메시지를 찾을 수 없습니다."));

        Room room = roomRepository.findById(message.getRoomId())
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

        if (!room.getParticipantIds().contains(requesterId)) {
            throw new RuntimeException("파일에 접근할 권한이 없습니다.");
        }
    }

    private String buildObjectKey(String uploaderId, FileCategory category, String filename) {
        String prefix = basePrefix;
        if (StringUtils.hasText(prefix)) {
            prefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        }
        return String.join("/",
                prefix,
                category.name().toLowerCase(),
                uploaderId,
                filename);
    }

    private String buildContentDisposition(File fileEntity, boolean inline) {
        String encoded = URLEncoder.encode(fileEntity.getOriginalname(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        if (inline) {
            return String.format("inline; filename=\"%s\"; filename*=UTF-8''%s", fileEntity.getOriginalname(), encoded);
        }
        return String.format("attachment; filename*=UTF-8''%s", encoded);
    }

    private void deleteFromS3(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (Exception e) {
            log.warn("S3 객체 삭제 실패: {}", e.getMessage());
        }
    }

    private String extractFilename(String objectKey) {
        int idx = objectKey.lastIndexOf('/');
        if (idx == -1) {
            return objectKey;
        }
        return objectKey.substring(idx + 1);
    }
}
