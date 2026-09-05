package com.loan.mini.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.approval.entity.ProductApproval;
import com.loan.approval.mapper.ProductApprovalMapper;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.product.entity.BankProduct;
import com.loan.product.mapper.BankProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小程序端渠道产品管理（C9 撤销审批 + 申请删除 + 撤销删除）。
 *
 * <p><b>数据模型：</b>复用既有 {@link ProductApproval}（t_product_approval），
 * 以 {@code applyType} 区分申请类型，以 {@code approveStatus} 承载流程状态，
 * 不新增表：
 * <ul>
 *   <li>{@code applyType}：CREATE（录入/上架申请）/ DELETE（删除申请）</li>
 *   <li>{@code approveStatus}：DRAFT 草稿 → PENDING 待审批 → OK 已上架 / REJECTED 已驳回；
 *       另有 PENDING_DELETE 待删除审批（C9 新增状态值）</li>
 * </ul>
 *
 * <p><b>状态机：</b>
 * <pre>
 *   DRAFT ─submit→ PENDING ─通过→ OK ─deleteApply→ PENDING_DELETE ─通过→ 物理删除（留痕）
 *     ↑              │（revoke）                        │（deleteCancel / 驳回 → OK）
 *     └──────────────┘                                  └─────────────────────────┘
 * </pre>
 *
 * <p><b>权限：</b>仅渠道可管理自有产品（沙箱内只见本渠道录入）；
 * 删除终审由运营 / 超级管理员在管理端完成。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class MiniProductService {

    /** 申请类型：录入 / 上架 */
    public static final String APPLY_CREATE = "CREATE";
    /** 申请类型：删除 */
    public static final String APPLY_DELETE = "DELETE";

    /** 状态：草稿 */
    public static final String ST_DRAFT = "DRAFT";
    /** 状态：待审批 */
    public static final String ST_PENDING = "PENDING";
    /** 状态：已上架（审批通过） */
    public static final String ST_OK = "OK";
    /** 状态：已驳回 */
    public static final String ST_REJECTED = "REJECTED";
    /** 状态：待删除审批（C9 新增） */
    public static final String ST_PENDING_DELETE = "PENDING_DELETE";

    private final ProductApprovalMapper approvalMapper;
    private final BankProductMapper bankProductMapper;

    /**
     * 我的产品列表（渠道视角，仅本渠道录入）。
     *
     * @param channelUserId 渠道账号 ID
     * @return 产品列表（含状态与驳回原因）
     */
    public List<Map<String, Object>> myProducts(Long channelUserId) {
        List<ProductApproval> list = approvalMapper.selectList(
                new LambdaQueryWrapper<ProductApproval>()
                        .eq(ProductApproval::getChannelUserId, channelUserId)
                        .orderByDesc(ProductApproval::getCreatedAt));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProductApproval a : list) {
            rows.add(toRow(a));
        }
        return rows;
    }

    /**
     * 保存产品（新建草稿，或编辑草稿 / 已驳回后重提）。
     *
     * @param payload       产品信息（bankProductCode 必填）
     * @param user          当前渠道用户
     * @return 审批单号
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(Map<String, Object> payload, LoanUser user) {
        String bankProductCode = str(payload.get("bankProductCode"));
        if (!StringUtils.hasText(bankProductCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "银行产品编码不能为空");
        }
        // 校验产品存在（避免渠道录入不存在的银行产品编码）
        BankProduct product = bankProductMapper.selectOne(new LambdaQueryWrapper<BankProduct>()
                .eq(BankProduct::getProductCode, bankProductCode));
        if (product == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "银行产品不存在：" + bankProductCode);
        }

        // 幂等：同一渠道 + 同一产品 + 草稿/待审批 → 更新而非新建
        ProductApproval exist = approvalMapper.selectOne(new LambdaQueryWrapper<ProductApproval>()
                .eq(ProductApproval::getChannelUserId, user.getUserId())
                .eq(ProductApproval::getBankProductCode, bankProductCode)
                .in(ProductApproval::getApproveStatus, ST_DRAFT, ST_PENDING, ST_REJECTED));
        if (exist != null) {
            exist.setAfterSnapshotJson(toSnapshot(payload));
            exist.setApproveStatus(ST_DRAFT);
            exist.setApproveOpinion(null);
            exist.setUpdatedBy(user.getName());
            exist.setUpdatedAt(LocalDateTime.now());
            approvalMapper.updateById(exist);
            return result(exist.getApprovalNo(), "UPDATED");
        }

        ProductApproval a = new ProductApproval();
        a.setApprovalNo(BizIdGenerator.generate("papr"));
        a.setBankProductCode(bankProductCode);
        a.setChannelUserId(user.getUserId());
        a.setApplyType(APPLY_CREATE);
        a.setApproveStatus(ST_DRAFT);
        a.setAfterSnapshotJson(toSnapshot(payload));
        a.setDuplicateFlag(0);
        a.setCreatedBy(user.getName());
        a.setUpdatedBy(user.getName());
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        approvalMapper.insert(a);
        return result(a.getApprovalNo(), "CREATED");
    }

    /**
     * 产品详情（编辑态回填，C9）。
     *
     * <p>按审批单号定位本渠道的申请（越权校验见 {@link #requireOwn}），
     * 返回表单可回填字段；amountRange（"100-500万"）解析为 amountMin/amountMax，
     * requirement 序列化为 requirementText 供 textarea 展示。
     *
     * @param approvalNo 审批单号
     * @param user       当前渠道用户
     * @return 表单回填字段
     */
    public Map<String, Object> detail(String approvalNo, LoanUser user) {
        ProductApproval a = requireOwn(approvalNo, user);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", a.getApprovalNo());
        m.put("bankProductCode", a.getBankProductCode());
        m.put("cooperateUntil", null);
        m.put("amountMin", null);
        m.put("amountMax", null);
        m.put("requirement", null);
        Map<String, Object> snapshot = parseSnapshot(a.getAfterSnapshotJson());
        if (snapshot != null) {
            m.put("cooperateUntil", snapshot.get("cooperateUntil"));
            String[] range = splitAmountRange(str(snapshot.get("amountRange")));
            m.put("amountMin", range[0]);
            m.put("amountMax", range[1]);
            m.put("requirement", snapshot.get("requirement"));
        }
        return m;
    }

    /**
     * 编辑产品（C9：DRAFT / REJECTED 可编辑重提）。
     *
     * <p>按路径 code 定位审批单更新（而非按 bankProductCode 重建），
     * 编码创建后不可修改（与前端 disabled 双重防护）；状态非法时拒绝。
     *
     * @param approvalNo 审批单号
     * @param payload    产品信息（bankProductCode 必填，且须与审批单原编码一致）
     * @param user       当前渠道用户
     * @return 保存结果（approvalNo + action=UPDATED）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(String approvalNo, Map<String, Object> payload, LoanUser user) {
        ProductApproval a = requireOwn(approvalNo, user);
        if (!ST_DRAFT.equals(a.getApproveStatus()) && !ST_REJECTED.equals(a.getApproveStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前状态不可编辑");
        }
        String bankProductCode = str(payload.get("bankProductCode"));
        if (!StringUtils.hasText(bankProductCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "银行产品编码不能为空");
        }
        // 编辑态编码不可变：与审批单原编码比对（前端 disabled，后端防篡改）
        if (!bankProductCode.equals(a.getBankProductCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品编码创建后不可修改");
        }
        // 校验产品仍存在（避免编辑已被下架/移除的产品编码）
        BankProduct product = bankProductMapper.selectOne(new LambdaQueryWrapper<BankProduct>()
                .eq(BankProduct::getProductCode, bankProductCode));
        if (product == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "银行产品不存在：" + bankProductCode);
        }
        a.setAfterSnapshotJson(toSnapshot(payload));
        a.setApproveStatus(ST_DRAFT);
        a.setApproveOpinion(null);
        a.setUpdatedBy(user.getName());
        a.setUpdatedAt(LocalDateTime.now());
        approvalMapper.updateById(a);
        return result(a.getApprovalNo(), "UPDATED");
    }

    /**
     * 提交审批（DRAFT → PENDING），走运营 / 超管终审。
     *
     * @param approvalNo 审批单号
     * @param user       当前渠道用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(String approvalNo, LoanUser user) {
        ProductApproval a = requireOwn(approvalNo, user);
        if (!ST_DRAFT.equals(a.getApproveStatus()) && !ST_REJECTED.equals(a.getApproveStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前状态不可提交审批");
        }
        a.setApproveStatus(ST_PENDING);
        a.setApproveOpinion(null);
        a.setUpdatedBy(user.getName());
        a.setUpdatedAt(LocalDateTime.now());
        approvalMapper.updateById(a);
    }

    /**
     * 撤销审批（PENDING → DRAFT），无需审批，即时生效。
     *
     * @param approvalNo 审批单号
     * @param user       当前渠道用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(String approvalNo, LoanUser user) {
        ProductApproval a = requireOwn(approvalNo, user);
        if (!ST_PENDING.equals(a.getApproveStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅待审批状态可撤销");
        }
        a.setApproveStatus(ST_DRAFT);
        a.setUpdatedBy(user.getName());
        a.setUpdatedAt(LocalDateTime.now());
        approvalMapper.updateById(a);
    }

    /**
     * 申请删除（OK → PENDING_DELETE），需我司运营 / 超管终审，
     * 审批通过后从全量库物理删除（操作留痕至审计日志）。
     *
     * @param approvalNo 审批单号
     * @param reason     删除原因
     * @param user       当前渠道用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyDelete(String approvalNo, String reason, LoanUser user) {
        ProductApproval a = requireOwn(approvalNo, user);
        if (!ST_OK.equals(a.getApproveStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅已上架产品可申请删除");
        }
        a.setApplyType(APPLY_DELETE);
        a.setApproveStatus(ST_PENDING_DELETE);
        a.setApproveOpinion(StringUtils.hasText(reason) ? reason : "渠道主动申请下架");
        a.setUpdatedBy(user.getName());
        a.setUpdatedAt(LocalDateTime.now());
        approvalMapper.updateById(a);
        // TODO 通知运营 / 超管（站内信 + 短信），与统一通知中心对接后补齐
    }

    /**
     * 撤销删除申请（PENDING_DELETE → OK）。
     *
     * @param approvalNo 审批单号
     * @param user       当前渠道用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelDelete(String approvalNo, LoanUser user) {
        ProductApproval a = requireOwn(approvalNo, user);
        if (!ST_PENDING_DELETE.equals(a.getApproveStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅待删除状态可撤销");
        }
        a.setApplyType(APPLY_CREATE);
        a.setApproveStatus(ST_OK);
        a.setApproveOpinion(null);
        a.setUpdatedBy(user.getName());
        a.setUpdatedAt(LocalDateTime.now());
        approvalMapper.updateById(a);
    }

    /* ==================== 管理端：删除终审（C9 闭环） ==================== */

    /**
     * 待删除审批列表（运营 / 超管视角）。
     *
     * @return 待删除产品列表
     */
    public List<Map<String, Object>> pendingDeleteList() {
        List<ProductApproval> list = approvalMapper.selectList(
                new LambdaQueryWrapper<ProductApproval>()
                        .eq(ProductApproval::getApproveStatus, ST_PENDING_DELETE)
                        .orderByAsc(ProductApproval::getCreatedAt));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProductApproval a : list) {
            rows.add(toRow(a));
        }
        return rows;
    }

    /**
     * 删除审批（运营 / 超管终审）。
     *
     * <p>批准 → 物理删除审批单（对应产品从全量库移除，留痕由审计日志承载）；
     * 驳回 → 回到 OK 并回填驳回原因，渠道侧可见。
     *
     * @param approvalNo 审批单号
     * @param approve    是否批准
     * @param opinion    审批意见
     * @param operator   审批人
     */
    @Transactional(rollbackFor = Exception.class)
    public void auditDelete(String approvalNo, boolean approve, String opinion, String operator) {
        ProductApproval a = approvalMapper.selectOne(new LambdaQueryWrapper<ProductApproval>()
                .eq(ProductApproval::getApprovalNo, approvalNo));
        if (a == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "审批单不存在");
        }
        if (!ST_PENDING_DELETE.equals(a.getApproveStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该审批单不在待删除状态");
        }
        if (approve) {
            // 批准删除：物理移除审批单（产品从全量库移除）
            // TODO 同步清理 t_partner_product 中对应上架记录，并写 t_audit_log 留痕
            approvalMapper.deleteById(a.getId());
        } else {
            a.setApplyType(APPLY_CREATE);
            a.setApproveStatus(ST_OK);
            a.setApproveOpinion(StringUtils.hasText(opinion) ? opinion : "删除申请被驳回");
            a.setApproverStaffCode(operator);
            a.setApprovedAt(LocalDateTime.now());
            a.setUpdatedBy(operator);
            a.setUpdatedAt(LocalDateTime.now());
            approvalMapper.updateById(a);
        }
        // TODO 通知渠道申请人审批结果（站内信 + 短信）
    }

    /* ==================== 私有方法 ==================== */

    /** 按审批单号取本渠道的申请（越权校验） */
    private ProductApproval requireOwn(String approvalNo, LoanUser user) {
        ProductApproval a = approvalMapper.selectOne(new LambdaQueryWrapper<ProductApproval>()
                .eq(ProductApproval::getApprovalNo, approvalNo));
        if (a == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "产品不存在");
        }
        if (user == null || user.getUserId() == null
                || !user.getUserId().equals(a.getChannelUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作本渠道录入的产品");
        }
        return a;
    }

    /** 实体 → 出参行（补产品名称与银行名） */
    private Map<String, Object> toRow(ProductApproval a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", a.getApprovalNo());
        m.put("bankProductCode", a.getBankProductCode());
        m.put("applyType", a.getApplyType());
        m.put("status", a.getApproveStatus());
        m.put("rejectReason", a.getApproveOpinion());
        m.put("approverStaffCode", a.getApproverStaffCode());
        m.put("createdAt", a.getCreatedAt());
        m.put("updatedAt", a.getUpdatedAt());
        // 补产品名称与额度利率区间（t_bank_product 以 productCode 为业务编码）
        BankProduct p = bankProductMapper.selectOne(new LambdaQueryWrapper<BankProduct>()
                .eq(BankProduct::getProductCode, a.getBankProductCode()));
        if (p != null) {
            m.put("productName", p.getProductName());
            m.put("bankChannelCode", p.getBankChannelCode());
            m.put("amountRange", rangeText(p.getAmountMin(), p.getAmountMax(), "万"));
            m.put("rate", rangeText(p.getRateMin(), p.getRateMax(), "%"));
        }
        return m;
    }

    /** 数值区间文案：min-max + 单位（统一处理空值与相等值） */
    private String rangeText(java.math.BigDecimal min, java.math.BigDecimal max, String unit) {
        if (min == null && max == null) return null;
        if (min != null && max != null && min.compareTo(max) == 0) return min.stripTrailingZeros().toPlainString() + unit;
        String lo = min == null ? "?" : min.stripTrailingZeros().toPlainString();
        String hi = max == null ? "?" : max.stripTrailingZeros().toPlainString();
        return lo + "-" + hi + unit;
    }

    /** 入参 → 快照 JSON（简化实现：直接序列化 Map） */
    private String toSnapshot(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 快照 JSON → Map（解析失败返回 null） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            Object v = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Object.class);
            return v instanceof Map ? (Map<String, Object>) v : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 金额区间文案 → [min, max]。
     *
     * <p>支持三种形态："100-500万" → ["100","500"]；"100万"（等值）→ ["100","100"]；
     * 空 / 非法 / "?" 占位 → null。
     */
    private String[] splitAmountRange(String rangeText) {
        if (!StringUtils.hasText(rangeText)) {
            return new String[]{null, null};
        }
        String raw = rangeText.trim();
        if (raw.endsWith("万")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        int dash = raw.indexOf('-');
        if (dash < 0) {
            String single = normalizeRangePart(raw.trim());
            return new String[]{single, single};
        }
        String lo = normalizeRangePart(raw.substring(0, dash).trim());
        String hi = normalizeRangePart(raw.substring(dash + 1).trim());
        return new String[]{lo, hi};
    }

    /** 区间单值规范化："?" 或空视为 null（编辑回填时空缺段置空） */
    private String normalizeRangePart(String part) {
        if (!StringUtils.hasText(part) || "?".equals(part)) {
            return null;
        }
        return part;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v).trim();
    }

    private Map<String, Object> result(String approvalNo, String action) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", approvalNo);
        m.put("approvalNo", approvalNo);
        m.put("action", action);
        return m;
    }
}
