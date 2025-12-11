package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.FileUploadRequest;
import com.ktb.chatapp.dto.PresignedFileResponse;
import com.ktb.chatapp.dto.PresignedUrlResponse;
import com.ktb.chatapp.model.FileCategory;

public interface FileService {

    PresignedUrlResponse uploadFile(FileUploadRequest request, String uploaderId, FileCategory category);
    
    PresignedFileResponse loadFileAsResource(String fileName, String requesterId, boolean inline);
    
    boolean deleteFile(String fileId, String requesterId);
    
    boolean deleteFileByPath(String objectKey);
}
