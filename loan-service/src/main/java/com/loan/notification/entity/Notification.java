package com.loan.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内消息通知实体（t_notification，参考 tse NotificationPo）。
 *
 * @author loan-platform
 */
@Data
@TableName("t_notification")
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通知类型：系统通知 */
    public static final String TYPE_SYSTEM_NOTICE = "SYSTEM_NOTICE";
    /** 通知类型：线索回收预警 */
    public static final String TYPE_LEAD_RECYCLE_WARN = "LEAD_RECYCLE_WARN";
    /** 通知类型：产品审核结果 */
    public static final String TYPE_PRODUCT_APPROVAL = "PRODUCT_APPROVAL";
    /** 通知类型：服务工单 */
    public static final String TYPE_SERVICE_ORDER = "SERVICE_ORDER";

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 通知业务 ID（noti + 32 位随机） */
    private String notificationId;

    /** 接收人（员工工号 staff_code / 渠道账号 / 客户编号） */
    private String userNo;

    /** 通知类型 */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 关联业务 ID（如线索 lead_no / 审核 approval_no） */
    private String relatedId;

    /** 已读状态（0 未读 / 1 已读） */
    private Integer readStatus;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 阅读时间 */
    private LocalDateTime readAt;
}
