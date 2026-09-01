package com.loan.plan.model;

import lombok.Data;

import java.util.List;

/**
 * 渠道名单批量查询/删除请求。
 *
 * @author loan-platform
 */
@Data
public class ChannelUserListBatchRequest {

    /** 名单记录业务编码集合 */
    private List<String> listCodes;
}
