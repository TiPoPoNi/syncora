package com.prs.dto;

import com.prs.entity.DeliverableStatus;
import com.prs.entity.GateType;
import lombok.Data;

@Data
public class TaskApprovalGateDto {
    private Long id;
    private Long taskId;
    private String taskName;
    private Long deliverableId;
    private String deliverableName;
    private DeliverableStatus deliverableStatus;
    private GateType gateType;
    private Boolean isMandatory;
    private Boolean isSatisfied;
}
