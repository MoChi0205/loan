package com.loan.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 短信/报告模板统一审批记录。 */
@Data
@TableName("t_content_approval")
public class ContentApproval {
    @TableId(type = IdType.AUTO) private Long id;
    private String approvalNo;
    private String approvalType;
    private String targetCode;
    private Integer targetVersion;
    private String applicantStaffCode;
    private String status;
    private String opinion;
    private String approverStaffCode;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime updatedAt;
}
