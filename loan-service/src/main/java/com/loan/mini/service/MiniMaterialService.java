package com.loan.mini.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.approval.service.MaterialReviewService;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.context.LoanUser;
import com.loan.ocr.model.OcrResult;
import com.loan.ocr.service.OcrService;
import com.loan.submission.service.SubmissionFactsMerger;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小程序端材料回灌服务（#4b OCR 回灌诊断链路 L1+L2+L3 串联）。
 *
 * <p>{@code ingest(fileKey, bizType, clientCode, reportNo, user)}：
 * <ol>
 *   <li>调 {@link OcrService#recognize} 识别 → facts；</li>
 *   <li>若 facts 非空：写入「材料复核」待审单（{@link MaterialReviewService#createPending}），
 *       事实先进入 {@code t_material_review.pending_facts_json}，<b>不立即回灌</b>客户数据；</li>
 *   <li>我司审批通过后（{@link MaterialReviewService#audit}）才按「仅补空不覆盖」回灌提交单并置客可见。</li>
 * </ol>
 *
 * <p>配置：{@code loan.ocr.enabled=false} 时直接跳过，仅保留落盘附件（向后兼容）。</p>
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class MiniMaterialService {

    private final OcrService ocrService;
    private final SubmissionFactsMerger merger;
    private final ClientProfileMapper clientProfileMapper;
    private final MaterialReviewService materialReviewService;

    @Value("${loan.ocr.enabled:true}")
    private boolean ocrEnabled;

    /**
     * 材料回灌入口。
     *
     * @param fileKey    文件 key（落盘后的本地文件名前缀）
     * @param bizType    资料类型（ID_CARD / BUSINESS_LICENSE / FINANCIAL_STATEMENT / CONTRACT / DUE_DILIGENCE / OTHER）
     * @param clientCode 客户编码（用于解析客群，可不传）
     * @param reportNo   报告编号（诊断补充材料场景，可不传）
     * @param user       当前登录用户（留痕用）
     * @return {ocrApplied, pendingReview, reviewNo, extractedFields, mergedCount, ocrRecordId}
     */
    public Map<String, Object> ingest(String fileKey, String bizType, String clientCode,
                                      String reportNo, LoanUser user) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ocrApplied", false);
        result.put("pendingReview", false);
        result.put("reviewNo", null);
        result.put("extractedFields", new ArrayList<Map<String, Object>>());
        result.put("mergedCount", 0);
        result.put("ocrRecordId", null);

        if (!ocrEnabled) {
            return result;
        }

        String customerGroup = resolveCustomerGroup(clientCode);
        String operator = user == null ? null : user.getName();

        OcrResult ocr = ocrService.recognize(fileKey, bizType, customerGroup);
        result.put("ocrRecordId", ocr.getOcrRecordId());
        result.put("extractedFields", ocr.getExtractedFields() == null
                ? new ArrayList<Map<String, Object>>() : ocr.getExtractedFields());

        // facts 非空 → 进入审批门控（先存待复核，不回灌客数据）
        if (ocr.getFacts() != null && !ocr.getFacts().isEmpty()) {
            String reviewNo = materialReviewService.createPending(
                    ocr.getOcrRecordId(), bizType, clientCode, reportNo, ocr.getFacts(), operator);
            result.put("pendingReview", true);
            result.put("reviewNo", reviewNo);
            return result;
        }

        // facts 为空（如 mock / VLM 未配置）：保持向后兼容的即时回灌行为
        boolean applied = false;
        int mergedCount = 0;
        if (StringUtils.hasText(reportNo) && ocr.getFacts() != null && !ocr.getFacts().isEmpty()) {
            Map<String, Object> merge = merger.mergeFromOcr(reportNo, ocr.getFacts(), operator);
            applied = Boolean.TRUE.equals(merge.get("applied"));
            Object mc = merge.get("mergedCount");
            mergedCount = mc instanceof Number ? ((Number) mc).intValue() : 0;
        }
        result.put("ocrApplied", applied);
        result.put("mergedCount", mergedCount);
        return result;
    }

    /** 解析客群（按 clientCode → t_client_profile.customer_group，缺失兜底 ENTERPRISE） */
    private String resolveCustomerGroup(String clientCode) {
        if (StringUtils.hasText(clientCode)) {
            ClientProfile profile = clientProfileMapper.selectOne(
                    new LambdaQueryWrapper<ClientProfile>()
                            .eq(ClientProfile::getClientCode, clientCode).last("limit 1"));
            if (profile != null && StringUtils.hasText(profile.getCustomerGroup())) {
                return profile.getCustomerGroup();
            }
        }
        return "ENTERPRISE";
    }
}
