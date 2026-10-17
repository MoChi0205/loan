package com.loan.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.product.entity.BankProductCity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 产品-服务城市关系 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface BankProductCityMapper extends BaseMapper<BankProductCity> {

    /**
     * 批量幂等写入产品城市关系。
     *
     * @param items 待写入关系
     * @return 实际新增条数
     */
    default int insertIgnoreBatch(List<BankProductCity> items) {
        if (items == null || items.isEmpty()) return 0;
        LambdaQueryWrapper<BankProductCity> query = new LambdaQueryWrapper<>();
        query.select(BankProductCity::getProductCode, BankProductCity::getProvince, BankProductCity::getCity);
        query.and(keys -> items.forEach(item -> keys.or(key -> key
                .eq(BankProductCity::getProductCode, item.getProductCode())
                .eq(BankProductCity::getProvince, item.getProvince())
                .eq(BankProductCity::getCity, item.getCity()))));
        Set<List<String>> existing = new HashSet<>();
        selectList(query).forEach(item -> existing.add(naturalKey(item)));
        int count = 0;
        for (BankProductCity item : items) {
            if (!existing.add(naturalKey(item))) continue;
            try { count += insert(item); } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                // 只吞自然键已存在的并发重复，不吞随机业务编码冲突。
                if (selectOne(new LambdaQueryWrapper<BankProductCity>().select(BankProductCity::getId)
                        .eq(BankProductCity::getProductCode, item.getProductCode())
                        .eq(BankProductCity::getProvince, item.getProvince())
                        .eq(BankProductCity::getCity, item.getCity()).last("FOR UPDATE")) == null) throw duplicate;
            }
        }
        return count;
    }

    default List<String> naturalKey(BankProductCity item) {
        return Arrays.asList(item.getProductCode(), item.getProvince(), item.getCity());
    }
}
