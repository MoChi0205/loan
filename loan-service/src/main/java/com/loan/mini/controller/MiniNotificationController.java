package com.loan.mini.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.context.CurrentUser;
import com.loan.mini.dto.MiniNotificationVO;
import com.loan.notification.entity.Notification;
import com.loan.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 小程序端：站内消息中心。
 *
 * <p><b>为什么单独开一个 mini 端 Controller</b>：网关按「方法 + 路径」索引接口权限，
 * 且用户类型放行规则按 api_key 前缀划分——{@code CUSTOMER} 只放行 {@code mini:} 前缀
 * （{@link com.loan.apiperm.service.ApiPermissionService} 的 typeRules）。
 * 既有 {@code /api/notification/**}（api_key 前缀 {@code notification:}）客户角色不可达，
 * 故小程序侧统一走 {@code /api/mini/notification/**}，与
 * {@link MiniDashboardController} 同一模式：控制器由启动期自动登记进接口权限表。
 *
 * <p><b>数据范围</b>：一律按当前登录用户 {@code userNo} 收口，只返回本人通知；
 * 不接收任何可扩大范围的入参。
 *
 * <p><b>合规（D68 / 02-红线 #7）</b>：响应只含「来源 + 事项 + 时间 + 未读」，
 * 经 {@link MiniNotificationVO} 脱敏，不出现任何业务单号。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/mini/notification")
@RequiredArgsConstructor
public class MiniNotificationController {

    /** 当日时间格式。 */
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    /** 跨日时间格式。 */
    private static final DateTimeFormatter MM_DD_HHMM = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** 来源标签：默认（系统通知与未知类型）。 */
    private static final String SOURCE_SYSTEM = "系统";

    private final NotificationService notificationService;

    /**
     * 我的消息分页（含未读数）。
     *
     * @param page   页码，从 1 开始
     * @param size   每页大小
     * @param userNo 当前登录用户业务账号（员工工号 / 渠道账号 / 客户编号）
     * @return {@code {records: MiniNotificationVO[], total, unreadCount}}
     */
    @GetMapping("/mine")
    public Result<Map<String, Object>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentUser("userNo") String userNo) {
        if (!StringUtils.hasText(userNo)) {
            return Result.ok(emptyPage());
        }
        PageResult<Notification> result = notificationService.page(userNo, page, size);
        List<Notification> rows = result == null ? null : result.getRecords();
        List<MiniNotificationVO> records = rows == null
                ? new ArrayList<>()
                : rows.stream().map(MiniNotificationController::toVo).collect(Collectors.toList());
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("records", records);
        data.put("total", result == null ? 0L : result.getTotal());
        data.put("unreadCount", notificationService.countUnread(userNo));
        return Result.ok(data);
    }

    /**
     * 未读数（供红点轮询）。
     *
     * @param userNo 当前登录用户业务账号
     * @return 未读条数
     */
    @GetMapping("/mine/unread-count")
    public Result<Integer> unreadCount(@CurrentUser("userNo") String userNo) {
        return Result.ok(notificationService.countUnread(userNo));
    }

    /**
     * 全部标记已读（打开消息列表后清红点）。
     *
     * @param userNo 当前登录用户业务账号
     * @return 受影响行数
     */
    @PostMapping("/mine/read-all")
    public Result<Integer> readAll(@CurrentUser("userNo") String userNo) {
        if (!StringUtils.hasText(userNo)) {
            return Result.ok(0);
        }
        return Result.ok(notificationService.markAllAsRead(userNo));
    }

    /** 未取到当前用户时的空页结构（避免下游 NPE / 500）。 */
    private Map<String, Object> emptyPage() {
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("records", Collections.emptyList());
        data.put("total", 0L);
        data.put("unreadCount", 0);
        return data;
    }

    /** 实体 → 展示态：去业务单号 + 类型映射来源 + 时间归并。 */
    private static MiniNotificationVO toVo(Notification n) {
        MiniNotificationVO vo = new MiniNotificationVO();
        vo.setSource(sourceOf(n.getType()));
        vo.setMatter(StringUtils.hasText(n.getTitle()) ? n.getTitle() : n.getContent());
        vo.setTime(formatTime(n.getCreatedAt()));
        vo.setCreatedAt(n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
        vo.setUnread(n.getReadStatus() == null || n.getReadStatus() == 0);
        return vo;
    }

    /**
     * 通知类型 → 来源标签。
     *
     * <p>对外口径：{@code PRODUCT_APPROVAL} 展示为「审核」（合规约定，非「审批」）。
     *
     * @param type 通知类型（{@link Notification} 常量）
     * @return 来源标签
     */
    private static String sourceOf(String type) {
        if (type == null) {
            return SOURCE_SYSTEM;
        }
        switch (type) {
            case Notification.TYPE_PRODUCT_APPROVAL:
                return "审核";
            case Notification.TYPE_LEAD_RECYCLE_WARN:
            case Notification.TYPE_CLIENT_RECYCLE_WARN:
                return "提醒";
            case Notification.TYPE_SERVICE_ORDER:
                return "服务";
            default:
                return SOURCE_SYSTEM;
        }
    }

    /**
     * 时间归并：今天 HH:mm / 昨天 HH:mm / MM-dd HH:mm。
     *
     * @param time 创建时间
     * @return 展示文案
     */
    private static String formatTime(LocalDateTime time) {
        if (time == null) {
            return "";
        }
        LocalDate today = LocalDate.now();
        LocalDate day = time.toLocalDate();
        if (day.equals(today)) {
            return "今天 " + time.format(HHMM);
        }
        if (day.equals(today.minusDays(1))) {
            return "昨天 " + time.format(HHMM);
        }
        return time.format(MM_DD_HHMM);
    }
}
