package com.loan.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.util.BizIdGenerator;
import com.loan.notification.dto.NotificationReq;
import com.loan.notification.entity.Notification;
import com.loan.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内消息通知服务（参考 tse NotificationServiceImpl）。
 *
 * <p>未读数用 Redis 短缓存（45s），减轻前端 60s 轮询对 DB 的压力；通知业务 ID 遵循「小写前缀 + 32 位随机」。
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 未读数缓存 key 前缀 */
    private static final String UNREAD_KEY_PREFIX = "loan:notify:unread:";

    private final NotificationMapper notificationMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 发送通知（通知业务 ID = noti + 32 位随机）。
     *
     * @param req 通知请求
     * @return 通知实体
     */
    public Notification send(NotificationReq req) {
        if (req == null || !StringUtils.hasText(req.getUserNo()) || !StringUtils.hasText(req.getTitle())) {
            throw new IllegalArgumentException("接收人与标题必填");
        }
        Notification po = new Notification();
        po.setNotificationId(BizIdGenerator.generate("noti"));
        po.setUserNo(req.getUserNo());
        po.setType(req.getType());
        po.setTitle(req.getTitle());
        po.setContent(req.getContent());
        po.setRelatedId(req.getRelatedId());
        po.setReadStatus(0);
        po.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(po);
        evictUnreadCache(req.getUserNo());
        log.info("通知已发送: userNo={}, type={}, title={}", req.getUserNo(), req.getType(), req.getTitle());
        return po;
    }

    /**
     * 同类型 + 关联 ID 是否已通知（去重）。
     *
     * @param type      通知类型
     * @param relatedId 关联业务 ID
     * @return true 已存在
     */
    public boolean existsByTypeAndRelatedId(String type, String relatedId) {
        if (!StringUtils.hasText(type) || !StringUtils.hasText(relatedId)) {
            return false;
        }
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getType, type)
                .eq(Notification::getRelatedId, relatedId));
        return count != null && count > 0;
    }

    /**
     * 分页查询当前用户通知。
     *
     * @param userNo 接收人
     * @param page   页码
     * @param size   每页大小
     * @return 通知分页
     */
    public PageResult<Notification> page(String userNo, int page, int size) {
        Page<Notification> result = notificationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserNo, userNo)
                        .orderByDesc(Notification::getCreatedAt));
        return PageResult.build(page, size, result.getTotal(), result.getRecords());
    }

    /**
     * 未读数（Redis 缓存 45s）。
     *
     * @param userNo 接收人
     * @return 未读数
     */
    public int countUnread(String userNo) {
        if (!StringUtils.hasText(userNo)) {
            return 0;
        }
        String key = UNREAD_KEY_PREFIX + userNo;
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Integer.parseInt(cached);
        }
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserNo, userNo)
                .eq(Notification::getReadStatus, 0));
        int unread = count == null ? 0 : count.intValue();
        stringRedisTemplate.opsForValue().set(key, String.valueOf(unread), Duration.ofSeconds(45));
        return unread;
    }

    /**
     * 标记单条已读。
     *
     * @param notificationId 通知业务 ID
     * @return 更新行数
     */
    public int markAsRead(String notificationId) {
        Notification po = notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getNotificationId, notificationId));
        int updated = notificationMapper.markRead(notificationId);
        if (po != null) {
            evictUnreadCache(po.getUserNo());
        }
        return updated;
    }

    /**
     * 全部标记已读。
     *
     * @param userNo 接收人
     * @return 更新行数
     */
    public int markAllAsRead(String userNo) {
        int updated = notificationMapper.markAllRead(userNo);
        evictUnreadCache(userNo);
        return updated;
    }

    /**
     * 删除当前用户全部通知。
     *
     * @param userNo 接收人
     * @return 删除行数
     */
    public int deleteAll(String userNo) {
        int deleted = notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserNo, userNo));
        evictUnreadCache(userNo);
        return deleted;
    }

    /**
     * 批量删除（仅删归属本人的）。
     *
     * @param userNo          接收人
     * @param notificationIds 通知业务 ID 列表
     * @return 删除行数
     */
    public int deleteBatch(String userNo, List<String> notificationIds) {
        if (!StringUtils.hasText(userNo) || notificationIds == null || notificationIds.isEmpty()) {
            return 0;
        }
        int deleted = notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserNo, userNo)
                .in(Notification::getNotificationId, notificationIds));
        evictUnreadCache(userNo);
        return deleted;
    }

    /** 清未读数缓存。 */
    private void evictUnreadCache(String userNo) {
        if (StringUtils.hasText(userNo)) {
            stringRedisTemplate.delete(UNREAD_KEY_PREFIX + userNo);
        }
    }
}
