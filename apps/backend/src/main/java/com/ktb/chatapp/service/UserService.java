package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.*;
import com.ktb.chatapp.model.FileCategory;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.util.ImageValidationUtil;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final FileService fileService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.profile.image.max-size:5242880}")
    private long maxProfileImageSize;

    public UserResponse getCurrentUserProfile(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }

    public UserResponse updateUserProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        user.setName(request.getName());
        user.setUpdatedAt(LocalDateTime.now());

        if ((request.getNewPassword() != null) && !(request.getNewPassword().isEmpty())
                && (request.getNewPassword().equals(request.getConfirmPassword()))) {
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        User updatedUser = userRepository.save(user);
        log.info("사용자 프로필 업데이트 완료 - ID: {}, Name: {}", user.getId(), request.getName());

        return UserResponse.from(updatedUser);
    }

    /**
     * 프로필 이미지 조회
     * @param email 사용자 이메일
     */
    public ProfileImageUrlResponse getProfileImage(String email) {
        // 사용자 조회
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        String profileImageUrl = user.getProfileImage();

        log.info("프로필 이미지 조회 완료 - User ID: {}, Image Url: {}", user.getId(), profileImageUrl);

        return ProfileImageUrlResponse.builder()
                .success(true)
                .message("프로필 이미지를 조회합니다.")
                .profileImageUrl(profileImageUrl)
                .build();
    }

    /**
     * 프로필 이미지 업로드
     * @param email 사용자 이메일
     */
    public ProfileImageResponse uploadProfileImage(String email, FileUploadRequest request) {
        // 사용자 조회
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        ImageValidationUtil.validateProfileImage(request, maxProfileImageSize);

        fileRepository.findByFilename(user.getProfileImage()).ifPresent(file -> {
            try {
                fileService.deleteFile(file.getId(), user.getId());
            } catch (Exception e) {
                log.warn("기존 프로필 이미지 정리 실패: {}", e.getMessage());
            }
        });

        PresignedUrlResponse presignedUrl = fileService.uploadFile(request, user.getId(), FileCategory.PROFILE);

        user.setProfileImage(presignedUrl.getUploadUrl());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("프로필 이미지 업로드 presigned URL 발급 - User ID: {}, File: {}", user.getId(), presignedUrl.getFile().getFilename());

        return ProfileImageResponse.builder()
                .success(true)
                .message("프로필 이미지 업로드 URL이 생성되었습니다.")
                .filename(presignedUrl.getFile().getFilename())
                .uploadUrl(presignedUrl.getUploadUrl())
                .expiresAt(presignedUrl.getExpiresAt())
                .headers(presignedUrl.getHeaders())
                .file(presignedUrl.getFile())
                .build();
    }

    public UserResponse getUserProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        return UserResponse.from(user);
    }

    public void deleteProfileImage(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            fileRepository.findByFilename(user.getProfileImage()).ifPresent(file -> {
                try {
                    fileService.deleteFile(file.getId(), user.getId());
                } catch (Exception e) {
                    log.warn("프로필 이미지 삭제 실패: {}", e.getMessage());
                }
            });
            user.setProfileImage("");
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("프로필 이미지 삭제 완료 - User ID: {}", user.getId());
        }
    }

    public void deleteUserAccount(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            fileRepository.findByFilename(user.getProfileImage()).ifPresent(file -> {
                try {
                    fileService.deleteFile(file.getId(), user.getId());
                } catch (Exception e) {
                    log.warn("회원 탈퇴 시 프로필 이미지 삭제 실패: {}", e.getMessage());
                }
            });
        }

        userRepository.delete(user);
        log.info("회원 탈퇴 완료 - User ID: {}", user.getId());
    }
}
