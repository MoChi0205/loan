package com.loan.serviceops.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 小程序/H5 客户视角；只含本人可见服务协同字段。 */
@Data
public class CustomerAppointmentDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String appointmentNo;
    private String serviceMethod;
    private String serviceMethodName;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private String adviserName;
    private String locationName;
    private String locationDetail;
    private String status;
    private String statusName;
    private String nextAction;
    private Boolean changeAllowed;
}
