package com.loan.serviceops.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Web 员工视角；包含履约操作所需信息，但不包含银行产品或准入匹配字段。 */
@Data
public class StaffAppointmentDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String appointmentNo;
    private String clientCode;
    private String customerGroup;
    private String customerName;
    private String contactName;
    private String contactPhoneMasked;
    private String hostStaffCode;
    private String hostStaffName;
    private String hostDeptCode;
    private String serviceMethod;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private String locationName;
    private String locationDetail;
    private String status;
    private String customerConfirmStatus;
    private String customerVisibleNote;
    private String internalNote;
    private String createdByType;
    private String createdByCode;
    private String sourceTerminal;
    private String rescheduledFromNo;
    private LocalDateTime actualArrivedAt;
    private LocalDateTime actualLeftAt;
}
