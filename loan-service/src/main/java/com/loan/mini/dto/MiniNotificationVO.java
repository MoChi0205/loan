package com.loan.mini.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 小程序消息中心条目（展示态）。
 *
 * <p><b>合规（D68 / 02-红线 #7）</b>：只暴露「来源 + 事项 + 时间 + 未读」四要素，
 * 不含 {@code notificationId} / {@code relatedId} 等任何业务单号，客户端零单号。
 *
 * <p>「来源」由通知类型映射而来（如 系统 / 审核 / 提醒 / 服务）；对外表述遵守
 * 「审批 → 审核」口径。时间由服务端归并为「今天 HH:mm / 昨天 HH:mm / MM-dd HH:mm」，
 * 客户端直接渲染，避免各端重复实现相对时间逻辑。
 *
 * @author loan-platform
 */
@Data
public class MiniNotificationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源标签（通知类型映射）。 */
    private String source;

    /** 事项（通知标题；标题缺失时回退内容）。 */
    private String matter;

    /** 展示时间（服务端归并后的相对时间文案）。 */
    private String time;

    /** 创建时间（ISO-8601 字符串，供排序或后续二次展示）。 */
    private String createdAt;

    /** 是否未读。 */
    private Boolean unread;
}
