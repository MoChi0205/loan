package com.loan.notification.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 通知发送请求（参考 tse NotificationReq）。
 *
 * @author loan-platform
 */
@Data
public class NotificationReq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 接收人（员工工号 staff_code / 渠道账号 / 客户编号） */
    private String userNo;

    /** 通知类型 */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 关联业务 ID */
    private String relatedId;
}
