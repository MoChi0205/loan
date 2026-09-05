package com.loan.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.plan.entity.ChannelUserList;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
    @Insert({"<script>",
            "INSERT IGNORE INTO t_channel_user_list",
            "(list_code, channel_code, customer_group, list_type, list_key, created_by, created_at) VALUES",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.listCode}, #{item.channelCode}, #{item.customerGroup}, #{item.listType},",
            " #{item.listKey}, #{item.createdBy}, #{item.createdAt})",
            "</foreach>",
            "</script>"})
    int insertIgnoreBatch(@Param("items") List<ChannelUserList> items);
}
