package com.prs.controller;

import com.prs.dto.VendorDto;
import com.prs.service.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vendors")
@RequiredArgsConstructor

public class VendorController {
    private final VendorService vendorService;

    @GetMapping
    public List<VendorDto> getAllVendors() {
        return vendorService.getAllVendors();
    }

    @GetMapping("/{id}")
    public VendorDto getVendorById(@PathVariable Long id) {
        return vendorService.getVendorById(id);
    }

    @PostMapping
    public VendorDto createVendor(@RequestBody VendorDto vendorDto) {
        return vendorService.createVendor(vendorDto);
    }

    @PutMapping("/{id}")
    public VendorDto updateVendor(@PathVariable Long id, @RequestBody VendorDto vendorDto) {
        return vendorService.updateVendor(id, vendorDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVendor(@PathVariable Long id) {
        vendorService.deleteVendor(id);
        return ResponseEntity.ok().build();
    }
}
