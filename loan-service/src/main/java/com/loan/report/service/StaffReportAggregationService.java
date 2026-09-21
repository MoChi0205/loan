package com.loan.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.approval.entity.MaterialReview;
import com.loan.approval.mapper.MaterialReviewMapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.context.LoanUser;
import com.loan.mini.service.MiniMatchService;
import com.loan.personal.entity.PersonalProfile;
import com.loan.personal.mapper.PersonalProfileMapper;
import com.loan.report.dto.StaffAggregatedReport;
import com.loan.report.dto.StaffClientProfile;
import com.loan.report.dto.StaffMatchedProduct;
import com.loan.report.dto.StaffMaterialSummary;
import com.loan.report.dto.StaffReportDetail;
import com.loan.report.entity.ClientScreening;
import com.loan.report.mapper.ClientScreeningMapper;
import com.loan.submission.entity.ClientSubmission;
import com.loan.submission.mapper.ClientSubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 员工内部报告聚合服务。所有附加查询必须发生在 P0 权限校验之后。 */
@Service
@RequiredArgsConstructor
public class StaffReportAggregationService {

    private static final String DATA_SOURCE_NOTICE =
            "本报告仅使用客户主动填写及授权上传材料；未调用外部个人信息查询接口";

    private final ReportService reportService;
    private final MiniMatchService miniMatchService;
    private final ClientScreeningMapper screeningMapper;
    private final ClientProfileMapper clientProfileMapper;
    private final PersonalProfileMapper personalProfileMapper;
    private final ClientSubmissionMapper submissionMapper;
    private final MaterialReviewMapper materialReviewMapper;
    private final ObjectMapper objectMapper;

