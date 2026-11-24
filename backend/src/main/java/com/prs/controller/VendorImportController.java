package com.prs.controller;

import com.prs.dto.VendorImportPreviewDto;
import com.prs.service.VendorCsvImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vendors/import")
@RequiredArgsConstructor

public class VendorImportController {

    private final VendorCsvImportService vendorCsvImportService;

    @PostMapping("/preview")
    public ResponseEntity<VendorImportPreviewDto> previewImport(@RequestParam("file") MultipartFile file) {
        VendorImportPreviewDto preview = vendorCsvImportService.previewImport(file);
        return ResponseEntity.ok(preview);
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmImport(
            @RequestBody List<VendorImportPreviewDto.VendorRowDto> records) {
        int count = vendorCsvImportService.confirmImport(records);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "importedCount", count,
                "message", "Successfully imported " + count + " new vendor(s)."
        ));
    }
}
