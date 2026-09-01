package com.loan.notification.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.notification.dto.NotificationReq;
import com.loan.notification.entity.Notification;
import com.loan.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站内消息通知 HTTP 接口（参考 tse NotificationController）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 分页查询我的通知（含未读数）。
     *
     * @param page   页码
     * @param size   每页大小
     * @param userNo 当前用户工号
     * @return 通知列表 + 未读数
     */
    @GetMapping("/mine")
    public Result<Map<String, Object>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser("userNo") String userNo) {
        PageResult<Notification> list = notificationService.page(userNo, page, size);
        int unreadCount = notificationService.countUnread(userNo);
        Map<String, Object> data = new HashMap<>(4);
        data.put("records", list.getRecords());
        data.put("total", list.getTotal());
        data.put("unreadCount", unreadCount);
        return Result.ok(data);
    }

    /**
     * 未读数（轮询）。
     *
     * @param userNo 当前用户工号
     * @return 未读数
     */
    @GetMapping("/mine/unread-count")
    public Result<Integer> unreadCount(@CurrentUser("userNo") String userNo) {
        return Result.ok(notificationService.countUnread(userNo));
    }

    /**
     * 标记单条已读。
     *
     * @param notificationId 通知业务 ID
     * @return 更新行数
     */
    @PostMapping("/{notificationId}/read")
    public Result<Integer> markAsRead(@PathVariable String notificationId) {
        return Result.ok(notificationService.markAsRead(notificationId));
    }

    /**
     * 全部标记已读。
     *
     * @param userNo 当前用户工号
     * @return 更新行数
     */
    @PostMapping("/mine/read-all")
    public Result<Integer> markAllAsRead(@CurrentUser("userNo") String userNo) {
        return Result.ok(notificationService.markAllAsRead(userNo));
    }

    /**
     * 删除全部。
     *
     * @param userNo 当前用户工号
     * @return 删除行数
     */
    @PostMapping("/mine/delete-all")
    public Result<Integer> deleteAll(@CurrentUser("userNo") String userNo) {
        return Result.ok(notificationService.deleteAll(userNo));
    }

    /**
     * 批量删除（仅删归属本人的）。
     *
     * @param body  { notificationIds: [...] }
     * @param userNo 当前用户工号
     * @return 删除行数
     */
    @PostMapping("/mine/delete-batch")
    public Result<Integer> deleteBatch(@RequestBody Map<String, List<String>> body,
                                       @CurrentUser("userNo") String userNo) {
        return Result.ok(notificationService.deleteBatch(userNo, body.get("notificationIds")));
    }

    /**
     * 发送通知（内部/管理端触发，如线索回收预警）。
     *
     * @param req 通知请求
     * @return 通知实体
     */
    @PostMapping("/send")
    public Result<Notification> send(@RequestBody NotificationReq req) {
        return Result.ok(notificationService.send(req));
    }
}
