package com.prs.controller;

import com.prs.service.CalendarExportService;
import com.prs.service.CsvExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor

public class ProjectExportController {

    private final CalendarExportService calendarExportService;
    private final CsvExportService csvExportService;

    @GetMapping(value = "/api/projects/{projectId}/calendar.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<byte[]> exportCalendar(@PathVariable Long projectId) {
        String icsContent = calendarExportService.generateProjectIcsCalendar(projectId);
        byte[] bytes = icsContent.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"project_" + projectId + "_schedule.ics\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping(value = "/api/projects/{projectId}/exports/tasks.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportTasksCsv(@PathVariable Long projectId) {
        String csv = csvExportService.exportTasksCsv(projectId);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"project_" + projectId + "_tasks.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping(value = "/api/projects/{projectId}/exports/assignments.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportAssignmentsCsv(@PathVariable Long projectId) {
        String csv = csvExportService.exportAssignmentsCsv(projectId);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"project_" + projectId + "_assignments.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping(value = "/api/projects/{projectId}/exports/delays.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportDelaysCsv(@PathVariable Long projectId) {
        String csv = csvExportService.exportDelaysCsv(projectId);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"project_" + projectId + "_delays.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }
}
