package com.loan.plan.model;

import lombok.Data;

/**
 * 渠道名单修改请求。
 *
 * <p>名单业务编码由路径参数提供且不可修改。支持局部字段修改；名单键未传时保留原值，
 * 传入新名单键时仍由后端按客群统一归一化。
 *
 * @author loan-platform
 */
@Data
public class ChannelUserListUpdateRequest {

    /** 渠道编码（可选） */
    private String channelCode;

    /** 客群（PERSONAL/ENTERPRISE，可选） */
    private String customerGroup;

    /** 名单类型（LOCAL_WHITE/LOCAL_BLACK，可选） */
    private String listType;

    /** 原始名单键：手机号或统一社会信用代码（可选） */
    private String listKey;
}
