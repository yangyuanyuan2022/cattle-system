package com.cattlefarm.admin.attachment;

import java.time.LocalDateTime;

public record AttachmentItem(
        String attachmentId,
        String businessType,
        String businessId,
        String fileName,
        String fileType,
        long fileSize,
        String uploadedBy,
        LocalDateTime uploadedAt
) {
}
