package com.loan.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.api.dto.product.ProductDTO;
import com.loan.partner.entity.PartnerProduct;
import com.loan.partner.service.PartnerProductService;
import com.loan.product.entity.BankChannel;
import com.loan.product.entity.BankProduct;
import com.loan.product.entity.BankProductCity;
import com.loan.product.mapper.BankChannelMapper;
import com.loan.product.mapper.BankProductMapper;
import com.loan.product.mapper.BankProductCityMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 产品库查询服务（供 ProductFacade 与后续 HTTP Controller 复用）。
 *
 * @author loan-platform
 */
@Service
public class ProductQueryService {

    @Resource
    private BankProductMapper bankProductMapper;

    @Resource
    private BankChannelMapper bankChannelMapper;

    @Resource
    private BankProductCityMapper bankProductCityMapper;

    @Resource
    private PartnerProductService partnerProductService;

    /**
     * 分页查询全量库产品（含银行名称）。
     *
     * @param customerGroup 客群（可为空）
     * @param page          页码（从 1 起）
     * @param size          每页大小
     * @return 产品分页结果
     */
    public PageResult<ProductDTO> queryProducts(String customerGroup, int page, int size) {
        LambdaQueryWrapper<BankProduct> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BankProduct::getStatus, "APPROVED");
        if (customerGroup != null && !customerGroup.isEmpty()) {
            wrapper.eq(BankProduct::getCustomerGroup, customerGroup);
        }
        wrapper.orderByDesc(BankProduct::getUpdatedAt);

        Page<BankProduct> result = bankProductMapper.selectPage(new Page<>(page, size), wrapper);

