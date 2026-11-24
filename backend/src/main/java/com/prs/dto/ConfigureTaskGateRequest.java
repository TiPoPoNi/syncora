package com.prs.dto;

import com.prs.entity.GateType;
import lombok.Data;

@Data
public class ConfigureTaskGateRequest {
    private Long deliverableId;
    private GateType gateType = GateType.COMPLETION_GATE;
    private Boolean isMandatory = true;
}
