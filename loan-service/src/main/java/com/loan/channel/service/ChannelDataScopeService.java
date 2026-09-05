package com.loan.channel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.client.service.ClientService;
import com.loan.common.ResultCode;
import com.loan.common.service.BusinessNameService;
import com.loan.common.util.BatchQueryUtils;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.HashUtils;
import com.loan.report.entity.ClientScreening;
import com.loan.report.mapper.ClientScreeningMapper;
import com.loan.report.service.ReportService;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 渠道数据范围唯一入口：仅允许查看本人录入并已转化的客户、报告和归属信息。
 *
 * <p>页面隐藏不是安全边界；列表和详情均在数据库查询中按渠道业务编号校验。
 */
@Service
@RequiredArgsConstructor
public class ChannelDataScopeService {

    private final ClientProfileMapper clientProfileMapper;
    private final ClientScreeningMapper screeningMapper;
    private final ClientService clientService;
    private final ReportService reportService;
    private final BusinessNameService businessNameService;

    public PageResult<Map<String, Object>> clientPage(String keyword, int page, int size,
                                                       String orderDir, LoanUser user) {
        String channelNo = requireChannel(user);
        String normalizedKeyword = normalize(keyword);
        String phoneHash = isPhone(normalizedKeyword) ? HashUtils.sha256Hex(normalizedKeyword) : null;
        Page<ClientProfile> result = clientProfileMapper.selectChannelOwnedPage(
                new Page<>(page, size), channelNo, normalizedKeyword, phoneHash, normalizeOrderDir(orderDir));

        List<Map<String, Object>> records = clientSummaries(result.getRecords());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /** 渠道本人客户批量摘要，保持请求顺序；未命中或越权编码不返回。 */
    public List<Map<String, Object>> clientBatch(List<String> clientCodes, LoanUser user) {
        String channelNo = requireChannel(user);
        List<String> codes = BatchQueryUtils.normalizeCodes(clientCodes);
        Map<String, ClientProfile> byCode = clientProfileMapper.selectChannelOwnedByCodes(channelNo, codes).stream()
                .collect(Collectors.toMap(ClientProfile::getClientCode, client -> client, (left, right) -> left));
        List<ClientProfile> ordered = codes.stream().map(byCode::get).filter(client -> client != null)
                .collect(Collectors.toList());
        return clientSummaries(ordered);
    }

    public Map<String, Object> clientDetail(String clientCode, LoanUser user) {
        String channelNo = requireChannel(user);
        if (!StringUtils.hasText(clientCode)
                || clientProfileMapper.countChannelOwnedClient(channelNo, clientCode.trim()) <= 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该客户档案");
        }
        Map<String, Object> detail = clientService.getClientDetail(clientCode.trim());
        if (detail == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户档案不存在");
        }
        // 渠道只需要理解归属姓名，不向其暴露归属工号或个人档内部关联编码。
        detail.remove("ownerStaffCode");
        Object personal = detail.get("personal");
        if (personal instanceof Map) {
            ((Map<?, ?>) personal).remove("clientProfileCode");
        }
        return detail;
    }

    public PageResult<Map<String, Object>> reportPage(String status, String grade, String keyword,
                                                       int page, int size, String orderDir, LoanUser user) {
        String channelNo = requireChannel(user);
        String normalizedKeyword = normalize(keyword);
        String phoneHash = isPhone(normalizedKeyword) ? HashUtils.sha256Hex(normalizedKeyword) : null;
        Page<ClientScreening> result = screeningMapper.selectChannelOwnedPage(new Page<>(page, size), channelNo,
                normalize(status), normalize(grade), normalizedKeyword, phoneHash, normalizeOrderDir(orderDir));

        List<Map<String, Object>> records = reportSummaries(result.getRecords());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    public Map<String, Object> reportDetail(String reportNo, LoanUser user) {
        String channelNo = requireChannel(user);
        if (!StringUtils.hasText(reportNo)
                || screeningMapper.countChannelOwnedReport(channelNo, reportNo.trim()) <= 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该客户分析报告");
        }
        Map<String, Object> detail = reportService.screeningDetail(reportNo.trim());
        if (detail == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "分析报告不存在");
        }
        Object clientCode = detail.get("clientProfileCode");
        if (clientCode != null) {
            ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                    .eq(ClientProfile::getClientCode, String.valueOf(clientCode)));
            detail.put("clientName", displayName(client));
            detail.put("phone", client == null ? null : DesensitizeUtils.phone(decrypt(client.getPhone())));
            detail.put("ownerStaffName", client == null || !StringUtils.hasText(client.getOwnerStaffCode()) ? null
                    : businessNameService.staffNames(Collections.singletonList(client.getOwnerStaffCode()))
                    .get(client.getOwnerStaffCode()));
        }
        // 渠道报告仅展示业务结果，客户关联编码和匹配 trace 留在服务内部。
        detail.remove("clientProfileCode");
        detail.remove("matchTraceUuid");
        detail.remove("templateCode");
        return detail;
    }

