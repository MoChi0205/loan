package com.loan.plan.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道本地白/黑名单实体（t_channel_user_list）。
 *
 * <p>名单键语义由客群决定：PERSONAL=手机号 MD5(32hex)；ENTERPRISE=统一社会信用代码(18位)。
 * 命中白名单放行、命中黑名单拒绝（全局前置风控模块引用）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_channel_user_list")
public class ChannelUserList {

    /** 主键 ID */
    @JsonIgnore
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 名单记录业务编码（culist + 小写字母数字，总长 16） */
    private String listCode;

    /** 渠道编码 */
    private String channelCode;

    /** 客群（PERSONAL/ENTERPRISE，决定 list_key 语义） */
    private String customerGroup;

    /** 名单类型（LOCAL_WHITE/LOCAL_BLACK） */
    private String listType;

    /** 名单键：PERSONAL=手机号MD5；ENTERPRISE=统一社会信用代码 */
    private String listKey;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
