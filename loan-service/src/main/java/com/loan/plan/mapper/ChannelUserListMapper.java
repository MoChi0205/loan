package com.loan.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.plan.entity.ChannelUserList;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 渠道本地白/黑名单 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ChannelUserListMapper extends BaseMapper<ChannelUserList> {

    /**
     * 批量幂等写入名单，依赖业务编码和自然业务键唯一索引防并发重复。
     *
     * @param items 待写入名单
     * @return 实际新增条数
     */
    default int insertIgnoreBatch(List<ChannelUserList> items) {
        if (items == null || items.isEmpty()) return 0;
        // 一次预查一批自然键，避免每条写入前都往返云数据库。
        LambdaQueryWrapper<ChannelUserList> query = new LambdaQueryWrapper<>();
        query.select(ChannelUserList::getChannelCode, ChannelUserList::getCustomerGroup,
                ChannelUserList::getListType, ChannelUserList::getListKey);
        query.and(keys -> items.forEach(item -> keys.or(key -> key
                .eq(ChannelUserList::getChannelCode, item.getChannelCode())
                .eq(ChannelUserList::getCustomerGroup, item.getCustomerGroup())
                .eq(ChannelUserList::getListType, item.getListType())
                .eq(ChannelUserList::getListKey, item.getListKey()))));
        Set<List<String>> existing = new HashSet<>();
        selectList(query).forEach(item -> existing.add(naturalKey(item)));
        int count = 0;
        for (ChannelUserList item : items) {
            if (!existing.add(naturalKey(item))) continue;
            try { count += insert(item); } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                // 只吞自然键已存在的并发重复，不吞随机业务编码冲突。
                // FOR UPDATE 当前读可看见并发事务刚提交的自然键，不能沿用批量预查的旧快照。
                if (selectOne(new LambdaQueryWrapper<ChannelUserList>().select(ChannelUserList::getId)
                        .eq(ChannelUserList::getChannelCode, item.getChannelCode())
                        .eq(ChannelUserList::getCustomerGroup, item.getCustomerGroup())
                        .eq(ChannelUserList::getListType, item.getListType())
                        .eq(ChannelUserList::getListKey, item.getListKey()).last("FOR UPDATE")) == null) throw duplicate;
            }
        }
        return count;
    }

    default List<String> naturalKey(ChannelUserList item) {
        return Arrays.asList(item.getChannelCode(), item.getCustomerGroup(), item.getListType(), item.getListKey());
    }
}
