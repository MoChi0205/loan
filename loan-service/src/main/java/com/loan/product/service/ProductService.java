package com.loan.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.common.util.FieldLengthGuard;
import com.loan.exception.BusinessException;
import com.loan.product.dto.ProductSaveReq;
import com.loan.product.entity.BankChannel;
import com.loan.product.entity.BankProduct;
import com.loan.product.mapper.BankChannelMapper;
import com.loan.product.mapper.BankProductMapper;
import com.loan.approval.entity.ProductApproval;
import com.loan.approval.mapper.ProductApprovalMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;

/**
 * 产品管理写服务（新增 / 编辑 / 删除 / 详情）。
 *
 * <p>bankName → bankChannelCode 解析：按名称精确匹配 {@code t_bank_channel}，不存在则自动建档（渠道编码随机生成）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final BankProductMapper bankProductMapper;

    private final BankChannelMapper bankChannelMapper;
    private final ProductApprovalMapper productApprovalMapper;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 新增产品。
     *
     * @param req      请求
     * @param operator 操作人姓名
     * @return 产品 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(ProductSaveReq req, String operator, String staffNo) {
        validate(req, false);
        if (bankProductMapper.selectCount(new LambdaQueryWrapper<BankProduct>()
                .eq(BankProduct::getProductName, req.getProductName())
                .eq(BankProduct::getCustomerGroup, req.getCustomerGroup())
                .eq(BankProduct::getBankChannelCode, resolveChannel(req.getBankName()))) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "同银行、同产品名和客群的产品已存在");
        }
        if (bankProductMapper.selectCount(new LambdaQueryWrapper<BankProduct>()
                .eq(BankProduct::getProductCode, req.getProductCode())) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品编码已存在");
        }
        BankProduct product = toEntity(req);
        product.setBankChannelCode(resolveChannel(req.getBankName()));
        product.setStatus("PENDING");
        product.setCreatedBy(operator);
        product.setCreatedStaffNo(staffNo);
        bankProductMapper.insert(product);
        ProductApproval approval = new ProductApproval();
        approval.setApprovalNo(BizIdGenerator.generate("papr"));
        approval.setBankProductCode(product.getProductCode());
        approval.setApplyType("CREATE");
        approval.setApproveStatus("PENDING");
        approval.setDuplicateFlag(0);
        approval.setCreatedBy(operator);
        approval.setUpdatedBy(operator);
        approval.setCreatedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        approval.setTimeoutAt(LocalDateTime.now().plusHours(48));
        try { approval.setAfterSnapshotJson(OBJECT_MAPPER.writeValueAsString(req)); }
        catch (Exception e) { throw new BusinessException(ResultCode.PARAM_ERROR, "产品资料无法生成审批快照"); }
        productApprovalMapper.insert(approval);
        return product.getProductCode();
    }

    /**
     * 编辑产品。
     *
     * @param req      请求（含 productCode）
     * @param operator 操作人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(ProductSaveReq req, String operator) {
        validate(req, true);
        if (!StringUtils.hasText(req.getProductCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品编码必填");
        }
        BankProduct exist = bankProductMapper.selectOne(new LambdaQueryWrapper<BankProduct>()
                .eq(BankProduct::getProductCode, req.getProductCode()));
        if (exist == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "产品不存在");
        }
        BankProduct product = toEntity(req);
        product.setId(exist.getId());
        product.setBankChannelCode(resolveChannel(req.getBankName()));
        product.setUpdatedBy(operator);
        bankProductMapper.updateById(product);
    }

    /**
     * 提交产品变更审批（员工仅可对本人创建的产品提交；审批通过后由审核流程应用变更）。
     *
     * @param req      变更请求（含 productCode）
     * @param operator 操作人姓名
     * @param staffNo  操作人工号（用于本人产品校验）
     * @return 审批工单号
     */
    @Transactional(rollbackFor = Exception.class)
    public String submitUpdateApproval(ProductSaveReq req, String operator, String staffNo) {
        validate(req, true);
        if (!StringUtils.hasText(req.getProductCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品编码必填");
        }
        BankProduct exist = requireOwnedProduct(req.getProductCode(), staffNo);
        requireNoPendingApproval(exist.getProductCode());
        LocalDateTime now = LocalDateTime.now();
        ProductApproval approval = new ProductApproval();
        approval.setApprovalNo(BizIdGenerator.generate("papr"));
        approval.setBankProductCode(exist.getProductCode());
        approval.setApplyType("UPDATE");
        approval.setApproveStatus("PENDING");
        approval.setDuplicateFlag(0);
        approval.setCreatedBy(operator);
        approval.setUpdatedBy(operator);
        approval.setCreatedAt(now);
        approval.setUpdatedAt(now);
        approval.setTimeoutAt(now.plusHours(48));
        try {
            BankProduct target = toEntity(req);
            target.setBankChannelCode(resolveChannel(req.getBankName()));
            approval.setBeforeSnapshotJson(OBJECT_MAPPER.writeValueAsString(exist));
            approval.setAfterSnapshotJson(OBJECT_MAPPER.writeValueAsString(target));
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品资料无法生成审批快照");
        }
        productApprovalMapper.insert(approval);
        return approval.getApprovalNo();
    }

    /**
     * 提交产品下线审批（删除理由必填；审批通过后逻辑下线 OFFLINE，不做物理删除）。
     *
     * @param productCode 产品编码
     * @param reason      删除理由（必填）
     * @param operator    操作人姓名
     * @param staffNo     操作人工号（用于本人产品校验）
     * @return 审批工单号
     */
    @Transactional(rollbackFor = Exception.class)
    public String submitOfflineApproval(String productCode, String reason, String operator, String staffNo) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "删除理由必填");
        }
        BankProduct exist = requireOwnedProduct(productCode, staffNo);
        requireNoPendingApproval(exist.getProductCode());
        LocalDateTime now = LocalDateTime.now();
        ProductApproval approval = new ProductApproval();
        approval.setApprovalNo(BizIdGenerator.generate("papr"));
        approval.setBankProductCode(exist.getProductCode());
        approval.setApplyType("DELETE");
        approval.setApplyReason(reason);
        approval.setApproveStatus("PENDING");
        approval.setDuplicateFlag(0);
        approval.setCreatedBy(operator);
        approval.setUpdatedBy(operator);
        approval.setCreatedAt(now);
        approval.setUpdatedAt(now);
        approval.setTimeoutAt(now.plusHours(48));
        try { approval.setBeforeSnapshotJson(OBJECT_MAPPER.writeValueAsString(exist)); }
        catch (Exception e) { throw new BusinessException(ResultCode.PARAM_ERROR, "产品资料无法生成审批快照"); }
        productApprovalMapper.insert(approval);
        return approval.getApprovalNo();
    }

    /** 校验产品存在且为本人创建（员工数据范围硬约束）。 */
    private BankProduct requireOwnedProduct(String productCode, String staffNo) {
        BankProduct exist = bankProductMapper.selectOne(new LambdaQueryWrapper<BankProduct>()
                .eq(BankProduct::getProductCode, productCode));
        if (exist == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "产品不存在");
        }
        if (StringUtils.hasText(staffNo) && !staffNo.equals(exist.getCreatedStaffNo())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅可对本人创建的产品提交审批");
        }
        return exist;
    }

    /** 防重复提交：同一产品存在待审核工单时禁止再次提交。 */
    private void requireNoPendingApproval(String productCode) {
        if (productApprovalMapper.selectCount(new LambdaQueryWrapper<ProductApproval>()
                .eq(ProductApproval::getBankProductCode, productCode)
                .eq(ProductApproval::getApproveStatus, "PENDING")) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该产品存在待审核工单，请等待审核完成后再提交");
        }
    }

    /**
     * 产品详情（按业务唯一编码）。
     *
     * @param productCode 产品编码
     * @return 产品实体
     */
    public BankProduct getByCode(String productCode) {
        return bankProductMapper.selectOne(new LambdaQueryWrapper<BankProduct>()
                .eq(BankProduct::getProductCode, productCode));
    }

    /**
     * 删除产品（逻辑下线：置 OFFLINE，保留历史数据与审批留痕）。
     *
     * @param productCode 产品编码
     * @param operator    操作人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByCode(String productCode, String operator) {
        BankProduct exist = bankProductMapper.selectOne(new LambdaQueryWrapper<BankProduct>()
                .eq(BankProduct::getProductCode, productCode));
        if (exist == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "产品不存在");
        }
        exist.setStatus("OFFLINE");
        exist.setUpdatedBy(operator);
        exist.setUpdatedAt(LocalDateTime.now());
        bankProductMapper.updateById(exist);
    }

    /**
     * 校验必填项。
     */
    private void validate(ProductSaveReq req, boolean requireStatus) {
        if (!StringUtils.hasText(req.getProductCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品编码必填");
        }
        // 长度显式校验：product_code varchar(64) / product_name varchar(128)，超长直接报错，禁止静默截断
        FieldLengthGuard.requireMaxLength("产品编码", req.getProductCode(), 64);
        FieldLengthGuard.requireMaxLength("产品名称", req.getProductName(), 128);
        if (!StringUtils.hasText(req.getProductName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品名称必填");
        }
        if (!StringUtils.hasText(req.getBankName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "所属银行必填");
        }
        if (requireStatus && !StringUtils.hasText(req.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态必填");
        }
    }

    /**
     * 按银行名称解析渠道编码（不存在则建档）。
     */
    private String resolveChannel(String bankName) {
        LambdaQueryWrapper<BankChannel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BankChannel::getBankName, bankName);
        BankChannel channel = bankChannelMapper.selectOne(wrapper);
        if (channel == null) {
            channel = new BankChannel();
            channel.setChannelCode(BizIdGenerator.generate("ch", 32));
            channel.setBankName(bankName);
            channel.setStatus("ACTIVE");
            bankChannelMapper.insert(channel);
        }
        return channel.getChannelCode();
    }

    /**
     * 请求 → 实体（不含 id / bankChannelCode）。
     */
    private BankProduct toEntity(ProductSaveReq req) {
        BankProduct product = new BankProduct();
        product.setProductCode(req.getProductCode());
        product.setProductName(req.getProductName());
        product.setCustomerGroup(req.getCustomerGroup());
        product.setSource(req.getSource());
        product.setAmountMin(req.getAmountMin());
        product.setAmountMax(req.getAmountMax());
        product.setRateMin(req.getRateMin());
        product.setRateMax(req.getRateMax());
        product.setTermMin(req.getTermMin());
        product.setTermMax(req.getTermMax());
        product.setStatus(req.getStatus());
        return product;
    }
}
