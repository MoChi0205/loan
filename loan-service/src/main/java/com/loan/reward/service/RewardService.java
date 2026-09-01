package com.loan.reward.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.infrastructure.security.HashUtils;
import com.loan.common.ResultCode;
import com.loan.common.util.BizIdGenerator;
import com.loan.common.util.PageOrder;
import com.loan.exception.BusinessException;
import com.loan.invitation.entity.Invitation;
import com.loan.invitation.mapper.InvitationMapper;
import com.loan.order.entity.ServiceOrder;
import com.loan.order.mapper.ServiceOrderMapper;
import com.loan.reward.entity.RewardRecord;
import com.loan.reward.entity.RewardRule;
import com.loan.reward.mapper.RewardRecordMapper;
import com.loan.reward.mapper.RewardRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 推荐奖励服务：成交自动结算 / 分页 / 审核发放 / 驳回 / 作废。
 *
 * <p>结算链路：工单 DEAL → 被推荐客户档案 → 邀请关系（referrer_type=CUSTOMER）→
 * 推荐人客户 → 最新生效奖励规则 → 计算直推奖励（默认只发 1 层）→ 落奖励单待审核。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class RewardService {

    /** 允许排序字段（白名单，防注入） */
    private static final java.util.Map<String, com.baomidou.mybatisplus.core.toolkit.support.SFunction<RewardRecord, ?>> ORDER_FIELDS =
            new java.util.HashMap<>();
    static {
        ORDER_FIELDS.put("createdAt", RewardRecord::getCreatedAt);
        ORDER_FIELDS.put("updatedAt", RewardRecord::getUpdatedAt);
        ORDER_FIELDS.put("rewardAmount", RewardRecord::getRewardAmount);
        ORDER_FIELDS.put("settleTime", RewardRecord::getSettleTime);
    }

    private final RewardRecordMapper rewardRecordMapper;
    private final RewardRuleMapper rewardRuleMapper;
    private final ServiceOrderMapper orderMapper;
    private final ClientProfileMapper clientProfileMapper;
    private final InvitationMapper invitationMapper;

    /**
     * 工单成交自动结算奖励（OrderService DEAL 时调用，幂等）。
     *
     * <p>规则匹配：按（产品编码 × 客群）精确匹配生效规则；无匹配规则（无默认兜底）则不结算。
     * 层级：直推 L1 必发；间推开关开启时，沿邀请链找推荐人的推荐人发 L2。
     * 手动金额：成交时传入 rewardAmount 则跳过比例计算，标记人工调整。
     *
     * @param orderNo         工单号
     * @param operator        操作人
     * @param manualRewardAmount 成交时手动指定金额（可选）
     * @return 生成奖励单数
     */
    @Transactional(rollbackFor = Exception.class)
    public int settleForOrder(String orderNo, String operator, BigDecimal manualRewardAmount) {
        ServiceOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<ServiceOrder>().eq(ServiceOrder::getOrderNo, orderNo));
        if (order == null || !ServiceOrder.STATUS_DEAL.equals(order.getStatus()) || order.getDealAmount() == null) {
            return 0;
        }
        // 被推荐人（成交客户）档案
        ClientProfile referee = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, order.getClientProfileCode()));
        if (referee == null) {
            return 0;
        }
        // 客群（用于规则匹配）
        String customerGroup = referee.getCustomerGroup();

        // 邀请关系：仅受邀用户推荐（referrer_type=CUSTOMER）进入奖励结算
        Invitation invitation = invitationMapper.selectOne(new LambdaQueryWrapper<Invitation>()
                .eq(Invitation::getReferrerType, "CUSTOMER")
                .eq(Invitation::getUsedByClientId, referee.getId())
                .eq(Invitation::getUsedFlag, 1)
                .orderByDesc(Invitation::getId)
                .last("limit 1"));
        if (invitation == null || invitation.getReferrerId() == null) {
            return 0;
        }
        ClientProfile referrer = clientProfileMapper.selectById(invitation.getReferrerId());
        if (referrer == null) {
            return 0;
        }

        // 按（产品 × 客群）精确匹配生效规则；无默认兜底，匹配不到则不结算
        RewardRule rule = rewardRuleMapper.selectOne(new LambdaQueryWrapper<RewardRule>()
                .eq(RewardRule::getStatus, "ACTIVE")
                .eq(RewardRule::getProductCode, order.getBankProductCode())
                .eq(RewardRule::getCustomerGroup, customerGroup)
                .le(RewardRule::getValidFrom, LocalDateTime.now())
                .and(w -> w.isNull(RewardRule::getValidUntil)
                        .or().ge(RewardRule::getValidUntil, LocalDateTime.now()))
                .orderByDesc(RewardRule::getValidFrom)
                .last("limit 1"));
        if (rule == null || rule.getDirectRate() == null) {
            return 0;
        }

        int count = 0;
        BigDecimal base = order.getDealAmount();

        // 直推 L1
        count += settleLevel(orderNo, referrer, referee, rule, 1,
                rule.getDirectRate(), base, operator, manualRewardAmount);

        // 间推 L2：开关开启且配置了间推比例，沿邀请链再上一层
        if (Integer.valueOf(1).equals(rule.getIndirectEnabled()) && rule.getIndirectRate() != null) {
            Invitation l2Inv = invitationMapper.selectOne(new LambdaQueryWrapper<Invitation>()
                    .eq(Invitation::getReferrerType, "CUSTOMER")
                    .eq(Invitation::getUsedByClientId, referrer.getId())
                    .eq(Invitation::getUsedFlag, 1)
                    .orderByDesc(Invitation::getId)
                    .last("limit 1"));
            if (l2Inv != null && l2Inv.getReferrerId() != null) {
                ClientProfile l2Referrer = clientProfileMapper.selectById(l2Inv.getReferrerId());
                if (l2Referrer != null) {
                    // L2 手动金额沿用 L1 的（如成交时指定）；间推一般仍按比例
                    count += settleLevel(orderNo, l2Referrer, referee, rule, 2,
                            rule.getIndirectRate(), base, operator, null);
                }
            }
        }
        return count;
    }

    /**
     * 单层奖励落库（含幂等、比例/手动金额计算、上下限约束）。
     *
     * @return 生成 1 或跳过 0
     */
    private int settleLevel(String orderNo, ClientProfile referrer, ClientProfile referee,
                            RewardRule rule, int level, BigDecimal rate,
                            BigDecimal base, String operator, BigDecimal manualRewardAmount) {
        // 幂等：同工单+同推荐人+同层级已结算则跳过
        Long exists = rewardRecordMapper.selectCount(new LambdaQueryWrapper<RewardRecord>()
                .eq(RewardRecord::getServiceOrderNo, orderNo)
                .eq(RewardRecord::getReferrerClientCode, referrer.getClientCode())
                .eq(RewardRecord::getLevel, level));
        if (exists != null && exists > 0) {
            return 0;
        }
        BigDecimal amount;
        boolean manual = manualRewardAmount != null && manualRewardAmount.compareTo(BigDecimal.ZERO) > 0;
        if (manual && level == 1) {
            amount = manualRewardAmount.setScale(2, RoundingMode.HALF_UP);
        } else {
            amount = base.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            if (rule.getMinAmount() != null && amount.compareTo(rule.getMinAmount()) < 0) {
                amount = rule.getMinAmount();
            }
            if (rule.getMaxAmount() != null && amount.compareTo(rule.getMaxAmount()) > 0) {
                amount = rule.getMaxAmount();
            }
        }
        RewardRecord record = new RewardRecord();
        record.setRewardNo(BizIdGenerator.generate("reward"));
        record.setReferrerClientCode(referrer.getClientCode());
        record.setRefereeClientCode(referee.getClientCode());
        record.setLevel(level);
        record.setServiceOrderNo(orderNo);
        record.setBaseAmount(base);
        record.setRateSnapshot(rate);
        record.setRuleVersion(rule.getRuleVersion());
        record.setCalcProcess("工单成交金额 " + base + " × " + (level == 1 ? "直推" : "间推") + "比例 " + rate
                + (manual && level == 1 ? "（成交手动指定 " + manualRewardAmount + "）" : ""));
        record.setRewardAmount(amount);
        record.setStatus(RewardRecord.STATUS_PENDING_AUDIT);
        record.setManualAdjustFlag(manual && level == 1 ? 1 : 0);
        record.setManualAdjustReason(manual && level == 1 ? "成交时手动指定金额" : null);
        record.setCreatedBy(operator);
        record.setUpdatedBy(operator);
        rewardRecordMapper.insert(record);
        return 1;
    }

    /**
     * 奖励分页（含推荐人/被推荐人客户名 + 手机号）。
     *
     * @param status    状态（可选）
     * @param keyword   关键字：奖励单号 / 工单号 / 客户姓名 / 手机号 / 企业名
     * @param startDate 创建时间起始（YYYY-MM-DD，含）
     * @param endDate   创建时间截止（YYYY-MM-DD，含）
     * @param page      页码
     * @param size      每页大小
     * @return 奖励分页
     */
    public PageResult<Map<String, Object>> page(String status, String keyword,
                                                String startDate, String endDate,
                                                int page, int size, String orderBy, String orderDir) {
        LambdaQueryWrapper<RewardRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(RewardRecord::getStatus, status);
        }
        // 日期范围筛选
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(RewardRecord::getCreatedAt, java.time.LocalDate.parse(startDate).atStartOfDay());
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(RewardRecord::getCreatedAt, java.time.LocalDate.parse(endDate).atTime(23, 59, 59));
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 奖励单号 / 工单号模糊；客户姓名 / 企业名 / 手机号（SHA-256 哈希精确）命中则追加匹配。
            // 不再按客户内部编码(客户ID)模糊，统一以「姓名/手机号/企业名」关键字查询。
            List<String> nameCodes = clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                            .like(ClientProfile::getContactName, kw)
                            .or().like(ClientProfile::getEnterpriseName, kw))
                    .stream().map(ClientProfile::getClientCode).filter(StringUtils::hasText)
                    .collect(Collectors.toList());
            List<String> phoneCodes = new ArrayList<>();
            if (kw.matches("\\d{11}")) {
                String hash = HashUtils.sha256Hex(kw);
                phoneCodes = clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                                .eq(ClientProfile::getPhoneHash, hash))
                        .stream().map(ClientProfile::getClientCode).filter(StringUtils::hasText)
                        .collect(Collectors.toList());
            }
            java.util.Set<String> matchedCodes = new java.util.LinkedHashSet<>();
            matchedCodes.addAll(nameCodes);
            matchedCodes.addAll(phoneCodes);
            wrapper.and(w -> {
                w.like(RewardRecord::getRewardNo, kw).or().like(RewardRecord::getServiceOrderNo, kw);
                if (!matchedCodes.isEmpty()) {
                    w.or().in(RewardRecord::getReferrerClientCode, matchedCodes)
                            .or().in(RewardRecord::getRefereeClientCode, matchedCodes);
                }
            });
        }
        PageOrder.apply(wrapper, orderBy, orderDir, ORDER_FIELDS, RewardRecord::getCreatedAt);
        Page<RewardRecord> result = rewardRecordMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), toViews(result.getRecords()));
    }

    /**
     * 审核奖励单（发放 / 驳回）。
     *
     * <p>发放时可附带手动金额（rewardAmount）：与系统计算金额不一致则覆盖并标记人工调整，须填原因。
     *
     * @param rewardNo          奖励单号
     * @param approve           是否通过
     * @param opinion           意见（驳回必填；发放且手动改金额时必填原因）
     * @param operator          审核人
     * @param rewardAmount      审核时手动指定金额（可选）
     * @param manualAdjustReason 人工调整原因（手动改金额必填）
     */
    @Transactional(rollbackFor = Exception.class)
    public void audit(String rewardNo, boolean approve, String opinion, String operator,
                      BigDecimal rewardAmount, String manualAdjustReason) {
        RewardRecord record = getByNo(rewardNo);
        if (!RewardRecord.STATUS_PENDING_AUDIT.equals(record.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅待审核奖励单可审核");
        }
        if (!approve && !StringUtils.hasText(opinion)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "驳回原因必填");
        }
        boolean manual = approve && rewardAmount != null && rewardAmount.compareTo(BigDecimal.ZERO) > 0
                && rewardAmount.compareTo(record.getRewardAmount()) != 0;
        if (manual && !StringUtils.hasText(manualAdjustReason)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "手动调整奖励金额须填写原因");
        }
        if (manual) {
            record.setRewardAmount(rewardAmount.setScale(2, RoundingMode.HALF_UP));
            record.setManualAdjustFlag(1);
            record.setManualAdjustReason(manualAdjustReason);
            record.setCalcProcess((record.getCalcProcess() == null ? "" : record.getCalcProcess() + "；")
                    + "审核手动调整为 " + rewardAmount);
        }
        record.setStatus(approve ? RewardRecord.STATUS_GRANTED : RewardRecord.STATUS_REJECTED);
        record.setRejectReason(approve ? null : opinion);
        record.setSettleStaffCode(operator);
        record.setSettleTime(LocalDateTime.now());
        record.setUpdatedBy(operator);
        rewardRecordMapper.updateById(record);
    }

    /**
     * 作废奖励单（REFUND 冲正联动或手动）。
     *
     * @param rewardNo 奖励单号
     * @param reason   原因（必填）
     * @param operator 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void voidReward(String rewardNo, String reason, String operator) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "作废原因必填");
        }
        RewardRecord record = getByNo(rewardNo);
        if (Arrays.asList(RewardRecord.STATUS_REJECTED, RewardRecord.STATUS_VOID).contains(record.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前状态不可作废");
        }
        record.setStatus(RewardRecord.STATUS_VOID);
        record.setRejectReason(reason);
        record.setUpdatedBy(operator);
        rewardRecordMapper.updateById(record);
    }

    /**
     * 奖励规则列表（全部，含停用；前端按产品/客群分组展示）。
     */
    public List<RewardRule> listRules() {
        return rewardRuleMapper.selectList(new LambdaQueryWrapper<RewardRule>()
                .orderByDesc(RewardRule::getValidFrom));
    }

    /**
     * 保存奖励规则（新增/更新二合一）。
     *
     * <p>产品编码 × 客群 唯一；无全局默认，须显式配置。状态缺省 ACTIVE。
     *
     * @param rule    规则（id 为空则新增）
     * @param operator 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveRule(RewardRule rule, String operator) {
        if (!StringUtils.hasText(rule.getProductCode()) || !StringUtils.hasText(rule.getCustomerGroup())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "产品编码与客群均须配置（无全局默认）");
        }
        if (rule.getDirectRate() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "直推比例必填");
        }
        if (rule.getStatus() == null) {
            rule.setStatus("ACTIVE");
        }
        if (rule.getValidFrom() == null) {
            rule.setValidFrom(LocalDateTime.now());
        }
        if (rule.getId() == null) {
            rule.setRuleVersion(BizIdGenerator.generate("rule"));
            rule.setCreatedBy(operator);
            rule.setUpdatedBy(operator);
            rewardRuleMapper.insert(rule);
        } else {
            rule.setUpdatedBy(operator);
            rewardRuleMapper.updateById(rule);
        }
    }

    /**
     * 停用奖励规则。
     */
    @Transactional(rollbackFor = Exception.class)
    public void disableRule(Long id, String operator) {
        RewardRule rule = rewardRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "规则不存在");
        }
        rule.setStatus("DISABLED");
        rule.setUpdatedBy(operator);
        rewardRuleMapper.updateById(rule);
    }

    /**
     * 按奖励单号查询。
     */
    private RewardRecord getByNo(String rewardNo) {
        RewardRecord record = rewardRecordMapper.selectOne(
                new LambdaQueryWrapper<RewardRecord>().eq(RewardRecord::getRewardNo, rewardNo));
        if (record == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "奖励单不存在");
        }
        return record;
    }

    /**
     * 实体 → 视图（含推荐人/被推荐人客户名）。
     */
    private List<Map<String, Object>> toViews(List<RewardRecord> records) {
        if (records.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<String> clientCodes = records.stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getReferrerClientCode(), r.getRefereeClientCode()))
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        final Map<String, String> nameMap;
        final Map<String, String> phoneMap;
        if (clientCodes.isEmpty()) {
            nameMap = java.util.Collections.emptyMap();
            phoneMap = java.util.Collections.emptyMap();
        } else {
            List<ClientProfile> profiles = clientProfileMapper.selectList(
                    new LambdaQueryWrapper<ClientProfile>().in(ClientProfile::getClientCode, clientCodes));
            nameMap = profiles.stream()
                    .collect(Collectors.toMap(ClientProfile::getClientCode,
                            c -> StringUtils.hasText(c.getEnterpriseName()) ? c.getEnterpriseName() : c.getContactName()));
            phoneMap = profiles.stream()
                    .filter(c -> StringUtils.hasText(c.getPhone()))
                    .collect(Collectors.toMap(ClientProfile::getClientCode, ClientProfile::getPhone, (a, b) -> a));
        }
        final Map<String, String> fNameMap = nameMap;
        final Map<String, String> fPhoneMap = phoneMap;
        return records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rewardNo", r.getRewardNo());
            m.put("referrerClientCode", r.getReferrerClientCode());
            m.put("referrerName", fNameMap.get(r.getReferrerClientCode()));
            m.put("referrerPhone", fPhoneMap.get(r.getReferrerClientCode()));
            m.put("refereeClientCode", r.getRefereeClientCode());
            m.put("refereeName", fNameMap.get(r.getRefereeClientCode()));
            m.put("refereePhone", fPhoneMap.get(r.getRefereeClientCode()));
            m.put("level", r.getLevel());
            m.put("serviceOrderNo", r.getServiceOrderNo());
            m.put("baseAmount", r.getBaseAmount());
            m.put("rateSnapshot", r.getRateSnapshot());
            m.put("ruleVersion", r.getRuleVersion());
            m.put("rewardAmount", r.getRewardAmount());
            m.put("status", r.getStatus());
            m.put("rejectReason", r.getRejectReason());
            m.put("manualAdjustFlag", r.getManualAdjustFlag());
            m.put("manualAdjustReason", r.getManualAdjustReason());
            m.put("calcProcess", r.getCalcProcess());
            m.put("settleStaffCode", r.getSettleStaffCode());
            m.put("settleTime", r.getSettleTime());
            m.put("createdAt", r.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
    }
}
