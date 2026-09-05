package com.loan.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.exception.BusinessException;
import com.loan.product.entity.BankProductCity;
import com.loan.product.mapper.BankProductCityMapper;
import com.loan.product.model.ProductCityQuery;
import com.loan.product.model.ProductCityUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 产品-服务城市服务：统一支持单条、批量与产品维度列表查询。
 *
 * <p>绑定写入使用批量 {@code INSERT IGNORE}，依赖业务编码与产品省市自然键唯一索引保证并发幂等。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class BankProductCityService {

    private static final int MAX_BATCH_SIZE = 100;
    private static final int BIZ_CODE_LENGTH = 16;

    private final BankProductCityMapper cityMapper;

    /** 统一组合条件分页查询产品城市关系。 */
    public PageResult<BankProductCity> page(ProductCityQuery query) {
        ProductCityQuery safeQuery = query == null ? new ProductCityQuery() : query;
        int page = safeQuery.getPage() <= 0 ? 1 : safeQuery.getPage();
        int size = safeQuery.getSize() <= 0 ? 10 : Math.min(safeQuery.getSize(), 100);
        LambdaQueryWrapper<BankProductCity> wrapper = buildQuery(safeQuery);
        wrapper.orderByDesc(BankProductCity::getCreatedAt).orderByDesc(BankProductCity::getId);
        Page<BankProductCity> result = cityMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), result.getRecords());
    }

    /** 批量绑定产品可服务城市（去重幂等、单次批量写库）。 */
    @Transactional(rollbackFor = Exception.class)
    public int bind(String productCode, List<CityItem> cities, String operator) {
        requireText(productCode, "产品编码必填");
        if (cities == null || cities.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "服务城市不能为空");
        }
        if (cities.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多绑定 " + MAX_BATCH_SIZE + " 个城市");
        }

        Map<String, CityItem> uniqueCities = new LinkedHashMap<>();
        for (CityItem item : cities) {
            if (item == null || !StringUtils.hasText(item.getCity())) {
                continue;
            }
            String province = item.getProvince() == null ? "" : item.getProvince().trim();
            String city = item.getCity().trim();
            CityItem normalized = new CityItem();
            normalized.setProvince(province);
            normalized.setCity(city);
            uniqueCities.put(province + "\u0000" + city, normalized);
        }
        if (uniqueCities.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        List<BankProductCity> toInsert = new ArrayList<>();
        for (CityItem item : uniqueCities.values()) {
            BankProductCity relation = new BankProductCity();
            relation.setProductCityCode(BizIdGenerator.generate("pcity", BIZ_CODE_LENGTH));
            relation.setProductCode(productCode.trim());
            relation.setProvince(item.getProvince());
            relation.setCity(item.getCity());
            relation.setCreatedBy(operator);
            relation.setCreatedAt(now);
            toInsert.add(relation);
        }
        return cityMapper.insertIgnoreBatch(toInsert);
    }

    /** 按业务编码查询单条产品城市关系。 */
    public BankProductCity detail(String productCityCode) {
        requireText(productCityCode, "产品城市关系编码不能为空");
        BankProductCity item = cityMapper.selectOne(new LambdaQueryWrapper<BankProductCity>()
                .eq(BankProductCity::getProductCityCode, productCityCode.trim()));
        if (item == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "产品城市关系不存在");
        }
        return item;
    }

    /** 按业务编码批量查询，结果保持请求顺序，未命中项不返回。 */
    public List<BankProductCity> batchQuery(List<String> productCityCodes) {
        List<String> codes = normalizeCodes(productCityCodes);
        if (codes.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, BankProductCity> itemMap = cityMapper.selectList(new LambdaQueryWrapper<BankProductCity>()
                        .in(BankProductCity::getProductCityCode, codes)).stream()
                .collect(Collectors.toMap(BankProductCity::getProductCityCode, item -> item, (left, right) -> left));
        return codes.stream().map(itemMap::get).filter(item -> item != null).collect(Collectors.toList());
    }

    /** 按业务编码修改产品城市关系，关系业务编码自身不可修改。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(String productCityCode, ProductCityUpdateRequest request) {
        requireText(productCityCode, "产品城市关系编码不能为空");
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "修改内容不能为空");
        }
        requireText(request.getProductCode(), "产品编码必填");
        requireText(request.getProvince(), "省名称必填");
        requireText(request.getCity(), "市名称必填");

        detail(productCityCode);
        try {
            cityMapper.update(null, new LambdaUpdateWrapper<BankProductCity>()
                    .eq(BankProductCity::getProductCityCode, productCityCode.trim())
                    .set(BankProductCity::getProductCode, request.getProductCode().trim())
                    .set(BankProductCity::getProvince, request.getProvince().trim())
                    .set(BankProductCity::getCity, request.getCity().trim()));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该产品已绑定相同的省市");
        }
    }

    /** 按业务编码解绑单条城市。 */
    @Transactional(rollbackFor = Exception.class)
    public void unbind(String productCityCode) {
        requireText(productCityCode, "产品城市关系编码不能为空");
        int deleted = cityMapper.delete(new LambdaQueryWrapper<BankProductCity>()
                .eq(BankProductCity::getProductCityCode, productCityCode.trim()));
        if (deleted == 0) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "产品城市关系不存在");
        }
    }

    /** 查询产品绑定的城市列表。 */
    public List<BankProductCity> listByProduct(String productCode) {
        requireText(productCode, "产品编码不能为空");
        return cityMapper.selectList(new LambdaQueryWrapper<BankProductCity>()
                .eq(BankProductCity::getProductCode, productCode.trim())
                .orderByAsc(BankProductCity::getProvince)
                .orderByAsc(BankProductCity::getCity));
    }

    /** 按申请城市查可服务产品编码集合（匹配筛选用，精确匹配市名）。 */
    public List<String> listProductCodesByCity(String city) {
        if (!StringUtils.hasText(city)) {
            return new ArrayList<>();
        }
        return cityMapper.selectList(new LambdaQueryWrapper<BankProductCity>()
                        .eq(BankProductCity::getCity, city.trim()))
                .stream().map(BankProductCity::getProductCode).distinct().collect(Collectors.toList());
    }

    /** 构建统一组合查询条件，供分页等入口复用。 */
    private LambdaQueryWrapper<BankProductCity> buildQuery(ProductCityQuery query) {
        LambdaQueryWrapper<BankProductCity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getProductCode())) {
            wrapper.eq(BankProductCity::getProductCode, query.getProductCode().trim());
        }
        if (StringUtils.hasText(query.getProvince())) {
            wrapper.eq(BankProductCity::getProvince, query.getProvince().trim());
        }
        if (StringUtils.hasText(query.getCity())) {
            wrapper.eq(BankProductCity::getCity, query.getCity().trim());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(item -> item.like(BankProductCity::getProvince, keyword)
                    .or().like(BankProductCity::getCity, keyword));
        }
        return wrapper;
    }

    /** 归一化并限制批量业务编码。 */
    private List<String> normalizeCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyList();
        }
        if (codes.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多查询 " + MAX_BATCH_SIZE + " 个产品城市关系");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String code : codes) {
            if (StringUtils.hasText(code)) {
                result.add(code.trim());
            }
        }
        return new ArrayList<>(result);
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, message);
        }
    }

    /** 城市项（绑定入参）。 */
    @lombok.Data
    public static class CityItem {
        /** 省名称 */
        private String province;
        /** 市名称 */
        private String city;
    }
}
