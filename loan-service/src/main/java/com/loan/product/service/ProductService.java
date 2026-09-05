package com.loan.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.exception.BusinessException;
import com.loan.product.dto.ProductSaveReq;
import com.loan.product.entity.BankChannel;
import com.loan.product.entity.BankProduct;
import com.loan.product.mapper.BankChannelMapper;
import com.loan.product.mapper.BankProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    /**
     * 新增产品。
     *
     * @param req      请求
     * @param operator 操作人姓名
     * @return 产品 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(ProductSaveReq req, String operator) {
        validate(req);
        if (bankProductMapper.selectCount(new LambdaQueryWrapper<BankProduct>()
                .eq(BankProduct::getProductCode, req.getProductCode())) > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品编码已存在");
        }
        BankProduct product = toEntity(req);
        product.setBankChannelCode(resolveChannel(req.getBankName()));
        product.setCreatedBy(operator);
        bankProductMapper.insert(product);
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
     * 删除产品（按业务唯一编码）。
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
        bankProductMapper.deleteById(exist.getId());
    }

    /**
     * 校验必填项。
     */
    private void validate(ProductSaveReq req) {
        if (!StringUtils.hasText(req.getProductCode())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品编码必填");
        }
        if (!StringUtils.hasText(req.getProductName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品名称必填");
        }
        if (!StringUtils.hasText(req.getBankName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "所属银行必填");
        }
        if (!StringUtils.hasText(req.getStatus())) {
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
            channel.setChannelCode(BizIdGenerator.generate("ch"));
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
