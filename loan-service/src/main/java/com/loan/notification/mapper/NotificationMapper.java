package com.loan.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 站内消息通知 Mapper。
 *
 * @author loan-platform
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 标记单条已读（按业务 ID）。
     *
     * @param notificationId 通知业务 ID
     * @return 更新行数
     */
    @Update("UPDATE t_notification SET read_status = 1, read_at = NOW() "
            + "WHERE notification_id = #{notificationId} AND read_status = 0")
    int markRead(@Param("notificationId") String notificationId);

    /**
     * 标记当前用户全部已读。
     *
     * @param userNo 接收人
     * @return 更新行数
     */
    @Update("UPDATE t_notification SET read_status = 1, read_at = NOW() "
            + "WHERE user_no = #{userNo} AND read_status = 0")
    int markAllRead(@Param("userNo") String userNo);
}
