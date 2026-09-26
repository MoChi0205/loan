package com.loan.sensitive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 超出敏感手机号日额度后的专用审批单。 */
@Data
@TableName("t_sensitive_view_approval")
public class SensitiveViewApproval implements Serializable {
    @TableId(type = IdType.AUTO) private Long id;
    private String approvalNo;
    private String clientCode;
    private String applicantStaffCode;
    private String applicantRoleCode;
    private String applicantDeptCode;
    private java.time.LocalDate viewDate;
    private String approverStaffCode;
    private String approvalStage;
    private String approveStatus;
    private String approveOpinion;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}