    public StaffAggregatedReport aggregate(String reportNo, LoanUser user) {
        // 必须置于第一步：复用 P0 的 STAFF 身份、角色和客户归属范围校验。
        StaffReportDetail reportSummary = reportService.staffScreeningDetail(reportNo, user);

        ClientScreening screening = screeningMapper.selectOne(new LambdaQueryWrapper<ClientScreening>()
                .eq(ClientScreening::getReportNo, reportNo).last("limit 1"));
        ClientProfile profile = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, reportSummary.getClientProfileCode()).last("limit 1"));
        ClientSubmission submission = latestSubmission(screening == null ? null : screening.getMatchTraceUuid());
        List<MaterialReview> reviews = materialReviewMapper.selectList(new LambdaQueryWrapper<MaterialReview>()
                .eq(MaterialReview::getReportNo, reportNo)
                .orderByDesc(MaterialReview::getCreatedAt));

        StaffAggregatedReport result = new StaffAggregatedReport();
        result.setReportNo(reportNo);
        result.setReportSummary(reportSummary);
        PersonalProfile personal = profile == null ? null : personalProfileMapper.selectOne(
                new LambdaQueryWrapper<PersonalProfile>()
                        .eq(PersonalProfile::getClientProfileCode, profile.getClientCode()).last("limit 1"));
        result.setClientProfile(toProfile(profile, personal));
        result.setMaterialStatus(toMaterialSummary(submission, reviews));
        result.setBusinessAnalysis(miniMatchService.reportDiagnosis(reportNo, null));
        result.setMatchedProducts(toProducts(miniMatchService.reportProducts(reportNo)));
        result.setDataSourceNotice(DATA_SOURCE_NOTICE);
        result.setAggregatedAt(LocalDateTime.now());
        return result;
    }

    private ClientSubmission latestSubmission(String matchTraceUuid) {
        if (!StringUtils.hasText(matchTraceUuid)) {
            return null;
        }
        List<ClientSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<ClientSubmission>()
                        .eq(ClientSubmission::getMatchTraceNo, matchTraceUuid)
                        .orderByDesc(ClientSubmission::getCreatedAt).last("limit 1"));
        return submissions.isEmpty() ? null : submissions.get(0);
    }

    private StaffClientProfile toProfile(ClientProfile source, PersonalProfile personal) {
        if (source == null) {
            return null;
        }
        StaffClientProfile target = new StaffClientProfile();
        target.setClientCode(source.getClientCode());
        target.setCustomerGroup(source.getCustomerGroup());
        target.setContactName(source.getContactName());
        target.setContactPhoneMasked(maskPhone(source.getPhone()));
        target.setEnterpriseName(source.getEnterpriseName());
        target.setCreditCodeMasked(maskCreditCode(source.getCreditCode()));
        if ("ENTERPRISE".equals(source.getCustomerGroup())) {
            target.setIdentityType("统一社会信用代码");
            target.setIdentityMasked(maskCreditCode(source.getCreditCode()));
        } else if (personal != null) {
            target.setIdentityType("身份证号");
            target.setIdentityMasked(maskIdCard(personal.getIdCardNo()));
            if (!StringUtils.hasText(target.getContactName())) target.setContactName(personal.getRealName());
        }
        target.setOwnerStaffCode(source.getOwnerStaffCode());
        target.setSource(source.getSource());
        target.setStatus(source.getStatus());
        target.setVipLevel(source.getVipLevel());
        target.setCreatedAt(source.getCreatedAt());
        return target;
    }

    private StaffMaterialSummary toMaterialSummary(ClientSubmission submission, List<MaterialReview> reviews) {
        StaffMaterialSummary result = new StaffMaterialSummary();
        if (submission != null) {
            result.setSubmissionNo(submission.getSubmissionNo());
            result.setSubmissionStatus(submission.getStatus());
            result.setSubmittedAt(submission.getCreatedAt());
            result.setMaterialVersion(resolveMaterialVersion(submission.getDataJson()));
        } else {
            result.setMaterialVersion("v1");
        }
        int pending = 0;
        int approved = 0;
        int rejected = 0;
        LocalDateTime latest = null;
        for (MaterialReview review : reviews) {
            if ("PENDING_REVIEW".equals(review.getReviewStatus())) pending++;
            if ("APPROVED".equals(review.getReviewStatus())) approved++;
            if ("REJECTED".equals(review.getReviewStatus())) rejected++;
            LocalDateTime time = review.getReviewTime() == null ? review.getCreatedAt() : review.getReviewTime();
            if (time != null && (latest == null || time.isAfter(latest))) latest = time;
        }
        result.setReviewTotal(reviews.size());
        result.setPendingReviewCount(pending);
        result.setApprovedCount(approved);
        result.setRejectedCount(rejected);
        result.setLatestReviewAt(latest);
        return result;
    }

    private String resolveMaterialVersion(String dataJson) {
        if (!StringUtils.hasText(dataJson)) return "v1";
        try {
            Map<String, Object> facts = objectMapper.readValue(dataJson, new TypeReference<Map<String, Object>>() { });
            Object meta = facts.get("_ocrMeta");
            if (meta instanceof Map) {
                Object version = ((Map<?, ?>) meta).get("version");
                if (version instanceof Number) return "v" + ((Number) version).intValue();
            }
        } catch (Exception ignored) {
            // 历史脏数据按初始版本展示，不中断内部报告。
        }
        return "v1";
    }

    private List<StaffMatchedProduct> toProducts(List<Map<String, Object>> rows) {
        List<StaffMatchedProduct> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            StaffMatchedProduct product = objectMapper.convertValue(new LinkedHashMap<>(row), StaffMatchedProduct.class);
            result.add(product);
        }
        return result;
    }

    private String maskPhone(String value) {
        if (!StringUtils.hasText(value) || value.length() < 7) return value;
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private String maskCreditCode(String value) {
        if (!StringUtils.hasText(value) || value.length() < 8) return value;
        return value.substring(0, 4) + "**********" + value.substring(value.length() - 4);
    }

    private String maskIdCard(String value) {
        if (!StringUtils.hasText(value) || value.length() < 10) return value;
        return value.substring(0, 6) + "********" + value.substring(value.length() - 4);
    }
}
