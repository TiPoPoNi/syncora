package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorImportPreviewDto {
    private int totalRows;
    private int validRowsCount;
    private int errorRowsCount;
    @Builder.Default
    private List<VendorRowDto> validRecords = new ArrayList<>();
    @Builder.Default
    private List<RowErrorDto> errors = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VendorRowDto {
        private int rowNumber;
        private String name;
        private String company;
        private String email;
        private String phone;
        private String services;
        private boolean duplicateInDatabase;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowErrorDto {
        private int rowNumber;
        private String field;
        private String errorMessage;
        private String rawContent;
    }
}
