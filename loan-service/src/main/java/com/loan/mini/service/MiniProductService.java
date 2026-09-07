package com.loan.mini.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.loan.approval.entity.ProductApproval;
import com.loan.approval.mapper.ProductApprovalMapper;
import com.loan.channel.entity.ChannelUser;
import com.loan.channel.dto.ChannelProductReq;
import com.loan.channel.mapper.ChannelUserMapper;
import com.loan.product.entity.BankChannel;
import com.loan.product.mapper.BankChannelMapper;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.product.entity.BankProduct;
import com.loan.product.mapper.BankProductMapper;
import com.loan.partner.service.PartnerProductService;
import com.loan.notification.dto.NotificationReq;
import com.loan.notification.entity.Notification;
import com.loan.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 小程序端渠道产品管理（C9 撤销审批 + 申请删除 + 撤销删除）。
 *
 * <p><b>数据模型：</b>复用既有 {@link ProductApproval}（t_product_approval），
 * 以 {@code applyType} 区分申请类型，以 {@code approveStatus} 承载流程状态，
 * 不新增表：
 * <ul>
 *   <li>{@code applyType}：CREATE（录入/上架申请）/ DELETE（删除申请）</li>
 *   <li>{@code approveStatus}：DRAFT 草稿 → PENDING 待审批 → APPROVED 已上架 / REJECTED 已驳回；
 *       另有 PENDING_DELETE 待删除审批（C9 新增状态值）</li>
 * </ul>
 *
 * <p><b>状态机：</b>
 * <pre>
 *   DRAFT ─submit→ PENDING ─通过→ APPROVED ─deleteApply→ PENDING_DELETE ─通过→ OFFLINE（留痕）
 *     ↑              │（revoke）                              │（deleteCancel → APPROVED）
 *     └──────────────┘                                  └─────────────────────────┘
 * </pre>
 *
 * <p><b>权限：</b>仅渠道可管理自有产品（沙箱内只见本渠道录入）；
 * 录入与删除终审均由老板 / 超级管理员完成。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class MiniProductService {

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** 申请类型：录入 / 上架 */
    public static final String APPLY_CREATE = "CREATE";
    /** 申请类型：删除 */
    public static final String APPLY_DELETE = "DELETE";

    /** 状态：草稿 */
    public static final String ST_DRAFT = "DRAFT";
    /** 状态：待审批 */
    public static final String ST_PENDING = "PENDING";
    /** 状态：已上架（审批通过） */
    public static final String ST_APPROVED = "APPROVED";
    /** 历史版本曾写入的已上架状态，仅用于兼容读取，不再新增。 */
    public static final String ST_LEGACY_OK = "OK";
    /** 状态：已驳回 */
    public static final String ST_REJECTED = "REJECTED";
    /** 状态：待删除审批（C9 新增） */
    public static final String ST_PENDING_DELETE = "PENDING_DELETE";

    private final ProductApprovalMapper approvalMapper;
    private final BankProductMapper bankProductMapper;
    private final PartnerProductService partnerProductService;
    private final NotificationService notificationService;
    private final ChannelUserMapper channelUserMapper;
    private final BankChannelMapper bankChannelMapper;

    /**
     * 我的产品列表（渠道视角，仅本渠道录入）。
     *
     * @param user 当前渠道账号
     * @return 产品列表（含状态与驳回原因）
     */
    public List<Map<String, Object>> myProducts(LoanUser user) {
        requireChannelUser(user);
        List<ProductApproval> list = approvalMapper.selectList(
                new LambdaQueryWrapper<ProductApproval>()
                        .eq(ProductApproval::getChannelUserId, user.getUserId())
                        .orderByDesc(ProductApproval::getCreatedAt));
        Map<String, BankProduct> products = productMap(list);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProductApproval a : list) {
            rows.add(toRow(a, products.get(a.getBankProductCode())));
        }
        return rows;
    }

    /**
     * 保存产品（新建草稿，或编辑草稿 / 已驳回后重提）。
     *
     * @param req           渠道产品表单；产品编码和所属银行均由后端生成/推导
     * @param user          当前渠道用户
     * @return 审批单号
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> save(ChannelProductReq req, LoanUser user) {
        requireChannelUser(user);
        validateProductReq(req);
        BankProduct product = createChannelProduct(req, user);
        String bankProductCode = product.getProductCode();

        ProductApproval a = new ProductApproval();
        a.setApprovalNo(BizIdGenerator.generate("papr"));
        a.setBankProductCode(bankProductCode);
        a.setChannelUserId(user.getUserId());
        a.setApplyType(APPLY_CREATE);
        a.setApproveStatus(ST_DRAFT);
        a.setAfterSnapshotJson(toSnapshot(req));
        a.setDuplicateFlag(0);
        a.setCreatedBy(user.getName());
        a.setUpdatedBy(user.getName());
        a.setCreatedAt(LocalDateTime.now());
        a.setTimeoutAt(LocalDateTime.now().plusHours(48));
        a.setUpdatedAt(LocalDateTime.now());
        approvalMapper.insert(a);
        return result(a.getApprovalNo(), "CREATED");
    }

    /**
     * 产品详情（编辑态回填，C9）。
     *
     * <p>按审批单号定位本渠道的申请（越权校验见 {@link #requireOwn}），
     * 返回产品业务字段及合作有效期；不暴露物理主键和内部产品编码。
     *
     * @param approvalNo 审批单号
     * @param user       当前渠道用户
     * @return 表单回填字段
     */
    public Map<String, Object> detail(String approvalNo, LoanUser user) {
        ProductApproval a = requireOwn(approvalNo, user);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", a.getApprovalNo());
        BankProduct product = requireChannelProduct(a.getBankProductCode(), user);
        m.put("productName", product.getProductName());
        m.put("customerGroup", product.getCustomerGroup());
        m.put("cooperateUntil", null);
        m.put("amountMin", product.getAmountMin());
        m.put("amountMax", product.getAmountMax());
        m.put("rateMin", product.getRateMin());
        m.put("rateMax", product.getRateMax());
        m.put("termMin", product.getTermMin());
        m.put("termMax", product.getTermMax());
        m.put("taxThreshold", product.getTaxThreshold());
        m.put("invoiceRequire", product.getInvoiceRequire());
        m.put("bizTermsJson", product.getBizTermsJson());
        Map<String, Object> snapshot = parseSnapshot(a.getAfterSnapshotJson());
        if (snapshot != null) {
            m.put("cooperateUntil", snapshot.get("cooperateUntil"));
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
     * @param req        可编辑业务字段；产品编码、所属银行、来源与状态不接收前端改写
     * @param user       当前渠道用户
     * @return 保存结果（approvalNo + action=UPDATED）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(String approvalNo, ChannelProductReq req, LoanUser user) {
        ProductApproval a = requireOwn(approvalNo, user);
        if (!ST_DRAFT.equals(a.getApproveStatus()) && !ST_REJECTED.equals(a.getApproveStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前状态不可编辑");
        }
        validateProductReq(req);
        BankProduct product = requireChannelProduct(a.getBankProductCode(), user);
        applyProductFields(product, req);
        product.setStatus(ST_DRAFT);
        product.setUpdatedBy(user.getName());
        product.setUpdatedAt(LocalDateTime.now());
        bankProductMapper.updateById(product);
        a.setAfterSnapshotJson(toSnapshot(req));
        a.setApproveStatus(ST_DRAFT);
        a.setApproveOpinion(null);
        a.setUpdatedBy(user.getName());
        a.setUpdatedAt(LocalDateTime.now());
        approvalMapper.updateById(a);
        return result(a.getApprovalNo(), "UPDATED");
    }

    /**
     * 提交审批（DRAFT → PENDING），走老板 / 超级管理员终审。
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
        updateProductStatus(a.getBankProductCode(), ST_PENDING, user.getName());
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
        updateProductStatus(a.getBankProductCode(), ST_DRAFT, user.getName());
    }

    /**
     * 申请删除（APPROVED → PENDING_DELETE），需我司老板 / 超管终审，
     * 审批通过后合作库置为 OFFLINE 并保留审批记录。
     *
     * @param approvalNo 审批单号
     * @param reason     删除原因
     * @param user       当前渠道用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyDelete(String approvalNo, String reason, LoanUser user) {
        ProductApproval a = requireOwn(approvalNo, user);
        if (!isApproved(a.getApproveStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅已上架产品可申请删除");
        }
        a.setApplyType(APPLY_DELETE);
        a.setApproveStatus(ST_PENDING_DELETE);
        a.setApproveOpinion(StringUtils.hasText(reason) ? reason : "渠道主动申请下架");
        a.setUpdatedBy(user.getName());
        a.setUpdatedAt(LocalDateTime.now());
        approvalMapper.updateById(a);
    }

    /**
     * 撤销删除申请（PENDING_DELETE → APPROVED）。
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
        a.setApproveStatus(ST_APPROVED);
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
        Map<String, BankProduct> products = productMap(list);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProductApproval a : list) {
            rows.add(toRow(a, products.get(a.getBankProductCode())));
        }
        return rows;
    }

    /**
     * 删除审批（老板 / 超级管理员终审）。
     *
     * <p>批准 → 合作库置 OFFLINE、审批记录保留；
     * 驳回 → 记录 DELETE/REJECTED，渠道侧仍映射为已上架并可再次申请。
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
        String targetStatus = approve ? ST_APPROVED : ST_REJECTED;
        if (!ST_PENDING_DELETE.equals(a.getApproveStatus())) {
            if (APPLY_DELETE.equals(a.getApplyType()) && targetStatus.equals(a.getApproveStatus())) {
                if (approve) {
                    partnerProductService.offlineByApproval(a.getBankProductCode(), operator);
                }
                sendApprovalResult(a, approve);
                return;
            }
            throw new BusinessException(ResultCode.PARAM_ERROR, "该审批单已被处理，请刷新后重试");
        }
        String finalOpinion = StringUtils.hasText(opinion) ? opinion
                : (approve ? "删除申请已通过" : "删除申请被驳回");
        LocalDateTime now = LocalDateTime.now();
        int updated = approvalMapper.update(null, new LambdaUpdateWrapper<ProductApproval>()
                .eq(ProductApproval::getApprovalNo, approvalNo)
                .eq(ProductApproval::getApproveStatus, ST_PENDING_DELETE)
                .set(ProductApproval::getApproveStatus, targetStatus)
                .set(ProductApproval::getApproveOpinion, finalOpinion)
                .set(ProductApproval::getApproverStaffCode, operator)
                .set(ProductApproval::getApprovedAt, now)
                .set(ProductApproval::getUpdatedBy, operator)
                .set(ProductApproval::getUpdatedAt, now));
        if (updated == 0) {
            ProductApproval latest = approvalMapper.selectOne(new LambdaQueryWrapper<ProductApproval>()
                    .eq(ProductApproval::getApprovalNo, approvalNo));
            if (latest != null && APPLY_DELETE.equals(latest.getApplyType())
                    && targetStatus.equals(latest.getApproveStatus())) {
                if (approve) {
                    partnerProductService.offlineByApproval(latest.getBankProductCode(), operator);
                }
                sendApprovalResult(latest, approve);
                return;
            }
            throw new BusinessException(ResultCode.PARAM_ERROR, "审批单已由其他管理员处理，请刷新后查看");
        }
        a.setApproveStatus(targetStatus);
        a.setApproveOpinion(finalOpinion);
        a.setApproverStaffCode(operator);
        a.setApprovedAt(now);
        if (approve) {
            partnerProductService.offlineByApproval(a.getBankProductCode(), operator);
        }
        sendApprovalResult(a, approve);
    }

    /* ==================== 私有方法 ==================== */

    /** 按审批单号取本渠道的申请（越权校验） */
    private ProductApproval requireOwn(String approvalNo, LoanUser user) {
        requireChannelUser(user);
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

    /** Service 层角色门禁，防止任何 Controller 漏配时由物理 ID 碰撞造成越权。 */
    private void requireChannelUser(LoanUser user) {
        if (user == null || user.getUserId() == null || !StringUtils.hasText(user.getUserNo())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        if (!LoanUser.TYPE_CHANNEL.equals(user.getUserType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅渠道合作方可管理产品");
        }
    }

    /** 审批单转出参；列表由调用方批量预装产品，详情允许单条查询。 */
    private Map<String, Object> toRow(ProductApproval a, BankProduct p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", a.getApprovalNo());
        m.put("applyType", a.getApplyType());
        m.put("status", clientStatus(a));
        m.put("rejectReason", ST_REJECTED.equals(a.getApproveStatus()) ? a.getApproveOpinion() : null);
        m.put("createdAt", a.getCreatedAt());
        m.put("updatedAt", a.getUpdatedAt());
        // 补产品名称与额度利率区间（t_bank_product 以 productCode 为业务编码）
        if (p != null) {
            m.put("productName", p.getProductName());
            m.put("bankChannelCode", p.getBankChannelCode());
            m.put("amountRange", amountRangeText(p.getAmountMin(), p.getAmountMax()));
            m.put("rate", percentRangeText(p.getRateMin(), p.getRateMax()));
            m.put("customerGroup", p.getCustomerGroup());
        }
        return m;
    }

    /** 审批完成后通知申请渠道；按业务审批单号去重，避免并发重放重复发送。 */
    private void sendApprovalResult(ProductApproval approval, boolean approved) {
        if (approval.getChannelUserId() == null) {
            return;
        }
        ChannelUser channelUser = channelUserMapper.selectById(approval.getChannelUserId());
        if (channelUser == null || !StringUtils.hasText(channelUser.getPhoneHash())) {
            return;
        }
        String userNo = channelUser.getPhoneHash();
        String relatedId = approval.getApprovalNo();
        NotificationReq req = new NotificationReq();
        req.setUserNo(userNo);
        req.setType(Notification.TYPE_PRODUCT_APPROVAL);
        req.setTitle(approved ? "产品删除申请已通过" : "产品删除申请未通过");
        req.setContent(approved ? "相关合作产品已下架，可在我的产品中查看。"
                : "相关合作产品继续保持上架，请查看审批意见。");
        req.setRelatedId(relatedId);
        notificationService.sendOnce(req);
    }

    /** 一次批量装配列表所需产品，禁止逐行查库。 */
    private Map<String, BankProduct> productMap(List<ProductApproval> approvals) {
        List<String> codes = approvals.stream().map(ProductApproval::getBankProductCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        if (codes.isEmpty()) {
            return Collections.emptyMap();
        }
        return bankProductMapper.selectList(new LambdaQueryWrapper<BankProduct>()
                .in(BankProduct::getProductCode, codes)).stream()
                .collect(Collectors.toMap(BankProduct::getProductCode, Function.identity(), (left, right) -> left));
    }

    /** 新状态使用 APPROVED，同时兼容历史 OK 存量。 */
    private boolean isApproved(String status) {
        return ST_APPROVED.equals(status) || ST_LEGACY_OK.equals(status);
    }

    /** 删除申请驳回后产品仍处于已上架态，客户端应允许再次发起删除。 */
    private String clientStatus(ProductApproval approval) {
        if (APPLY_DELETE.equals(approval.getApplyType()) && ST_REJECTED.equals(approval.getApproveStatus())) {
            return ST_APPROVED;
        }
        return ST_LEGACY_OK.equals(approval.getApproveStatus()) ? ST_APPROVED : approval.getApproveStatus();
    }


    /** 新增渠道产品：编码由后端生成，所属银行强制取登录渠道。 */
    private BankProduct createChannelProduct(ChannelProductReq req, LoanUser user) {
        ChannelUser channelUser = channelUserMapper.selectById(user.getUserId());
        if (channelUser == null || channelUser.getBankChannelId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "渠道账号未绑定所属银行");
        }
        BankChannel bankChannel = bankChannelMapper.selectById(channelUser.getBankChannelId());
        if (bankChannel == null || !StringUtils.hasText(bankChannel.getChannelCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "所属银行档案不存在");
        }
        BankProduct product = new BankProduct();
        product.setProductCode(BizIdGenerator.generate("product"));
        product.setBankChannelCode(bankChannel.getChannelCode());
        product.setProductName(req.getProductName().trim());
        product.setCustomerGroup(req.getCustomerGroup());
        product.setSource("CHANNEL_SELF");
        product.setChannelUserId(user.getUserId());
        product.setStatus(ST_DRAFT);
        applyProductFields(product, req);
        product.setCreatedBy(user.getName());
        product.setUpdatedBy(user.getName());
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        bankProductMapper.insert(product);
        return product;
    }

    /** 既有产品必须属于当前渠道账号的银行，禁止跨渠道引用。 */
    private BankProduct requireChannelProduct(String productCode, LoanUser user) {
        BankProduct product = bankProductMapper.selectOne(new LambdaQueryWrapper<BankProduct>()
                .eq(BankProduct::getProductCode, productCode).last("limit 1"));
        if (product == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "银行产品不存在");
        }
        String channelCode = user.getBankChannelCode();
        if (!StringUtils.hasText(channelCode)) {
            ChannelUser channelUser = channelUserMapper.selectById(user.getUserId());
            BankChannel bankChannel = channelUser == null || channelUser.getBankChannelId() == null ? null
                    : bankChannelMapper.selectById(channelUser.getBankChannelId());
            channelCode = bankChannel == null ? null : bankChannel.getChannelCode();
        }
        if (!StringUtils.hasText(channelCode) || !channelCode.equals(product.getBankChannelCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能管理所属银行的产品");
        }
        if (product.getChannelUserId() != null && !user.getUserId().equals(product.getChannelUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能管理本人录入的产品");
        }
        return product;
    }

    /** 渠道可维护的产品字段白名单，忽略前端传入的状态、来源和所属银行。 */
    private void applyProductFields(BankProduct product, ChannelProductReq req) {
        product.setProductName(req.getProductName().trim());
        product.setCustomerGroup(req.getCustomerGroup());
        product.setAmountMin(req.getAmountMin());
        product.setAmountMax(req.getAmountMax());
        product.setRateMin(req.getRateMin());
        product.setRateMax(req.getRateMax());
        product.setTermMin(req.getTermMin());
        product.setTermMax(req.getTermMax());
        product.setTaxThreshold(req.getTaxThreshold());
        product.setInvoiceRequire(trimToNull(req.getInvoiceRequire()));
        product.setBizTermsJson(normalizeJson(req.getBizTermsJson()));
    }

    /** 业务字段及区间关系校验。 */
    private void validateProductReq(ChannelProductReq req) {
        if (req == null || !StringUtils.hasText(req.getProductName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品名称不能为空");
        }
        if (!"ENTERPRISE".equals(req.getCustomerGroup()) && !"PERSONAL".equals(req.getCustomerGroup())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择企业或个人客群");
        }
        validateRange(req.getAmountMin(), req.getAmountMax(), "额度");
        validateRange(req.getRateMin(), req.getRateMax(), "利率");
        if (req.getTermMin() != null && req.getTermMax() != null && req.getTermMin() > req.getTermMax()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "期限下限不能大于上限");
        }
        if (StringUtils.hasText(req.getCooperateUntil())) {
            try {
                if (!LocalDate.parse(req.getCooperateUntil()).isAfter(LocalDate.now())) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "合作有效期必须晚于今天");
                }
            } catch (DateTimeParseException ex) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "合作有效期格式应为 yyyy-MM-dd");
            }
        }
        normalizeJson(req.getBizTermsJson());
    }

    private void validateRange(BigDecimal min, BigDecimal max, String fieldName) {
        if (min != null && min.signum() < 0 || max != null && max.signum() < 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, fieldName + "不能小于 0");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, fieldName + "下限不能大于上限");
        }
    }

    /** 数值区间文案：min-max + 单位（统一处理空值与相等值） */
    private String rangeText(java.math.BigDecimal min, java.math.BigDecimal max, String unit) {
        if (min == null && max == null) return null;
        if (min != null && max != null && min.compareTo(max) == 0) return min.stripTrailingZeros().toPlainString() + unit;
        String lo = min == null ? "?" : min.stripTrailingZeros().toPlainString();
        String hi = max == null ? "?" : max.stripTrailingZeros().toPlainString();
        return lo + "-" + hi + unit;
    }

    /** 数据库存元，渠道列表统一转换为万元展示。 */
    private String amountRangeText(BigDecimal min, BigDecimal max) {
        BigDecimal divisor = new BigDecimal("10000");
        return rangeText(min == null ? null : min.divide(divisor),
                max == null ? null : max.divide(divisor), "万");
    }

    /** 数据库存小数利率，渠道列表统一转换为百分比展示。 */
    private String percentRangeText(BigDecimal min, BigDecimal max) {
        BigDecimal multiplier = new BigDecimal("100");
        return rangeText(min == null ? null : min.multiply(multiplier),
                max == null ? null : max.multiply(multiplier), "%");
    }

    /** 入参 → 快照 JSON（简化实现：直接序列化 Map） */
    private String toSnapshot(ChannelProductReq req) {
        try {
            return OBJECT_MAPPER.writeValueAsString(req);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "产品快照生成失败");
        }
    }

    /** 快照 JSON → Map（解析失败返回 null） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSnapshot(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            Object v = OBJECT_MAPPER.readValue(json, Object.class);
            return v instanceof Map ? (Map<String, Object>) v : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeJson(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            Object parsed = OBJECT_MAPPER.readValue(json, Object.class);
            return OBJECT_MAPPER.writeValueAsString(parsed);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "进件要求格式不正确");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void updateProductStatus(String productCode, String status, String operator) {
        bankProductMapper.update(null, new LambdaUpdateWrapper<BankProduct>()
                .eq(BankProduct::getProductCode, productCode)
                .set(BankProduct::getStatus, status)
                .set(BankProduct::getUpdatedBy, operator)
                .set(BankProduct::getUpdatedAt, LocalDateTime.now()));
    }

    private Map<String, Object> result(String approvalNo, String action) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", approvalNo);
        m.put("approvalNo", approvalNo);
        m.put("action", action);
        return m;
    }
}
