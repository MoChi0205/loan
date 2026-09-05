package com.loan.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.approval.entity.AttachmentDownloadApproval;
import com.loan.approval.entity.MaterialReview;
import com.loan.approval.entity.ProductApproval;
import com.loan.approval.mapper.AttachmentDownloadApprovalMapper;
import com.loan.approval.mapper.MaterialReviewMapper;
import com.loan.approval.mapper.ProductApprovalMapper;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.common.util.PageOrder;
import com.loan.common.service.BusinessNameService;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.mini.service.MiniClientService;
import com.loan.product.entity.BankProduct;
import com.loan.product.mapper.BankProductMapper;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批服务：产品审核工单 + 附件下载审批单。
 *
 * <p>产品审核：通过入全量库（t_bank_product.status → APPROVED），驳回置 REJECTED；
 * 附件下载审批：通过生成 24h 限时链接 token（超时作废需重新申请），下载动作全量留痕。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    /** 允许排序字段（白名单，防注入） */
    private static final java.util.Map<String, com.baomidou.mybatisplus.core.toolkit.support.SFunction<ProductApproval, ?>> ORDER_FIELDS =
            new java.util.HashMap<>();
    static {
        ORDER_FIELDS.put("createdAt", ProductApproval::getCreatedAt);
        ORDER_FIELDS.put("updatedAt", ProductApproval::getUpdatedAt);
        ORDER_FIELDS.put("approvedAt", ProductApproval::getApprovedAt);
    }

    /** 下载审批允许排序字段（白名单） */
    private static final java.util.Map<String, com.baomidou.mybatisplus.core.toolkit.support.SFunction<com.loan.approval.entity.AttachmentDownloadApproval, ?>> DOWNLOAD_ORDER_FIELDS =
            new java.util.HashMap<>();
    static {
        DOWNLOAD_ORDER_FIELDS.put("createdAt", com.loan.approval.entity.AttachmentDownloadApproval::getCreatedAt);
        DOWNLOAD_ORDER_FIELDS.put("updatedAt", com.loan.approval.entity.AttachmentDownloadApproval::getUpdatedAt);
        DOWNLOAD_ORDER_FIELDS.put("approvedAt", com.loan.approval.entity.AttachmentDownloadApproval::getApprovedAt);
    }

    /** 审批类型：产品审核工单。 */
    public static final String TYPE_PRODUCT = "PRODUCT";
    /** 审批类型：附件无水印下载审批。 */
    public static final String TYPE_DOWNLOAD = "DOWNLOAD";
    /** 审批类型：无归宿客户分配审批。 */
    public static final String TYPE_ALLOCATION = "ALLOCATION";
    /** 审批类型：材料复核（上传材料经大模型识别后，我司审批通过才回灌客数据）。 */
    public static final String TYPE_MATERIAL_REVIEW = "MATERIAL_REVIEW";
    /** 统一审批「全部类型」入参值。 */
    public static final String TYPE_ALL = "ALL";

    /** 待审状态值（与 t_product_approval / t_attachment_download_approval.approve_status 一致）。 */
    private static final String STATUS_PENDING = "PENDING";

    /**
     * 分页语义提示：{@code SEGMENTED} 表示「分段分页」——
     * 各审批类型各取第 page 页后合并，而非全局连续分页，前端不可按 total/size 推算页数。
     */
    private static final String PAGINATION_HINT_SEGMENTED = "SEGMENTED";

    /** 统一审批支持的全部类型（顺序对外稳定：产品 → 下载 → 分配 → 材料复核）。 */
    private static final List<String> ALL_TYPES =
            Collections.unmodifiableList(Arrays.asList(TYPE_PRODUCT, TYPE_DOWNLOAD, TYPE_ALLOCATION, TYPE_MATERIAL_REVIEW));

    private final ProductApprovalMapper productApprovalMapper;
    private final AttachmentDownloadApprovalMapper downloadApprovalMapper;
    private final MaterialReviewMapper materialReviewMapper;
    private final MaterialReviewService materialReviewService;
    private final BankProductMapper bankProductMapper;
    private final MiniClientService miniClientService;
    private final StaffMapper staffMapper;
    private final BusinessNameService businessNameService;

    /**
     * 已开放的审批类型白名单（配置 {@code loan.mini.approval.types}，逗号分隔）。
     *
     * <p>为空时采用安全默认，仅开放 {@code ALLOCATION}；非空时仅白名单内类型会出现在统一待审列表与待审计数中。
     * 该字段为非 final，由 {@link Value} 字段注入，不参与 {@code @RequiredArgsConstructor}。</p>
     */
    @Value("${loan.mini.approval.types:}")
    private List<String> enabledTypes = Collections.emptyList();

    // ============================================================
    // 一、产品审核
    // ============================================================

    /**
     * 产品审核工单分页。
     *
     * @param status  审核状态（可选：PENDING/APPROVED/REJECTED）
     * @param keyword 工单号 / 产品编码（可选）
     * @param page    页码
     * @param size    每页大小
     * @return 审核工单分页
     */
    public PageResult<Map<String, Object>> productPage(String status, String keyword, int page, int size, String orderBy, String orderDir) {
        LambdaQueryWrapper<ProductApproval> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProductApproval::getApproveStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ProductApproval::getApprovalNo, kw)
                    .or().like(ProductApproval::getBankProductCode, kw));
        }
        PageOrder.apply(wrapper, orderBy, orderDir, ORDER_FIELDS, ProductApproval::getApproveStatus);
        Page<ProductApproval> result = productApprovalMapper.selectPage(new Page<>(page, size), wrapper);

        List<String> productCodes = result.getRecords().stream().map(ProductApproval::getBankProductCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        Map<String, String> productNameMap = businessNameService.productNames(productCodes);
        Map<String, String> staffNameMap = businessNameService.staffNames(result.getRecords().stream()
                .map(ProductApproval::getApproverStaffCode).collect(Collectors.toSet()));

        List<Map<String, Object>> records = result.getRecords().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("approvalNo", a.getApprovalNo());
            m.put("bankProductCode", a.getBankProductCode());
            m.put("bankProductName", productNameMap.get(a.getBankProductCode()));
            m.put("applyType", a.getApplyType());
            m.put("duplicateFlag", a.getDuplicateFlag());
            m.put("approveStatus", a.getApproveStatus());
            m.put("approverStaffCode", a.getApproverStaffCode());
            m.put("approverStaffName", staffNameMap.get(a.getApproverStaffCode()));
            m.put("approveOpinion", a.getApproveOpinion());
            m.put("timeoutAt", a.getTimeoutAt());
            m.put("approvedAt", a.getApprovedAt());
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 产品审核详情（含变更快照）。
     *
     * @param approvalNo 审核工单号
     * @return 详情
     */
    public Map<String, Object> productDetail(String approvalNo) {
        ProductApproval a = productByNo(approvalNo);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("approvalNo", a.getApprovalNo());
        m.put("bankProductCode", a.getBankProductCode());
        m.put("applyType", a.getApplyType());
        m.put("beforeSnapshotJson", a.getBeforeSnapshotJson());
        m.put("afterSnapshotJson", a.getAfterSnapshotJson());
        m.put("duplicateFlag", a.getDuplicateFlag());
        m.put("approveStatus", a.getApproveStatus());
        m.put("approverStaffCode", a.getApproverStaffCode());
        m.put("approverStaffName", businessNameService.staffNames(Collections.singleton(a.getApproverStaffCode()))
                .get(a.getApproverStaffCode()));
        m.put("approveOpinion", a.getApproveOpinion());
        m.put("timeoutAt", a.getTimeoutAt());
        m.put("approvedAt", a.getApprovedAt());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    /**
     * 产品审核（通过 / 驳回），通过联动产品状态入全量库。
     *
     * @param approvalNo 审核工单号
     * @param approve    是否通过
     * @param opinion    意见（驳回必填）
     * @param operator   审核人
     */
    @Transactional(rollbackFor = Exception.class)
    public void productAudit(String approvalNo, boolean approve, String opinion, String operator) {
        ProductApproval a = productByNo(approvalNo);
        if (!"PENDING".equals(a.getApproveStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅待审核工单可审核");
        }
        if (!approve && !StringUtils.hasText(opinion)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "驳回意见必填");
        }
        a.setApproveStatus(approve ? "APPROVED" : "REJECTED");
        a.setApproveOpinion(opinion);
        a.setApproverStaffCode(operator);
        a.setApprovedAt(LocalDateTime.now());
        a.setUpdatedBy(operator);
        productApprovalMapper.updateById(a);
        // 联动产品状态：通过入全量库 APPROVED，驳回置 REJECTED
        BankProduct product = bankProductMapper.selectOne(new LambdaQueryWrapper<BankProduct>()
                .eq(BankProduct::getProductCode, a.getBankProductCode()));
        if (product != null && "DRAFT".equals(product.getStatus())) {
            product.setStatus(approve ? "APPROVED" : "REJECTED");
            product.setUpdatedBy(operator);
            bankProductMapper.updateById(product);
        }
    }

    /**
     * 按审核工单号查询。
     */
    private ProductApproval productByNo(String approvalNo) {
        ProductApproval a = productApprovalMapper.selectOne(new LambdaQueryWrapper<ProductApproval>()
                .eq(ProductApproval::getApprovalNo, approvalNo));
        if (a == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "审核工单不存在");
        }
        return a;
    }

    // ============================================================
    // 二、附件下载审批
    // ============================================================

    /**
     * 提交无水印下载申请。
     *
     * @param attachmentIds 资料清单（JSON 数组附件 ID）
     * @param purpose       用途说明（必填）
     * @param expectDays    期望使用期限
     * @param applicantCode 申请人工号
     * @param applicantName 申请人姓名
     * @return 申请单号
     */
    @Transactional(rollbackFor = Exception.class)
    public String applyDownload(String attachmentIds, String purpose, Integer expectDays,
                                String applicantCode, String applicantName) {
        if (!StringUtils.hasText(attachmentIds) || !StringUtils.hasText(purpose)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "资料清单与用途说明必填");
        }
        AttachmentDownloadApproval a = new AttachmentDownloadApproval();
        a.setApprovalNo(BizIdGenerator.generate("dldapr"));
        a.setApplicantStaffCode(applicantCode);
        a.setAttachmentIds(attachmentIds);
        a.setPurpose(purpose);
        a.setExpectDays(expectDays);
        a.setApproveStatus("PENDING");
        a.setVoidFlag(0);
        a.setCreatedBy(applicantName);
        a.setUpdatedBy(applicantName);
        downloadApprovalMapper.insert(a);
        return a.getApprovalNo();
    }

    /**
     * 下载审批分页。
     *
     * @param status  审批状态（可选：PENDING/APPROVED/REJECTED）
     * @param keyword 申请单号 / 申请人工号（可选）
     * @param page    页码
     * @param size    每页大小
     * @return 审批单分页
     */
    public PageResult<Map<String, Object>> downloadPage(String status, String keyword, int page, int size, String orderBy, String orderDir) {
        LambdaQueryWrapper<AttachmentDownloadApproval> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(AttachmentDownloadApproval::getApproveStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            List<String> applicantCodes = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                            .like(Staff::getStaffName, kw))
                    .stream().map(Staff::getStaffCode).filter(StringUtils::hasText)
                    .collect(Collectors.toList());
            wrapper.and(w -> w.like(AttachmentDownloadApproval::getApprovalNo, kw)
                    .or().like(AttachmentDownloadApproval::getApplicantStaffCode, kw)
                    .func(x -> {
                        if (!applicantCodes.isEmpty()) {
                            x.or().in(AttachmentDownloadApproval::getApplicantStaffCode, applicantCodes);
                        }
                    }));
        }
        PageOrder.apply(wrapper, orderBy, orderDir, DOWNLOAD_ORDER_FIELDS, AttachmentDownloadApproval::getApproveStatus);
        Page<AttachmentDownloadApproval> result = downloadApprovalMapper.selectPage(new Page<>(page, size), wrapper);
        Set<String> staffCodes = result.getRecords().stream()
                .flatMap(a -> java.util.stream.Stream.of(a.getApplicantStaffCode(), a.getApproverStaffCode()))
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, String> staffNameMap = businessNameService.staffNames(staffCodes);
        List<Map<String, Object>> records = result.getRecords().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("approvalNo", a.getApprovalNo());
            m.put("applicantStaffCode", a.getApplicantStaffCode());
            m.put("applicantStaffName", staffNameMap.get(a.getApplicantStaffCode()));
            m.put("attachmentIds", a.getAttachmentIds());
            m.put("purpose", a.getPurpose());
            m.put("expectDays", a.getExpectDays());
            m.put("approveStatus", a.getApproveStatus());
            m.put("approverStaffCode", a.getApproverStaffCode());
            m.put("approverStaffName", staffNameMap.get(a.getApproverStaffCode()));
            m.put("approveOpinion", a.getApproveOpinion());
            m.put("linkToken", a.getLinkToken());
            m.put("linkExpireAt", a.getLinkExpireAt());
            m.put("voidFlag", a.getVoidFlag());
            m.put("approvedAt", a.getApprovedAt());
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 下载审批（通过生成 24h 限时链接 / 驳回）。
     *
     * @param approvalNo 申请单号
     * @param approve    是否通过
     * @param opinion    意见（驳回必填）
     * @param operator   审批人
     */
    @Transactional(rollbackFor = Exception.class)
    public void downloadAudit(String approvalNo, boolean approve, String opinion, String operator) {
        AttachmentDownloadApproval a = downloadByNo(approvalNo);
        if (!"PENDING".equals(a.getApproveStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅待审批单可审批");
        }
        if (!approve && !StringUtils.hasText(opinion)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "驳回意见必填");
        }
        a.setApproveStatus(approve ? "APPROVED" : "REJECTED");
        a.setApproveOpinion(opinion);
        a.setApproverStaffCode(operator);
        a.setApprovedAt(LocalDateTime.now());
        if (approve) {
            a.setLinkToken(genToken());
            a.setLinkExpireAt(LocalDateTime.now().plusHours(24));
        }
        a.setUpdatedBy(operator);
        downloadApprovalMapper.updateById(a);
    }

    /**
     * 作废下载审批单（超时 / 手动）。
     *
     * @param approvalNo 申请单号
     * @param operator   操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void voidDownload(String approvalNo, String operator) {
        AttachmentDownloadApproval a = downloadByNo(approvalNo);
        if (a.getVoidFlag() != null && a.getVoidFlag() == 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审批单已作废");
        }
        if (!"APPROVED".equals(a.getApproveStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅已通过且有效的审批单可作废");
        }
        a.setVoidFlag(1);
        a.setUpdatedBy(operator);
        downloadApprovalMapper.updateById(a);
    }

    /**
     * 按申请单号查询。
     */
    private AttachmentDownloadApproval downloadByNo(String approvalNo) {
        AttachmentDownloadApproval a = downloadApprovalMapper.selectOne(
                new LambdaQueryWrapper<AttachmentDownloadApproval>()
                        .eq(AttachmentDownloadApproval::getApprovalNo, approvalNo));
        if (a == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "审批单不存在");
        }
        return a;
    }

    /** 生成限时下载 token。 */
    private String genToken() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    // ============================================================
    // 三、审批中心统一入口（分配 / 产品 / 下载）
    // ============================================================

    /**
     * 无归宿客户分配待审分页（委派 {@link MiniClientService#pendingAllocations}）。
     *
     * <p>不重复实现查询逻辑，仅作为审批中心的统一取数出口，保证与小程序端口径一致。</p>
     *
     * @param page 页码（≤0 归一为 1）
     * @param size 每页大小（≤0 归一为 10，上限 100）
     * @return 分配待审分页
     */
    public PageResult<Map<String, Object>> allocationPage(int page, int size) {
        return miniClientService.pendingAllocations(normalizePage(page), normalizeSize(size));
    }

    /**
     * 统一待审列表（跨类型合并）。
     *
     * <p><b>返回类型说明（偏离设计）：</b>返回 {@code Map} 而非 {@link PageResult}，因为需要额外携带
     * {@code paginationHint} 字段说明分段分页语义，而 {@link PageResult} 位于 loan-api 模块、
     * 是 Dubbo 跨系统序列化契约，不宜为单一场景新增字段。Map 结构与 PageResult 字段同名对齐
     * （page/size/total/records），前端可复用同一套解析。</p>
     *
     * <p><b>白名单：</b>仅 {@link #typeEnabled(String)} 为真的类型参与合并，
     * 因此当 {@code loan.mini.approval.types=ALLOCATION} 时，PRODUCT / DOWNLOAD 不会出现在结果中。</p>
     *
     * @param type 审批类型（{@code ALL} / {@code PRODUCT} / {@code DOWNLOAD} / {@code ALLOCATION}，空视为 ALL）
     * @param page 页码
     * @param size 每页大小
     * @return { page, size, total, records（每条含 type 字段）, paginationHint }
     */
    public Map<String, Object> unifiedPending(String type, int page, int size) {
        int p = normalizePage(page);
        int s = normalizeSize(size);
        List<Map<String, Object>> merged = new ArrayList<>();
        long total = 0L;
        for (String t : requestedTypes(type)) {
            if (!typeEnabled(t)) {
                continue;
            }
            PageResult<Map<String, Object>> slice = pendingSlice(t, p, s);
            List<Map<String, Object>> rows = slice.getRecords() == null
                    ? Collections.<Map<String, Object>>emptyList() : slice.getRecords();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>(row);
                item.put("type", t);
                merged.add(item);
            }
            total += slice.getTotal();
        }

        // §5.6 跨类型时间归并：各表取前 size 条后，按 createdAt 倒序（新→旧）内存排序。
        // 某条 createdAt 缺失时落到段序兜底位置（保持其在原类型段内的相对顺序），不排到最前。
        // 单类型分支（type 非 ALL）各自已有序，排序后顺序不变，等效无操作。
        merged.sort(new CreatedAtDescComparator());

        // 归并后截 size（§5.6「内存归并后截 size」）；total 仍为三类型 PENDING 计数之和，不截断。
        if (merged.size() > s) {
            merged = merged.subList(0, s);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", (long) p);
        result.put("size", (long) s);
        result.put("total", total);
        result.put("records", merged);
        result.put("paginationHint", PAGINATION_HINT_SEGMENTED);
        return result;
    }

    /**
     * 各类型待审计数（审批中心红点/角标）。
     *
     * <p>白名单外的类型固定返回 0，与 {@link #unifiedPending} 口径保持一致；
     * 额外返回 {@code TOTAL} 汇总，便于前端一次取数。</p>
     *
     * @return { PRODUCT, DOWNLOAD, ALLOCATION, TOTAL }
     */
    public Map<String, Object> pendingCounts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        long total = 0L;
        for (String t : ALL_TYPES) {
            long c = typeEnabled(t) ? pendingSlice(t, 1, 1).getTotal() : 0L;
            counts.put(t, c);
            total += c;
        }
        counts.put("TOTAL", total);
        return counts;
    }

    /**
     * 材料复核待审分页（审批中心统一取数出口）。
     *
     * @param status  复核状态（可选：PENDING_REVIEW / APPROVED / REJECTED）
     * @param keyword 复核单号 / 客户编码 / 报告编号（可选）
     * @param page    页码
     * @param size    每页大小
     * @return 材料复核待审分页
     */
    public PageResult<Map<String, Object>> materialReviewPage(String status, String keyword, int page, int size) {
        int p = normalizePage(page);
        int s = normalizeSize(size);
        List<MaterialReview> rows = materialReviewService.pendingPage(
                StringUtils.hasText(status) ? status : MaterialReviewService.STATUS_PENDING, keyword, p, s);
        long total = materialReviewService.pendingCount();
        List<Map<String, Object>> records = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reviewNo", r.getReviewNo());
            m.put("ocrRecordId", r.getOcrRecordId());
            m.put("bizType", r.getBizType());
            m.put("clientProfileCode", r.getClientProfileCode());
            m.put("reportNo", r.getReportNo());
            m.put("pendingFactsJson", r.getPendingFactsJson());
            m.put("reviewStatus", r.getReviewStatus());
            m.put("reviewerStaffCode", r.getReviewerStaffCode());
            m.put("reviewOpinion", r.getReviewOpinion());
            m.put("reviewTime", r.getReviewTime());
            m.put("submissionNo", r.getSubmissionNo());
            m.put("createdBy", r.getCreatedBy());
            m.put("createdAt", r.getCreatedAt());
            return m;
        }).collect(java.util.stream.Collectors.toList());
        return PageResult.build(p, s, total, records);
    }

    /**
     * 材料复核详情。
     *
     * @param reviewNo 复核单号
     * @return 详情
     */
    public Map<String, Object> materialReviewDetail(String reviewNo) {
        MaterialReview r = materialReviewService.detail(reviewNo);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reviewNo", r.getReviewNo());
        m.put("ocrRecordId", r.getOcrRecordId());
        m.put("bizType", r.getBizType());
        m.put("clientProfileCode", r.getClientProfileCode());
        m.put("reportNo", r.getReportNo());
        m.put("pendingFactsJson", r.getPendingFactsJson());
        m.put("reviewStatus", r.getReviewStatus());
        m.put("reviewerStaffCode", r.getReviewerStaffCode());
        m.put("reviewOpinion", r.getReviewOpinion());
        m.put("reviewTime", r.getReviewTime());
        m.put("submissionNo", r.getSubmissionNo());
        m.put("createdBy", r.getCreatedBy());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    /**
     * 材料复核审批（通过 → 回灌客数据并置可见；驳回 → 作废）。
     *
     * @param reviewNo 复核单号
     * @param approve  是否通过
     * @param opinion  审批意见（驳回必填）
     * @param operator 审批人
     */
    @Transactional(rollbackFor = Exception.class)
    public void materialReviewAudit(String reviewNo, boolean approve, String opinion, String operator) {
        materialReviewService.audit(reviewNo, approve, opinion, operator);
    }

    /**
     * 统一审批动作（按类型路由到既有审批实现，不重复实现业务规则）。
     *
     * <p>事务由各下游方法自身的 {@code @Transactional} 保证，本方法只做路由，
     * 单次调用只落一个类型的审批，不存在跨类型事务边界问题。</p>
     *
     * @param type       审批类型（PRODUCT / DOWNLOAD / ALLOCATION）
     * @param approvalNo 审批单号
     * @param approve    true 通过，false 驳回
     * @param opinion    审批意见（驳回必填，由下游校验）
     * @param user       当前审批人（权限已由 Controller 层校验）
     */
    public void unifiedAudit(String type, String approvalNo, boolean approve, String opinion, LoanUser user) {
        if (!StringUtils.hasText(approvalNo)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审批单号不能为空");
        }
        String t = requireSupportedType(type);
        String operator = user == null || !StringUtils.hasText(user.getUserNo()) ? "system" : user.getUserNo();
        if (TYPE_ALLOCATION.equals(t)) {
            if (approve) {
                miniClientService.approveAllocation(approvalNo, user);
            } else {
                miniClientService.rejectAllocation(approvalNo, opinion, user);
            }
            return;
        }
        if (TYPE_PRODUCT.equals(t)) {
            productAudit(approvalNo, approve, opinion, operator);
            return;
        }
        if (TYPE_MATERIAL_REVIEW.equals(t)) {
            materialReviewAudit(approvalNo, approve, opinion, operator);
            return;
        }
        downloadAudit(approvalNo, approve, opinion, operator);
    }

    /**
     * 判断审批类型是否在配置白名单内。
     *
     * @param type 审批类型
     * @return 命中白名单（空配置时仅 ALLOCATION）则返回 true
     */
    public boolean typeEnabled(String type) {
        if (enabledTypes == null || enabledTypes.isEmpty()) {
            // 安全默认：小程序审批中心只开放客户分配审批；新增类型必须显式配置。
            return TYPE_ALLOCATION.equalsIgnoreCase(type);
        }
        for (String t : enabledTypes) {
            if (t != null && type != null && type.equalsIgnoreCase(t.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验审批类型已开放，未开放直接拒绝（供小程序端统一审批入口调用）。
     *
     * @param type 审批类型
     * @return 归一化后的大写类型
     */
    public String requireTypeEnabled(String type) {
        String t = requireSupportedType(type);
        if (!typeEnabled(t)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "审批类型未开放：" + t);
        }
        return t;
    }

    /** 按类型取一页待审数据（PRODUCT / DOWNLOAD / MATERIAL_REVIEW 均按 status=PENDING 过滤，保证 total 即待审数）。 */
    private PageResult<Map<String, Object>> pendingSlice(String type, int page, int size) {
        if (TYPE_PRODUCT.equals(type)) {
            return productPage(STATUS_PENDING, null, page, size, null, "DESC");
        }
        if (TYPE_DOWNLOAD.equals(type)) {
            return downloadPage(STATUS_PENDING, null, page, size, null, "DESC");
        }
        if (TYPE_MATERIAL_REVIEW.equals(type)) {
            return materialReviewPage(MaterialReviewService.STATUS_PENDING, null, page, size);
        }
        return allocationPage(page, size);
    }

    /** 解析请求类型：空 / ALL → 全部类型；否则单类型（校验合法性）。 */
    private List<String> requestedTypes(String type) {
        if (!StringUtils.hasText(type) || TYPE_ALL.equalsIgnoreCase(type.trim())) {
            return ALL_TYPES;
        }
        return Collections.singletonList(requireSupportedType(type));
    }

    /** 归一化并校验审批类型，非法抛参数异常。 */
    private String requireSupportedType(String type) {
        String t = StringUtils.hasText(type) ? type.trim().toUpperCase() : "";
        if (!ALL_TYPES.contains(t)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的审批类型：" + type);
        }
        return t;
    }

    /** 页码归一：≤0 视为第 1 页。 */
    private int normalizePage(int page) {
        return page <= 0 ? 1 : page;
    }

    /** 每页大小归一：≤0 视为 10，上限 100。 */
    private int normalizeSize(int size) {
        return size <= 0 ? 10 : Math.min(size, 100);
    }

    /**
     * createdAt 倒序稳定比较器（§5.6 跨类型归并用）。
     *
     * <p>时间戳值可能为多种形态：ISO-8601 字符串（含空格分隔）、数字串（毫秒 / 秒级 epoch）、
     * 或实体本身为 {@link LocalDateTime}。统一解析为 long 后再比较，保证不同来源口径一致。</p>
     *
     * <p>缺失 / 无法解析的条目视为 {@code null}，按 {@code nullsLast} 落到段序兜底位置
     * （保持其在原类型段内的相对顺序），不排到最前；结合稳定排序，段内顺序得以保留。</p>
     */
    private static final class CreatedAtDescComparator implements Comparator<Map<String, Object>> {
        @Override
        public int compare(Map<String, Object> a, Map<String, Object> b) {
            Long ka = toEpoch(a.get("createdAt"));
            Long kb = toEpoch(b.get("createdAt"));
            return Comparator.nullsLast(Comparator.<Long>reverseOrder()).compare(ka, kb);
        }
    }

    /** createdAt 值 → epoch 毫秒，无法解析返回 null。 */
    private static Long toEpoch(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            long n = ((Number) v).longValue();
            // 秒级 epoch（10 位）转毫秒，避免与毫秒级（13 位）混排
            return Math.abs(n) < 1_000_000_000_000L ? n * 1000L : n;
        }
        if (v instanceof LocalDateTime) {
            return ((LocalDateTime) v).toEpochSecond(java.time.ZoneOffset.UTC) * 1000L;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignore) {
            // 非数字：尝试 ISO 解析（兼容 "2024-01-02 03:04:05" 这种空格分隔形态）
            try {
                LocalDateTime ldt = LocalDateTime.parse(s.replace(' ', 'T'));
                return ldt.toEpochSecond(java.time.ZoneOffset.UTC) * 1000L;
            } catch (DateTimeParseException ignore2) {
                try {
                    return LocalDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toEpochSecond(java.time.ZoneOffset.UTC) * 1000L;
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        }
    }
}
