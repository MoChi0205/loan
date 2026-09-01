package com.loan.lead.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.common.util.PageOrder;
import com.loan.exception.BusinessException;
import com.loan.lead.entity.Lead;
import com.loan.lead.entity.LeadAllocationRecord;
import com.loan.lead.mapper.LeadAllocationRecordMapper;
import com.loan.lead.mapper.LeadMapper;
import com.loan.notification.dto.NotificationReq;
import com.loan.notification.entity.Notification;
import com.loan.notification.service.NotificationService;
import com.loan.sensitive.service.SensitiveViewService;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 线索服务：新增（谁录入归谁，渠道/VIP 进公海）/ 分页（我的/公海）/ 认领 / 指派。
 *
 * <p>回收规则：按 t_lead_recycle_config 逐状态配置回收天数（阶段一默认 NEW 30 天），
 * 超过 lastFollowedAt 无跟进自动回收进公海（见 {@link #recycleOverdue()}，供定时任务）。
 * 回收前 3 天向归属人发站内预警通知（参考 tse CustomerAutoRecycleNotifyService）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class LeadService {

    /** 允许排序字段（白名单，防注入） */
    private static final java.util.Map<String, com.baomidou.mybatisplus.core.toolkit.support.SFunction<Lead, ?>> ORDER_FIELDS =
            new java.util.HashMap<>();
    static {
        ORDER_FIELDS.put("createdAt", Lead::getCreatedAt);
        ORDER_FIELDS.put("updatedAt", Lead::getUpdatedAt);
        ORDER_FIELDS.put("lastFollowedAt", Lead::getLastFollowedAt);
    }

    private final LeadMapper leadMapper;
    private final LeadAllocationRecordMapper allocationRecordMapper;
    private final NotificationService notificationService;
    private final SensitiveViewService sensitiveViewService;

    /**
     * 新增线索（谁录入归谁；来源 CHANNEL/VIP 不归属直接进公海）。
     *
     * @param lead          线索（contactName/phone/leadType 必填）
     * @param recorderCode  录入人工号（业务编码）
     * @param recorderName  录入人姓名
     * @return 线索编号（业务唯一ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public String create(Lead lead, String recorderCode, String recorderName) {
        if (!StringUtils.hasText(lead.getContactName()) || !StringUtils.hasText(lead.getPhone())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "联系人与手机号必填");
        }
        lead.setLeadNo(genLeadNo());
        lead.setPhoneHash(sha256(lead.getPhone()));
        lead.setRecorderStaffCode(recorderCode);
        lead.setFollowStatus(lead.getFollowStatus() == null ? "NEW" : lead.getFollowStatus());
        // 渠道 / VIP / 小程序(MINI) 录入不归属，直接进公海
        boolean toPool = "CHANNEL".equalsIgnoreCase(lead.getSource())
                || "VIP".equalsIgnoreCase(lead.getSource())
                || "MINI".equalsIgnoreCase(lead.getSource());
        lead.setOwnerStaffCode(toPool ? null : recorderCode);
        lead.setCreatedBy(recorderName);
        leadMapper.insert(lead);

        allocationRecordMapper.insert(buildRecord(lead.getLeadNo(), "MANUAL", null, lead.getOwnerStaffCode(),
                recorderName, "录入线索"));
        return lead.getLeadNo();
    }

    /**
     * 分页查询线索（我的线索 / 公海）。
     *
     * <p>手机号出参：豁免角色（老板/主管）直接返回明文；受限角色（顾问）列表页统一脱敏，
     * 明文需经敏感查看授权流程（apply-view）单独获取。
     *
     * @param ownerStaffCode 归属人工号（null 查公海）
     * @param leadType       客群（可选）
     * @param followStatus   跟进状态（可选）
     * @param keyword        关键字（可选）
     * @param page           页码
     * @param size           每页大小
     * @param roleCode       当前角色（决定列表页手机号是否脱敏）
     * @param userNo         当前工号
     * @return 线索分页
     */
    public PageResult<Lead> page(String ownerStaffCode, String leadType, String followStatus,
                                 String keyword, int page, int size, String roleCode, String userNo,
                                 String orderBy, String orderDir) {
        LambdaQueryWrapper<Lead> wrapper = new LambdaQueryWrapper<>();
        if (ownerStaffCode != null) {
            wrapper.eq(Lead::getOwnerStaffCode, ownerStaffCode);
        } else {
            wrapper.isNull(Lead::getOwnerStaffCode);
        }
        if (StringUtils.hasText(leadType)) {
            wrapper.eq(Lead::getLeadType, leadType);
        }
        if (StringUtils.hasText(followStatus)) {
            wrapper.eq(Lead::getFollowStatus, followStatus);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 联系人 / 线索编号模糊；11 位手机号追加 phoneHash（SHA-256）精确匹配（phone 为密文不可 LIKE）
            if (kw.matches("\\d{11}")) {
                String hash = sha256(kw);
                wrapper.and(w -> w.like(Lead::getContactName, kw).or().like(Lead::getLeadNo, kw)
                        .or().eq(Lead::getPhoneHash, hash));
            } else {
                wrapper.and(w -> w.like(Lead::getContactName, kw).or().like(Lead::getLeadNo, kw));
            }
        }
        PageOrder.apply(wrapper, orderBy, orderDir, ORDER_FIELDS, Lead::getCreatedAt);
        Page<Lead> result = leadMapper.selectPage(new Page<>(page, size), wrapper);
        // 出参：手机号 AES 解密；豁免角色直看明文，受限角色列表页脱敏
        boolean exempt = sensitiveViewService.isExemptRole(roleCode);
        result.getRecords().forEach(lead -> {
            String plain = com.loan.infrastructure.security.AesUtils.decrypt(lead.getPhone());
            lead.setPhone(exempt ? plain : DesensitizeUtils.phone(plain));
        });
        return PageResult.build(page, size, result.getTotal(), result.getRecords());
    }

    /**
     * 公海认领（冷却期内不可认领）。
     *
     * @param leadNo     线索编号（业务唯一ID）
     * @param staffCode  认领人工号（业务编码）
     * @param staffName  认领人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void claim(String leadNo, String staffCode, String staffName) {
        Lead lead = leadMapper.selectOne(new LambdaQueryWrapper<Lead>().eq(Lead::getLeadNo, leadNo));
        if (lead == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "线索不存在");
        }
        if (lead.getOwnerStaffCode() != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "线索已被认领");
        }
        // 冷却期内原归属人不可认领
        if (lead.getAssignBlockedUntil() != null
                && lead.getAssignBlockedUntil().isAfter(LocalDateTime.now())
                && lead.getRecorderStaffCode() != null
                && lead.getRecorderStaffCode().equals(staffCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "回收冷却期内不可认领");
        }
        String from = lead.getOwnerStaffCode();
        lead.setOwnerStaffCode(staffCode);
        lead.setUpdatedBy(staffName);
        leadMapper.updateById(lead);
        allocationRecordMapper.insert(buildRecord(lead.getLeadNo(), "CLAIM", from, staffCode, staffName, "公海认领"));
    }

    /**
     * 手动指派（主管/老板）。
     *
     * @param leadNo      线索编号（业务唯一ID）
     * @param toStaffCode 目标员工工号（业务编码）
     * @param operator    操作人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void assign(String leadNo, String toStaffCode, String operator) {
        Lead lead = leadMapper.selectOne(new LambdaQueryWrapper<Lead>().eq(Lead::getLeadNo, leadNo));
        if (lead == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "线索不存在");
        }
        String from = lead.getOwnerStaffCode();
        lead.setOwnerStaffCode(toStaffCode);
        lead.setUpdatedBy(operator);
        leadMapper.updateById(lead);
        allocationRecordMapper.insert(buildRecord(lead.getLeadNo(), "MANUAL", from, toStaffCode, operator, "手动指派"));
    }

    /**
     * 批量认领（公海 → 我的线索）。整体事务：任一条失败则全部回滚。
     *
     * @param leadNos   线索编号列表
     * @param staffCode 认领人工号（业务编码）
     * @param staffName 认领人姓名
     * @return 成功认领条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchClaim(List<String> leadNos, String staffCode, String staffName) {
        if (leadNos == null || leadNos.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要认领的线索");
        }
        for (String leadNo : leadNos) {
            claim(leadNo, staffCode, staffName);
        }
        return leadNos.size();
    }

    /**
     * 批量指派。整体事务：任一条失败则全部回滚。
     *
     * @param leadNos     线索编号列表
     * @param toStaffCode 目标员工工号（业务编码）
     * @param operator    操作人姓名
     * @return 成功指派条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchAssign(List<String> leadNos, String toStaffCode, String operator) {
        if (leadNos == null || leadNos.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要指派的线索");
        }
        if (!StringUtils.hasText(toStaffCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择目标员工");
        }
        for (String leadNo : leadNos) {
            assign(leadNo, toStaffCode, operator);
        }
        return leadNos.size();
    }

    /**
     * 批量删除线索（物理删除，审计留痕 DELETE）。
     * <p>t_lead 无逻辑删除字段，物理删除前会写 t_lead_allocation_record（DELETE 类型），
     * 且删除动作本身即为不可逆操作，前端需二次确认。
     *
     * @param leadNos  线索编号列表
     * @param operator 操作人姓名
     * @return 成功删除条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<String> leadNos, String operator) {
        if (leadNos == null || leadNos.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择要删除的线索");
        }
        for (String leadNo : leadNos) {
            Lead lead = leadMapper.selectOne(new LambdaQueryWrapper<Lead>().eq(Lead::getLeadNo, leadNo));
            if (lead == null) {
                continue;
            }
            // 审计留痕后物理删除
            allocationRecordMapper.insert(buildRecord(lead.getLeadNo(), "DELETE",
                    lead.getOwnerStaffCode(), null, operator, "批量删除线索"));
            leadMapper.deleteById(lead.getId());
        }
        return leadNos.size();
    }

    /**
     * 回收过期线索进公海（供定时任务 @Scheduled 调用）。
     * 阶段一默认：NEW 状态 30 天未跟进即回收，回收后冷却 7 天。
     *
     * @return 回收数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int recycleOverdue() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        LambdaQueryWrapper<Lead> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(Lead::getOwnerStaffCode)
                .and(w -> w.isNull(Lead::getLastFollowedAt)
                        .or().lt(Lead::getLastFollowedAt, threshold));
        List<Lead> overdue = leadMapper.selectList(wrapper);
        for (Lead lead : overdue) {
            String from = lead.getOwnerStaffCode();
            String ownerNo = lead.getOwnerStaffCode();
            lead.setOwnerStaffCode(null);
            lead.setAssignBlockedUntil(LocalDateTime.now().plusDays(7));
            lead.setUpdatedBy("system");
            leadMapper.updateById(lead);
            allocationRecordMapper.insert(buildRecord(lead.getLeadNo(), "RECYCLE", from, null, "system", "超期未跟进回收进公海"));
            // 站内通知：线索已回收进公海
            if (ownerNo != null) {
                notificationService.send(buildNotice(ownerNo, Notification.TYPE_LEAD_RECYCLE_WARN,
                        "线索已回收进公海",
                        "线索【" + lead.getLeadNo() + "】因超期未跟进已回收进公海，冷却期内不可认领。",
                        lead.getLeadNo()));
            }
        }
        return overdue.size();
    }

    /**
     * 回收预警：扫描 3 天内到期的线索，向归属人发站内预警通知（去重）。
     *
     * @return 预警发送数
     */
    @Transactional(rollbackFor = Exception.class)
    public int warnRecycle() {
        // 3 天内到期：lastFollowedAt 在 27~30 天前（或为空且创建 27 天前）
        LocalDateTime warnStart = LocalDateTime.now().minusDays(30);
        LocalDateTime warnEnd = LocalDateTime.now().minusDays(27);
        LambdaQueryWrapper<Lead> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(Lead::getOwnerStaffCode)
                .and(w -> w.isNull(Lead::getLastFollowedAt)
                        .or().between(Lead::getLastFollowedAt, warnStart, warnEnd));
        List<Lead> warns = leadMapper.selectList(wrapper);
        int sent = 0;
        for (Lead lead : warns) {
            String ownerNo = lead.getOwnerStaffCode();
            if (ownerNo == null) {
                continue;
            }
            // 去重：同类型 + 同关联线索只发一次
            if (notificationService.existsByTypeAndRelatedId(Notification.TYPE_LEAD_RECYCLE_WARN, lead.getLeadNo())) {
                continue;
            }
            notificationService.send(buildNotice(ownerNo, Notification.TYPE_LEAD_RECYCLE_WARN,
                    "线索即将回收预警",
                    "线索【" + lead.getLeadNo() + "】将在 3 天内因超期未跟进被回收进公海，请及时跟进。",
                    lead.getLeadNo()));
            sent++;
        }
        return sent;
    }

    /**
     * 构建流转记录（线索/归属人均为业务编码）。
     */
    private LeadAllocationRecord buildRecord(String leadNo, String actionType, String fromCode, String toCode,
                                             String operator, String remark) {
        LeadAllocationRecord r = new LeadAllocationRecord();
        r.setLeadNo(leadNo);
        r.setActionType(actionType);
        r.setFromStaffCode(fromCode);
        r.setToStaffCode(toCode);
        r.setOperator(operator);
        r.setRemark(remark);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    /**
     * 构建通知请求。
     */
    private NotificationReq buildNotice(String userNo, String type, String title, String content, String relatedId) {
        NotificationReq req = new NotificationReq();
        req.setUserNo(userNo);
        req.setType(type);
        req.setTitle(title);
        req.setContent(content);
        req.setRelatedId(relatedId);
        return req;
    }

    /** 生成线索业务 ID（业务前缀 lead + 32 位随机，全表唯一）。 */
    private String genLeadNo() {
        return BizIdGenerator.generate("lead");
    }

    /** 手机号 SHA-256 哈希。 */
    private String sha256(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 失败", e);
        }
    }
}
