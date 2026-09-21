package com.loan.serviceops.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Web 员工外出视角；不向客户或渠道返回，定位解密结果按操作人最小授权。 */
@Data
public class StaffOutingDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String outingNo;
    private String staffCode;
    private String staffName;
    private String deptCode;
    private String clientCode;
    private String customerName;
    private String appointmentNo;
    private String orderNo;
    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;
    private LocalDateTime actualDepartedAt;
    private LocalDateTime actualReturnedAt;
    private String destination;
    private String purpose;
    private String status;
    private Boolean departureCheckInCompleted;
    private Boolean returnCheckInCompleted;
}