    /** 渠道本人客户报告批量摘要，保持请求顺序；未命中或越权编号不返回。 */
    public List<Map<String, Object>> reportBatch(List<String> reportNos, LoanUser user) {
        String channelNo = requireChannel(user);
        List<String> codes = BatchQueryUtils.normalizeCodes(reportNos);
        Map<String, ClientScreening> byNo = screeningMapper.selectChannelOwnedByReportNos(channelNo, codes).stream()
                .collect(Collectors.toMap(ClientScreening::getReportNo, report -> report, (left, right) -> left));
        List<ClientScreening> ordered = codes.stream().map(byNo::get).filter(report -> report != null)
                .collect(Collectors.toList());
        return reportSummaries(ordered);
    }

    public String requireChannel(LoanUser user) {
        if (user == null || !LoanUser.TYPE_CHANNEL.equals(user.getUserType())
                || !StringUtils.hasText(user.getUserNo())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅合作渠道账号可访问");
        }
        return user.getUserNo();
    }

    private Map<String, Object> reportSummary(ClientScreening report) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reportNo", report.getReportNo());
        row.put("grade", report.getGrade());
        row.put("bankCount", report.getBankCount());
        row.put("productCount", report.getProductCount());
        row.put("passCount", report.getPassCount());
        row.put("conditionCount", report.getConditionCount());
        row.put("rejectCount", report.getRejectCount());
        row.put("status", report.getStatus());
        row.put("createdAt", report.getCreatedAt());
        return row;
    }

    private List<Map<String, Object>> clientSummaries(List<ClientProfile> clients) {
        List<String> ownerCodes = clients.stream().map(ClientProfile::getOwnerStaffCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        Map<String, String> ownerNames = businessNameService.staffNames(ownerCodes);
        return clients.stream().map(client -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("clientCode", client.getClientCode());
            row.put("clientName", displayName(client));
            row.put("contactName", client.getContactName());
            row.put("enterpriseName", client.getEnterpriseName());
            row.put("customerGroup", client.getCustomerGroup());
            row.put("phone", DesensitizeUtils.phone(decrypt(client.getPhone())));
            row.put("ownerStaffName", ownerNames.get(client.getOwnerStaffCode()));
            row.put("assigned", StringUtils.hasText(client.getOwnerStaffCode()));
            row.put("status", client.getStatus());
            row.put("createdAt", client.getCreatedAt());
            return row;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> reportSummaries(List<ClientScreening> reports) {
        List<String> clientCodes = reports.stream().map(ClientScreening::getClientProfileCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        Map<String, ClientProfile> clients = clientCodes.isEmpty() ? Collections.emptyMap()
                : clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                        .in(ClientProfile::getClientCode, clientCodes)).stream()
                .collect(Collectors.toMap(ClientProfile::getClientCode, client -> client, (left, right) -> left));
        List<String> ownerCodes = clients.values().stream().map(ClientProfile::getOwnerStaffCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        Map<String, String> ownerNames = businessNameService.staffNames(ownerCodes);
        return reports.stream().map(report -> {
            ClientProfile client = clients.get(report.getClientProfileCode());
            Map<String, Object> row = reportSummary(report);
            row.put("clientName", displayName(client));
            row.put("ownerStaffName", client == null ? null : ownerNames.get(client.getOwnerStaffCode()));
            return row;
        }).collect(Collectors.toList());
    }

    private String displayName(ClientProfile client) {
        if (client == null) {
            return null;
        }
        return StringUtils.hasText(client.getEnterpriseName()) ? client.getEnterpriseName() : client.getContactName();
    }

    private String decrypt(String value) {
        return StringUtils.hasText(value) ? com.loan.infrastructure.security.AesUtils.decrypt(value) : null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean isPhone(String value) {
        return value != null && value.matches("\\d{11}");
    }

    private String normalizeOrderDir(String orderDir) {
        return "asc".equalsIgnoreCase(orderDir) ? "asc" : "desc";
    }
}
