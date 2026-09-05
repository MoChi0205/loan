package com.loan.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.exception.BusinessException;
import com.loan.ocr.entity.OcrRecord;
import com.loan.ocr.mapper.OcrRecordMapper;
import com.loan.approval.entity.MaterialReview;
import com.loan.approval.mapper.MaterialReviewMapper;
import com.loan.report.entity.ClientScreening;
import com.loan.report.mapper.ClientScreeningMapper;
import com.loan.submission.entity.ClientSubmission;
import com.loan.submission.mapper.ClientSubmissionMapper;
import com.loan.submission.service.SubmissionFactsMerger;
import com.loan.submission.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 材料复核服务（#4b 审批门控核心）。
 *
 * <p>业务规则（我司审批通过后才可对客展示）：
 * <ul>
 *   <li>上传材料经 VLM 识别 → 调用 {@link #createPending} 写入 {@code PENDING_REVIEW} 复核单，
 *       同时标记 {@code t_ocr_record.review_status=PENDING_REVIEW / visible_flag=0}；
 *       此阶段事实仅存于 {@code t_material_review.pending_facts_json}，<b>绝不</b>进入客户提交单。</li>
 *   <li>我司审批通过 → {@link #audit} 调用 {@link SubmissionFactsMerger#mergeFromOcr} 仅补空回灌到提交单，
 *       并置 {@code visible_flag=1 / review_status=APPROVED}（客可见）。</li>
 *   <li>审批驳回 → 置 {@code REJECTED}，识别结果作废、永不回灌。</li>
 * </ul>
 *
 * <p>回灌复用既有「仅补空不覆盖」铁律，与用户手填值互不冲突。</p>
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialReviewService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MaterialReviewMapper reviewMapper;
    private final OcrRecordMapper ocrRecordMapper;
    private final ClientScreeningMapper screeningMapper;
    private final SubmissionFactsMerger merger;
    private final SubmissionService submissionService;
    private final ClientSubmissionMapper submissionMapper;

    public static final String STATUS_PENDING = "PENDING_REVIEW";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    /**
     * 创建待复核单（上传识别后调用）。
     *
     * @param ocrRecordId      OCR 记录主键
     * @param bizType          资料类型
     * @param clientProfileCode 客户编码
     * @param reportNo         关联报告编号（可空）
     * @param facts            已映射的规范 facts（VLM 抽取结果）
     * @param operator         上传操作人
     * @return 复核单号
     */
    @Transactional(rollbackFor = Exception.class)
    public String createPending(Long ocrRecordId, String bizType, String clientProfileCode,
                                String reportNo, Map<String, Object> facts, String operator) {
        MaterialReview r = new MaterialReview();
        r.setReviewNo(BizIdGenerator.generate("matrev"));
        r.setOcrRecordId(ocrRecordId);
        r.setBizType(bizType);
        r.setClientProfileCode(clientProfileCode);
        r.setReportNo(reportNo);
        r.setPendingFactsJson(toJson(facts));
        r.setReviewStatus(STATUS_PENDING);
        r.setCreatedBy(StringUtils.hasText(operator) ? operator : "system");
        r.setUpdatedBy(r.getCreatedBy());
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        reviewMapper.insert(r);

        // 标记 OCR 记录为待复核、客不可见
        if (ocrRecordId != null) {
            OcrRecord rec = ocrRecordMapper.selectById(ocrRecordId);
            if (rec != null) {
                rec.setReviewStatus(STATUS_PENDING);
                rec.setVisibleFlag(0);
                rec.setUpdatedAt(LocalDateTime.now());
                ocrRecordMapper.updateById(rec);
            }
        }
        return r.getReviewNo();
    }

    /**
     * 审批（通过 / 驳回）。
     *
     * <p>通过：回灌提交单（无提交单则按 facts 新建）+ 置客可见；
     * 驳回：结果作废。</p>
     *
     * @param reviewNo 复核单号
     * @param approve  是否通过
     * @param opinion  审批意见（驳回必填）
     * @param operator 审批人
     */
    @Transactional(rollbackFor = Exception.class)
    public void audit(String reviewNo, boolean approve, String opinion, String operator) {
        MaterialReview r = byNo(reviewNo);
        if (!STATUS_PENDING.equals(r.getReviewStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅待复核单可审批");
        }
        if (!approve && !StringUtils.hasText(opinion)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "驳回意见必填");
        }
        r.setReviewStatus(approve ? STATUS_APPROVED : STATUS_REJECTED);
        r.setReviewOpinion(opinion);
        r.setReviewerStaffCode(operator);
        r.setReviewTime(LocalDateTime.now());
        r.setUpdatedBy(operator);
        r.setUpdatedAt(LocalDateTime.now());

        if (approve) {
            Map<String, Object> facts = parseFacts(r.getPendingFactsJson());
            String submissionNo = backfillSubmission(r, facts, operator);
            r.setSubmissionNo(submissionNo);
            // 置 OCR 记录客可见
            if (r.getOcrRecordId() != null) {
                OcrRecord rec = ocrRecordMapper.selectById(r.getOcrRecordId());
                if (rec != null) {
                    rec.setVisibleFlag(1);
                    rec.setReviewStatus(STATUS_APPROVED);
                    rec.setUpdatedAt(LocalDateTime.now());
                    ocrRecordMapper.updateById(rec);
                }
            }
        }
        reviewMapper.updateById(r);
    }

    /**
     * 回灌提交单：优先按 reportNo 回灌既有提交单；无则按 facts 新建提交单。
     *
     * @return 提交单号（业务ID）
     */
    private String backfillSubmission(MaterialReview r, Map<String, Object> facts, String operator) {
        if (StringUtils.hasText(r.getReportNo())) {
            Map<String, Object> merge = merger.mergeFromOcr(r.getReportNo(), facts, operator);
            if (Boolean.TRUE.equals(merge.get("applied"))) {
                return resolveSubmissionNoByReport(r.getReportNo());
            }
        }
        // 无既有提交单：按 facts 新建（仅当存在客户编码）
        if (StringUtils.hasText(r.getClientProfileCode()) && facts != null && !facts.isEmpty()) {
            ClientSubmission submission = submissionService.submit(
                    r.getClientProfileCode(), null, facts, null, operator);
            return submission == null ? null : submission.getSubmissionNo();
        }
        return null;
    }

    /** 按报告号反查最新提交单号（回灌目标）。 */
    private String resolveSubmissionNoByReport(String reportNo) {
        com.loan.report.entity.ClientScreening screening = screeningMapper.selectOne(
                new LambdaQueryWrapper<com.loan.report.entity.ClientScreening>()
                        .eq(com.loan.report.entity.ClientScreening::getReportNo, reportNo));
        if (screening == null || !StringUtils.hasText(screening.getMatchTraceUuid())) {
            return null;
        }
        ClientSubmission submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<ClientSubmission>()
                        .eq(ClientSubmission::getMatchTraceNo, screening.getMatchTraceUuid())
                        .orderByDesc(ClientSubmission::getCreatedAt).last("limit 1"));
        return submission == null ? null : submission.getSubmissionNo();
    }

    /** 待复核分页。 */
    public List<MaterialReview> pendingPage(String status, String keyword, int page, int size) {
        LambdaQueryWrapper<MaterialReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MaterialReview::getReviewStatus, StringUtils.hasText(status) ? status : STATUS_PENDING);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.eq(MaterialReview::getReviewNo, keyword)
                    .or().eq(MaterialReview::getClientProfileCode, keyword)
                    .or().eq(MaterialReview::getReportNo, keyword));
        }
        wrapper.orderByDesc(MaterialReview::getCreatedAt);
        long offset = (long) (page <= 0 ? 0 : page - 1) * (size <= 0 ? 10 : Math.min(size, 100));
        wrapper.last("limit " + offset + ", " + (size <= 0 ? 10 : Math.min(size, 100)));
        return reviewMapper.selectList(wrapper);
    }

    /** 待复核计数。 */
    public long pendingCount() {
        return reviewMapper.selectCount(new LambdaQueryWrapper<MaterialReview>()
                .eq(MaterialReview::getReviewStatus, STATUS_PENDING));
    }

    /** 详情。 */
    public MaterialReview detail(String reviewNo) {
        return byNo(reviewNo);
    }

    private MaterialReview byNo(String reviewNo) {
        MaterialReview r = reviewMapper.selectOne(new LambdaQueryWrapper<MaterialReview>()
                .eq(MaterialReview::getReviewNo, reviewNo));
        if (r == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "材料复核单不存在");
        }
        return r;
    }

    private Map<String, Object> parseFacts(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Map<String, Object> facts) {
        try {
            return MAPPER.writeValueAsString(facts == null ? new LinkedHashMap<>() : facts);
        } catch (Exception e) {
            return "{}";
        }
    }
}
