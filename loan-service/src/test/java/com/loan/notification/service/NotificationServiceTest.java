package com.loan.notification.service;

import com.loan.notification.dto.NotificationReq;
import com.loan.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 站内通知并发幂等回归。 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private StringRedisTemplate redisTemplate;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationMapper, redisTemplate);
    }

    @Test
    @DisplayName("sendOnce：并发唯一键冲突视为幂等成功而非系统异常")
    void sendOnce_handlesDuplicateKey() {
        when(notificationMapper.selectCount(any())).thenReturn(0L);
        when(notificationMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        NotificationReq req = new NotificationReq();
        req.setUserNo("channel-user");
        req.setType("PRODUCT_APPROVAL");
        req.setTitle("审批结果");
        req.setRelatedId("papr001");

        assertFalse(service.sendOnce(req));

        verify(notificationMapper).insert(any());
    }
}
