package com.loan.serviceops.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 渠道视角固定不开放内部服务名单，不承载任何客户或员工数据。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelServiceAccessDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean available;
    private String message;

    public static ChannelServiceAccessDTO denied() {
        return new ChannelServiceAccessDTO(false, "渠道账号不可访问公司内部服务名单");
    }
}