        // 批量查银行渠道，映射 bankChannelCode → bankName
        List<String> channelCodes = result.getRecords().stream()
                .map(BankProduct::getBankChannelCode)
                .filter(code -> code != null && !code.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        Map<String, BankChannel> channelMap = channelCodes.isEmpty()
                ? java.util.Collections.emptyMap()
                : bankChannelMapper.selectList(
                        new LambdaQueryWrapper<BankChannel>().in(BankChannel::getChannelCode, channelCodes)).stream()
                        .collect(Collectors.toMap(BankChannel::getChannelCode, Function.identity()));

        Map<String, String> cityMap = cityMap(result.getRecords());
        List<ProductDTO> records = result.getRecords().stream()
                .map(p -> toDTO(p, channelMap.get(p.getBankChannelCode()), cityMap.get(p.getProductCode())))
                .collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 实体 → DTO 转换。
     */
    private ProductDTO toDTO(BankProduct p, BankChannel channel, String serviceCities) {
        ProductDTO dto = new ProductDTO();
        dto.setProductCode(p.getProductCode());
        dto.setBankName(channel != null ? channel.getBankName() : null);
        dto.setProductName(p.getProductName());
        dto.setCustomerGroup(p.getCustomerGroup());
        dto.setAmountRange(formatWan(p.getAmountMin()) + " - " + formatWan(p.getAmountMax()));
        dto.setRateRange(formatPercent(p.getRateMin()) + " - " + formatPercent(p.getRateMax()));
        dto.setTermRange((p.getTermMin() == null ? "-" : p.getTermMin() + " 个月") + " - "
                + (p.getTermMax() == null ? "-" : p.getTermMax() + " 个月"));
        dto.setSource(p.getSource());
        dto.setStatus(p.getStatus());
        dto.setBankChannelCode(p.getBankChannelCode());
        dto.setAmountMin(p.getAmountMin());
        dto.setAmountMax(p.getAmountMax());
        dto.setRateMin(p.getRateMin());
        dto.setRateMax(p.getRateMax());
        dto.setTermMin(p.getTermMin());
        dto.setTermMax(p.getTermMax());
        dto.setCreatedBy(p.getCreatedBy());
        dto.setCreatedAt(p.getCreatedAt());
        dto.setServiceCities(serviceCities);
        return dto;
    }

    /** 一页产品的服务城市一次批量查询，避免产品列表 N+1。 */
    private Map<String, String> cityMap(List<BankProduct> products) {
        List<String> productCodes = products == null ? Collections.emptyList() : products.stream()
                .map(BankProduct::getProductCode).filter(StringUtils::hasText)
                .distinct().collect(Collectors.toList());
        if (productCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<BankProductCity> cities = bankProductCityMapper.selectList(
                new LambdaQueryWrapper<BankProductCity>().in(BankProductCity::getProductCode, productCodes));
        if (cities == null || cities.isEmpty()) {
            return Collections.emptyMap();
        }
        return cities.stream().filter(c -> StringUtils.hasText(c.getProductCode()) && StringUtils.hasText(c.getCity()))
                .collect(Collectors.groupingBy(BankProductCity::getProductCode,
                        Collectors.mapping(BankProductCity::getCity,
                                Collectors.joining(", "))));
    }

    /**
     * 金额转「万」描述（元 → 万，保留 0 位小数）。
     */
    private String formatWan(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        return amount.divide(new BigDecimal("10000"), 0, BigDecimal.ROUND_HALF_UP).toPlainString() + " 万";
    }

    /**
     * 利率转百分比描述。
     */
    private String formatPercent(BigDecimal rate) {
        if (rate == null) {
            return "-";
        }
        return rate.multiply(new BigDecimal("100")).setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString() + "%";
    }

    /**
     * 管理端分页查询（不过滤 APPROVED，支持状态 / 名称 / 银行 / 库类型筛选）。
     *
     * @param customerGroup   客群（可选）
     * @param status          状态（可选）
     * @param productName     产品名称（可选，模糊）
     * @param bankName        所属银行（可选，模糊）
     * @param scope           库类型：all=全量库（默认），cooperate=合作库（已挂渠道策略的产品）
     * @param bankChannelCode 强制所属银行渠道编码（渠道登录本行硬隔离，T11/D28；员工传 null 不限制）
     * @param page            页码（从 1 起）
     * @param size            每页大小
     * @return 产品分页结果
     */
    public PageResult<ProductDTO> queryProductsForAdmin(String customerGroup, String status,
                                                        String productName, String bankName, String city, String scope,
                                                        String bankChannelCode, int page, int size) {
        LambdaQueryWrapper<BankProduct> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(customerGroup)) {
            wrapper.eq(BankProduct::getCustomerGroup, customerGroup);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(BankProduct::getStatus, status);
        }
        if (StringUtils.hasText(productName)) {
            wrapper.like(BankProduct::getProductName, productName);
        }
        // 渠道数据范围硬隔离（T11/D28）：仅本银行渠道产品，禁止全量
        if (StringUtils.hasText(bankChannelCode)) {
            wrapper.eq(BankProduct::getBankChannelCode, bankChannelCode);
        }
        if ("cooperate".equalsIgnoreCase(scope)) {
            // 合作库：读 t_partner_product（status=ACTIVE 且 cooperate_until>now）的银行产品业务编码过滤全量库
            List<String> partnerCodes = partnerProductService.listActive().stream()
                    .map(PartnerProduct::getBankProductCode)
                    .filter(code -> code != null && !code.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
            if (partnerCodes.isEmpty()) {
                return PageResult.build(page, size, 0, Collections.emptyList());
            }
            wrapper.in(BankProduct::getProductCode, partnerCodes);
        }
        if (StringUtils.hasText(bankName)) {
            List<String> channelCodes = bankChannelMapper.selectList(
                            new LambdaQueryWrapper<BankChannel>().like(BankChannel::getBankName, bankName))
                    .stream().map(BankChannel::getChannelCode).collect(Collectors.toList());
            if (channelCodes.isEmpty()) {
                return PageResult.build(page, size, 0, Collections.emptyList());
            }
            wrapper.in(BankProduct::getBankChannelCode, channelCodes);
        }
        // 城市筛选：通过产品-城市关联表过滤
        if (StringUtils.hasText(city)) {
            List<String> cityProductCodes = bankProductCityMapper.selectList(
                            new LambdaQueryWrapper<BankProductCity>().like(BankProductCity::getCity, city))
                    .stream().map(BankProductCity::getProductCode).distinct().collect(Collectors.toList());
            if (cityProductCodes.isEmpty()) {
                return PageResult.build(page, size, 0, Collections.emptyList());
            }
            wrapper.in(BankProduct::getProductCode, cityProductCodes);
        }
        wrapper.orderByDesc(BankProduct::getUpdatedAt);

        Page<BankProduct> result = bankProductMapper.selectPage(new Page<>(page, size), wrapper);

        List<String> channelCodes = result.getRecords().stream()
                .map(BankProduct::getBankChannelCode)
                .filter(code -> code != null && !code.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        Map<String, BankChannel> channelMap = channelCodes.isEmpty()
                ? Collections.emptyMap()
                : bankChannelMapper.selectList(
                        new LambdaQueryWrapper<BankChannel>().in(BankChannel::getChannelCode, channelCodes)).stream()
                        .collect(Collectors.toMap(BankChannel::getChannelCode, Function.identity()));

        Map<String, String> cityMap = cityMap(result.getRecords());
        List<ProductDTO> records = result.getRecords().stream()
                .map(p -> toDTO(p, channelMap.get(p.getBankChannelCode()), cityMap.get(p.getProductCode())))
                .collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }
}
