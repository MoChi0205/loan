package com.loan.report.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 公司员工内部报告详情响应。
 *
 * <p>包含匹配统计与规则日志，只能由服务端确认的 STAFF 身份读取，禁止复用于客户响应。
 */
@Data
public class StaffReportDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private String reportNo;
    private String clientProfileCode;
    private String templateCode;
    private String status;
    private LocalDateTime createdAt;
    private String grade;
    private String totalResult;
    private Integer productCount;
    private String rating;
    private Integer bankCount;
    private Integer passCount;
    private Integer conditionCount;
    private Integer rejectCount;
    private String adviceJson;
    private Integer vipFlag;
    private List<ReportRuleLog> ruleLogs;
}
