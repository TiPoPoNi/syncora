package com.prs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorAnalyticsSummaryDto {
    private int totalVendors;
    private int activeVendors;
    private int totalRecordedDeliveries;
    private int totalOnTimeDeliveries;
    private int totalLateDeliveries;

    private Double overallOnTimeRatePercent; // null if 0 recorded deliveries
    private Double averageDeliveryDelayDays;
    private int totalOutstandingCommitments;

    private List<VendorPerformanceItemDto> vendorDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VendorPerformanceItemDto {
        private Long vendorId;
        private String vendorName;
        private String company;
        private String services;
        private String status;

        private int totalDeliveries;
        private int onTimeDeliveries;
        private int lateDeliveries;
        private Double onTimeRatePercent; // null if 0 deliveries
        private Double averageDelayDays;
        private int outstandingCommitments;
        private int sampleSizeObservations;

        private Map<String, Integer> delayDistribution; // "ON_TIME", "1-3_DAYS", "4-7_DAYS", "OVER_7_DAYS"
    }
}
