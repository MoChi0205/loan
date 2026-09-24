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
    /** 客户自己填写的补充说明，不代表系统动作。 */
    private String customerNote;
    /** 服务端根据预约状态生成的下一步动作。 */
    private String nextActionText;
    /** 旧字段兼容一版客户端；新客户端使用 customerNote / nextActionText。 */
    @Deprecated
    private String nextAction;
    private Boolean changeAllowed;
}
