package com.loan.notification.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.loan.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
    default int markRead(String notificationId) {
        return update(null, unread().eq(Notification::getNotificationId, notificationId));
    }
    default int markReadForUser(String notificationId, String userNo) {
        return update(null, unread().eq(Notification::getNotificationId, notificationId).eq(Notification::getUserNo, userNo));
    }
    default int markAllRead(String userNo) {
        return update(null, unread().eq(Notification::getUserNo, userNo));
    }
    default LambdaUpdateWrapper<Notification> unread() {
        return new LambdaUpdateWrapper<Notification>().eq(Notification::getReadStatus, 0)
            .set(Notification::getReadStatus, 1).set(Notification::getReadAt, LocalDateTime.now());
    }
}
