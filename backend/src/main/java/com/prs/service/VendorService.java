package com.prs.service;

import com.prs.dto.VendorDto;
import com.prs.entity.Organization;
import com.prs.entity.Vendor;
import com.prs.repository.OrganizationRepository;
import com.prs.repository.VendorRepository;
import com.prs.tenant.TenantAccessDeniedException;
import com.prs.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VendorService {
    private final VendorRepository vendorRepository;
    private final OrganizationRepository organizationRepository;

    public List<VendorDto> getAllVendors() {
        Long tenantId = TenantContext.getTenantId();
        List<Vendor> vendors;
        if (tenantId != null) {
            vendors = vendorRepository.findByOrganizationId(tenantId);
        } else {
            vendors = vendorRepository.findAll();
        }
        return vendors.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public VendorDto getVendorById(Long id) {
        Vendor vendor = vendorRepository.findById(id).orElseThrow();
        validateTenantAccess(vendor);
        return mapToDto(vendor);
    }

    public VendorDto createVendor(VendorDto vendorDto) {
        Vendor vendor = new Vendor();
        BeanUtils.copyProperties(vendorDto, vendor, "id", "createdAt", "updatedAt");
        
        Organization org = TenantContext.getOrganization();
        if (org == null && TenantContext.getTenantId() != null) {
            org = organizationRepository.findById(TenantContext.getTenantId()).orElse(null);
        }
        if (org == null) {
            org = organizationRepository.findAll().stream().findFirst().orElse(null);
        }
        vendor.setOrganization(org);

        return mapToDto(vendorRepository.save(vendor));
    }

    public VendorDto updateVendor(Long id, VendorDto vendorDto) {
        Vendor vendor = vendorRepository.findById(id).orElseThrow();
        validateTenantAccess(vendor);
        BeanUtils.copyProperties(vendorDto, vendor, "id", "createdAt", "updatedAt", "organization");
        return mapToDto(vendorRepository.save(vendor));
    }

    public void deleteVendor(Long id) {
        Vendor vendor = vendorRepository.findById(id).orElseThrow();
        validateTenantAccess(vendor);
        vendorRepository.deleteById(id);
    }

    private void validateTenantAccess(Vendor vendor) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && vendor.getOrganization() != null && !vendor.getOrganization().getId().equals(tenantId)) {
            throw new TenantAccessDeniedException("Access denied: Vendor does not belong to active organization.");
        }
    }

    private VendorDto mapToDto(Vendor vendor) {
        VendorDto dto = new VendorDto();
        BeanUtils.copyProperties(vendor, dto);
        return dto;
    }
}
