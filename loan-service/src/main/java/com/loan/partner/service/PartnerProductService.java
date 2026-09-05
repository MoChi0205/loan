package com.loan.partner.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.partner.dto.PartnerProductSaveReq;
import com.loan.partner.entity.PartnerProduct;
import com.loan.partner.mapper.PartnerProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 合作库上架服务（P0-5）：CRUD + 续签 + 状态流转 + 到期 job 逻辑。
 *
 * <p>状态机：ACTIVE 上架 → EXPIRING 临期（T-30/T-7 预警）→ EXPIRED 到期 / OFFLINE 手动下架；
 * 续签（renew）更新 cooperateUntil 并回到 ACTIVE；
 * 对客（小程序/报告）只暴露 {@link #listActive()}：status=ACTIVE 且 cooperate_until &gt; now。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class PartnerProductService {

    /** 状态常量 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRING = "EXPIRING";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_OFFLINE = "OFFLINE";

    /** T-30 预警窗口（天） */
    private static final long WARN_DAYS = 30;

    private final PartnerProductMapper partnerProductMapper;

    /**
     * 分页查询（管理端）。
     *
     * @param status 状态（可选）
     * @param page   页码（从 1 起）
     * @param size   每页大小
     * @return 分页结果
     */
    public PageResult<PartnerProduct> page(String status, int page, int size) {
        LambdaQueryWrapper<PartnerProduct> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(PartnerProduct::getStatus, status);
        }
        wrapper.orderByDesc(PartnerProduct::getCreatedAt);
        Page<PartnerProduct> result = partnerProductMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), result.getRecords());
    }

    /**
     * 新增上架。
     *
     * @param req      请求
     * @param operator 操作人姓名
     * @return 银行产品业务编码
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(PartnerProductSaveReq req, String operator) {
        if (req == null || !StringUtils.hasText(req.getBankProductCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "银行产品业务编码必填");
        }
        if (req.getCooperateUntil() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "合作库有效期必填");
        }
        if (partnerProductMapper.selectCount(new LambdaQueryWrapper<PartnerProduct>()
                .eq(PartnerProduct::getBankProductCode, req.getBankProductCode())) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该银行产品已上架合作库");
        }
        PartnerProduct product = new PartnerProduct();
        product.setBankProductCode(req.getBankProductCode());
        product.setCooperateUntil(req.getCooperateUntil());
        product.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : STATUS_ACTIVE);
        product.setCreatedBy(StringUtils.hasText(operator) ? operator : "system");
        product.setUpdatedBy(StringUtils.hasText(operator) ? operator : "system");
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        partnerProductMapper.insert(product);
        return product.getBankProductCode();
    }

    /**
     * 续签：更新合作库有效期，状态回到 ACTIVE。
     *
     * @param bankProductCode 银行产品业务编码
     * @param cooperateUntil  新的有效期
     * @param operator        操作人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void renew(String bankProductCode, LocalDateTime cooperateUntil, String operator) {
        PartnerProduct product = getByCode(bankProductCode);
        if (cooperateUntil == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "合作库有效期必填");
        }
        product.setCooperateUntil(cooperateUntil);
        product.setStatus(STATUS_ACTIVE);
        product.setUpdatedBy(StringUtils.hasText(operator) ? operator : "system");
        product.setUpdatedAt(LocalDateTime.now());
        partnerProductMapper.updateById(product);
    }

    /**
     * 状态流转（手动下架/上架）。
     *
     * @param bankProductCode 银行产品业务编码
     * @param status          目标状态（ACTIVE/OFFLINE；EXPIRED 需走续签）
     * @param operator        操作人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String bankProductCode, String status, String operator) {
        if (!STATUS_ACTIVE.equals(status) && !STATUS_OFFLINE.equals(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持手动下架(OFFLINE)/上架(ACTIVE)");
        }
        PartnerProduct product = getByCode(bankProductCode);
        product.setStatus(status);
        product.setUpdatedBy(StringUtils.hasText(operator) ? operator : "system");
        product.setUpdatedAt(LocalDateTime.now());
        partnerProductMapper.updateById(product);
    }

    /**
     * 对客可见合作产品：status=ACTIVE 且 cooperate_until &gt; now。
     *
     * @return 合作产品列表
     */
    public List<PartnerProduct> listActive() {
        LocalDateTime now = LocalDateTime.now();
        return partnerProductMapper.selectList(new LambdaQueryWrapper<PartnerProduct>()
                .eq(PartnerProduct::getStatus, STATUS_ACTIVE)
                .gt(PartnerProduct::getCooperateUntil, now)
                .orderByAsc(PartnerProduct::getCooperateUntil));
    }

    /**
     * 到期 job：EXPIRING 且已到期 → EXPIRED。
     *
     * @return 下架数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int expireOverdue() {
        List<PartnerProduct> expired = partnerProductMapper.selectList(new LambdaQueryWrapper<PartnerProduct>()
                .eq(PartnerProduct::getStatus, STATUS_EXPIRING)
                .le(PartnerProduct::getCooperateUntil, LocalDateTime.now()));
        if (expired.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (PartnerProduct product : expired) {
            product.setStatus(STATUS_EXPIRED);
            product.setUpdatedBy("job");
            product.setUpdatedAt(now);
            partnerProductMapper.updateById(product);
        }
        return expired.size();
    }

    /**
     * 到期 job：ACTIVE 且进入 T-30 预警窗口 → EXPIRING。
     *
     * <p>T-7 预警为 T-30 的子集（7 ≤ 30），一次扫描同时覆盖；预警集合见 {@link #listExpiring()}。
     *
     * @return 转入临期数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int markExpiring() {
        LocalDateTime now = LocalDateTime.now();
        List<PartnerProduct> warn = partnerProductMapper.selectList(new LambdaQueryWrapper<PartnerProduct>()
                .eq(PartnerProduct::getStatus, STATUS_ACTIVE)
                .le(PartnerProduct::getCooperateUntil, now.plusDays(WARN_DAYS)));
        if (warn.isEmpty()) {
            return 0;
        }
        for (PartnerProduct product : warn) {
            product.setStatus(STATUS_EXPIRING);
            product.setUpdatedBy("job");
            product.setUpdatedAt(now);
            partnerProductMapper.updateById(product);
        }
        return warn.size();
    }

    /**
     * 到期预警集合（EXPIRING 临期产品，含 T-30/T-7，按到期时间升序）。
     *
     * @return 预警列表
     */
    public List<PartnerProduct> listExpiring() {
        return partnerProductMapper.selectList(new LambdaQueryWrapper<PartnerProduct>()
                .eq(PartnerProduct::getStatus, STATUS_EXPIRING)
                .orderByAsc(PartnerProduct::getCooperateUntil));
    }

    /**
     * 按银行产品业务编码查询。
     *
     * @param bankProductCode 业务编码
     * @return 合作产品
     */
    public PartnerProduct getByCode(String bankProductCode) {
        if (!StringUtils.hasText(bankProductCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "银行产品业务编码必填");
        }
        PartnerProduct product = partnerProductMapper.selectOne(new LambdaQueryWrapper<PartnerProduct>()
                .eq(PartnerProduct::getBankProductCode, bankProductCode).last("limit 1"));
        if (product == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "合作库产品不存在");
        }
        return product;
    }
}
