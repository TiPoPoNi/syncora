package com.prs.service;

import com.prs.dto.VendorImportPreviewDto;
import com.prs.entity.Vendor;
import com.prs.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorCsvImportService {

    private final VendorRepository vendorRepository;

    public VendorImportPreviewDto previewImport(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file cannot be empty");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.toLowerCase().endsWith(".csv") && !filename.toLowerCase().endsWith(".txt"))) {
            throw new IllegalArgumentException("Only CSV files (.csv) are accepted for vendor import");
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds 2MB limit");
        }

        VendorImportPreviewDto preview = new VendorImportPreviewDto();
        Set<String> seenNamesInFile = new HashSet<>();
        Set<String> existingDbNames = new HashSet<>();
        Long tenantId = com.prs.tenant.TenantContext.getTenantId();
        List<Vendor> existingVendors = (tenantId != null)
                ? vendorRepository.findByOrganizationId(tenantId)
                : vendorRepository.findAll();
        existingVendors.forEach(v -> existingDbNames.add(v.getName().toLowerCase().trim()));

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }

            // Validate header
            String[] headers = parseCsvLine(headerLine);
            Map<String, Integer> headerIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerIndex.put(headers[i].toLowerCase().trim(), i);
            }

            if (!headerIndex.containsKey("name")) {
                throw new IllegalArgumentException("CSV header must contain at least a 'name' column");
            }

            int rowNum = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.trim().isEmpty()) continue;

                String[] columns = parseCsvLine(line);
                String name = getColumn(columns, headerIndex.get("name"));
                String company = getColumn(columns, headerIndex.get("company"));
                String email = getColumn(columns, headerIndex.get("email"));
                String phone = getColumn(columns, headerIndex.get("phone"));
                String services = getColumn(columns, headerIndex.get("services"));

                // Validation
                if (name == null || name.isBlank()) {
                    preview.getErrors().add(new VendorImportPreviewDto.RowErrorDto(
                            rowNum, "name", "Vendor name is required", line));
                    continue;
                }

                if (email != null && !email.isBlank() && (!email.contains("@") || !email.contains("."))) {
                    preview.getErrors().add(new VendorImportPreviewDto.RowErrorDto(
                            rowNum, "email", "Invalid email format: " + email, line));
                    continue;
                }

                String lowerName = name.toLowerCase().trim();
                boolean duplicateInFile = !seenNamesInFile.add(lowerName);
                if (duplicateInFile) {
                    preview.getErrors().add(new VendorImportPreviewDto.RowErrorDto(
                            rowNum, "name", "Duplicate vendor name within file: " + name, line));
                    continue;
                }

                boolean duplicateInDb = existingDbNames.contains(lowerName);

                preview.getValidRecords().add(VendorImportPreviewDto.VendorRowDto.builder()
                        .rowNumber(rowNum)
                        .name(name)
                        .company(company != null ? company : "")
                        .email(email != null ? email : "")
                        .phone(phone != null ? phone : "")
                        .services(services != null ? services : "General")
                        .duplicateInDatabase(duplicateInDb)
                        .build());
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse vendor CSV: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Error parsing CSV: " + e.getMessage());
        }

        preview.setTotalRows(preview.getValidRecords().size() + preview.getErrors().size());
        preview.setValidRowsCount(preview.getValidRecords().size());
        preview.setErrorRowsCount(preview.getErrors().size());
        return preview;
    }

    @Transactional
    public int confirmImport(List<VendorImportPreviewDto.VendorRowDto> records) {
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("No records provided to import");
        }

        int createdCount = 0;
        com.prs.entity.Organization currentOrg = com.prs.tenant.TenantContext.getOrganization();
        for (VendorImportPreviewDto.VendorRowDto row : records) {
            if (row.isDuplicateInDatabase()) {
                log.info("Skipping vendor '{}' as it already exists in database", row.getName());
                continue;
            }

            Vendor v = new Vendor();
            v.setName(row.getName());
            v.setCompany(row.getCompany());
            v.setEmail(row.getEmail());
            v.setPhone(row.getPhone());
            v.setServices(row.getServices());
            v.setStatus("ACTIVE");
            v.setOrganization(currentOrg);
            vendorRepository.save(v);
            createdCount++;
        }
        return createdCount;
    }

    private String getColumn(String[] cols, Integer idx) {
        if (idx == null || idx >= cols.length) return null;
        return cols[idx].trim();
    }

    private String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                    sb.append('\"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens.toArray(new String[0]);
    }
}
