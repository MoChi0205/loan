package com.loan.client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.client.entity.ClientProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户档案 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface ClientProfileMapper extends BaseMapper<ClientProfile> {

    /**
     * 按微信 openid SHA-256 哈希等值查询客户档案（方案 A 登录链路）。
     *
     * @param wxOpenidHash openid 哈希
     * @return 客户档案，不存在返回 null
     */
    default ClientProfile selectByWxOpenidHash(String wxOpenidHash) {
        if (wxOpenidHash == null || wxOpenidHash.isEmpty()) {
            return null;
        }
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getWxOpenidHash, wxOpenidHash)
                .last("limit 1"));
    }
}
