package com.ktb.chatapp.util;

import com.ktb.chatapp.dto.FileUploadRequest;
import java.util.Set;

public final class ImageValidationUtil {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp"
    );

    private ImageValidationUtil() {
    }

    public static void validateProfileImage(FileUploadRequest request, long maxSize) {
        FileUtil.validateMetadata(request);

        if (!ALLOWED_IMAGE_TYPES.contains(request.getContentType())) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        if (request.getSize() > maxSize) {
            throw new IllegalArgumentException("파일 크기는 " + (maxSize / (1024 * 1024)) + "MB를 초과할 수 없습니다.");
        }
    }
}
