package com.prs.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeliverableDocumentDto {
    private Long id;
    private Long deliverableId;
    private Integer versionNumber;
    private String originalFilename;
    private String storageKey;
    private Long fileSize;
    private String contentType;
    private String checksum;
    private String versionNotes;
    private String submissionStatus;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}
