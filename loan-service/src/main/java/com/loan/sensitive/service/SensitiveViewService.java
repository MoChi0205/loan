package com.loan.sensitive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.AesUtils;
import com.loan.lead.entity.Lead;
import com.loan.lead.mapper.LeadMapper;
import com.loan.notification.dto.NotificationReq;
import com.loan.notification.entity.Notification;
import com.loan.notification.service.NotificationService;
import com.loan.sensitive.dto.SensitiveApplyViewResp;
import com.loan.sensitive.dto.SensitiveQuotaVO;
import com.loan.sensitive.entity.SensitiveViewGrant;
import com.loan.sensitive.entity.SensitiveViewLog;
import com.loan.sensitive.mapper.SensitiveViewGrantMapper;
import com.loan.sensitive.mapper.SensitiveViewLogMapper;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 敏感数据查看授权服务（受限角色默认脱敏 → 申请 → 授权 → 日限额 30 → 留痕；豁免角色直看明文）。
 *
 * <p>规则：
 * <ul>
 *   <li>豁免角色（BOSS / DEPT_MANAGER）：直接查看明文，不受授权与日限额约束。</li>
 *   <li>受限角色（ADVISER）：默认脱敏；首次申请通过后写入授权记录并记留痕日志，
 *       当日解锁数达 {@link #DEFAULT_DAILY_LIMIT} 上限即拒绝并通知老板/主管。</li>
 *   <li>授权持久化：同一线索已授权后再次查看不再消耗当日额度。</li>
 * </ul>
 *
 * @author loan-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveViewService {

    /** 受限角色每日查看敏感数据上限（需求：日限额 30）。 */
    public static final int DEFAULT_DAILY_LIMIT = 30;

    /** 豁免角色（老板/主管）：直接查看明文。 */
    private static final Set<String> EXEMPT_ROLES =
            new HashSet<>(Arrays.asList("BOSS", "DEPT_MANAGER"));

    private final SensitiveViewGrantMapper grantMapper;
    private final SensitiveViewLogMapper logMapper;
    private final LeadMapper leadMapper;
    private final StaffMapper staffMapper;
    private final NotificationService notificationService;

    /**
     * 是否豁免角色（老板/主管）。
     *
     * @param roleCode 角色编码
     * @return true 豁免
     */
    public boolean isExemptRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return false;
        }
        return EXEMPT_ROLES.contains(roleCode.trim().toUpperCase());
    }

    /**
     * 当日查看上限。
     *
     * @return 上限
     */
    public int getDailyLimit() {
        return DEFAULT_DAILY_LIMIT;
    }

    /**
     * 统计某员工当日查看次数。
     *
     * @param userNo 工号
     * @param date   日期
     * @return 当日次数
     */
    public long countTodayViews(String userNo, LocalDate date) {
        if (!StringUtils.hasText(userNo)) {
            return 0L;
        }
        Long count = logMapper.countToday(userNo.trim(), date == null ? LocalDate.now() : date);
        return count == null ? 0L : count;
    }

    /**
     * 查询当前用户额度（上限 / 已用 / 剩余）。
     *
     * @param userNo 工号
     * @return 额度 VO
     */
    public SensitiveQuotaVO getQuota(String userNo) {
        int limit = getDailyLimit();
        long used = countTodayViews(userNo, LocalDate.now());
        SensitiveQuotaVO vo = new SensitiveQuotaVO();
        vo.setLimit(limit);
        vo.setUsed((int) Math.min(used, Integer.MAX_VALUE));
        vo.setRemaining((int) Math.max(0L, limit - used));
        return vo;
    }

    /**
     * 是否已授权查看某线索。
     *
     * @param userNo 工号
     * @param leadNo 线索业务 ID
     * @return true 已授权
     */
    public boolean hasGrant(String userNo, String leadNo) {
        if (!StringUtils.hasText(userNo) || !StringUtils.hasText(leadNo)) {
            return false;
        }
        Long count = grantMapper.selectCount(new LambdaQueryWrapper<SensitiveViewGrant>()
                .eq(SensitiveViewGrant::getUserNo, userNo.trim())
                .eq(SensitiveViewGrant::getLeadNo, leadNo.trim())
                .last("LIMIT 1"));
        return count != null && count > 0;
    }

    /**
     * 申请查看线索敏感手机号。
     *
     * <p>豁免角色直看明文；受限角色首次申请走授权 + 留痕 + 日限额校验；已授权直接看明文不再消耗额度。
     *
     * @param leadNo   线索业务 ID
     * @param userNo   当前工号
     * @param roleCode 当前角色
     * @return 申请查看响应
     */
    @Transactional(rollbackFor = Exception.class)
    public SensitiveApplyViewResp applyView(String leadNo, String userNo, String roleCode) {
        if (!StringUtils.hasText(leadNo)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "线索编号必填");
        }
        if (!StringUtils.hasText(userNo)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        Lead lead = leadMapper.selectOne(new LambdaQueryWrapper<Lead>().eq(Lead::getLeadNo, leadNo.trim()));
        if (lead == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "线索不存在");
        }
        String plain = AesUtils.decrypt(lead.getPhone());
        String masked = DesensitizeUtils.phone(plain);

        SensitiveApplyViewResp resp = new SensitiveApplyViewResp();
        resp.setLeadNo(leadNo.trim());
        resp.setPhoneMasked(masked);

        // 豁免角色：直接看明文，不走授权/限额
        if (isExemptRole(roleCode)) {
            resp.setPhonePlain(plain);
            resp.setRevealed(true);
            applyQuota(resp, userNo);
            return resp;
        }

        // 已授权：直接看明文，不再消耗当日额度（授权持久化）
        if (hasGrant(userNo, leadNo)) {
            resp.setPhonePlain(plain);
            resp.setRevealed(true);
            applyQuota(resp, userNo);
            return resp;
        }

        // 受限角色：校验当日额度
        long used = countTodayViews(userNo, LocalDate.now());
        if (used >= getDailyLimit()) {
            notifyOverLimit(userNo);
            throw new BusinessException(ResultCode.SENSITIVE_QUOTA_EXCEEDED,
                    String.format("今日查看次数已用完（已用 %d / 上限 %d）", used, getDailyLimit()));
        }
        insertGrant(userNo, leadNo);
        insertViewLog(userNo, leadNo);
        resp.setPhonePlain(plain);
        resp.setRevealed(true);
        long usedAfter = countTodayViews(userNo, LocalDate.now());
        if (usedAfter >= getDailyLimit()) {
            notifyOverLimit(userNo);
        }
        applyQuota(resp, userNo);
        return resp;
    }

    /** 填充额度字段。 */
    private void applyQuota(SensitiveApplyViewResp resp, String userNo) {
        SensitiveQuotaVO quota = getQuota(userNo);
        resp.setLimit(quota.getLimit());
        resp.setUsed(quota.getUsed());
        resp.setRemaining(quota.getRemaining());
    }

    /** 写入授权记录（并发重复视为已授权）。 */
    private void insertGrant(String userNo, String leadNo) {
        SensitiveViewGrant grant = new SensitiveViewGrant();
        grant.setUserNo(userNo.trim());
        grant.setLeadNo(leadNo.trim());
        grant.setCreatedAt(LocalDateTime.now());
        try {
            grantMapper.insert(grant);
        } catch (DuplicateKeyException ignored) {
            // 并发重复申请：UK 冲突，视为已授权
        }
    }

    /** 写入查看留痕（日限额统计依据）。 */
    private void insertViewLog(String userNo, String leadNo) {
        SensitiveViewLog logPo = new SensitiveViewLog();
        logPo.setUserNo(userNo.trim());
        logPo.setLeadNo(leadNo.trim());
        logPo.setViewDate(LocalDate.now());
        logPo.setCreatedAt(LocalDateTime.now());
        logMapper.insert(logPo);
    }

    /** 达上限通知老板/主管。 */
    private void notifyOverLimit(String applicantUserNo) {
        Staff applicant = staffMapper.selectOne(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, applicantUserNo.trim()));
        String name = applicant != null && StringUtils.hasText(applicant.getStaffName())
                ? applicant.getStaffName() : applicantUserNo;
        String content = String.format("%s 今日查看客户敏感数据已达上限 %d 次，请留意！！！", name, getDailyLimit());
        List<Staff> managers = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                .in(Staff::getRoleCode, "BOSS", "DEPT_MANAGER")
                .eq(Staff::getStatus, "ACTIVE"));
        if (managers == null) {
            return;
        }
        Set<String> notified = new HashSet<>();
        for (Staff s : managers) {
            if (s != null && StringUtils.hasText(s.getStaffCode()) && notified.add(s.getStaffCode())) {
                NotificationReq req = new NotificationReq();
                req.setUserNo(s.getStaffCode());
                req.setType(Notification.TYPE_SYSTEM_NOTICE);
                req.setTitle("敏感数据查看超额预警");
                req.setContent(content);
                req.setRelatedId(applicantUserNo);
                try {
                    notificationService.send(req);
                } catch (Exception e) {
                    log.warn("发送敏感查看超额通知失败: {}", s.getStaffCode(), e);
                }
            }
        }
    }
}
