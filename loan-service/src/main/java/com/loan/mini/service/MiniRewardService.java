package com.loan.mini.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.reward.entity.RewardRecord;
import com.loan.reward.mapper.RewardRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 小程序端推荐有礼：我的推荐奖励记录与汇总（referrer_client_code 维度）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class MiniRewardService {

    private final RewardRecordMapper rewardRecordMapper;

    /**
     * 我的奖励汇总（推荐有礼首页）。
     *
     * @param clientCode 客户编码
     * @return {totalAmount, totalCount, pendingCount, grantedCount}
     */
    public Map<String, Object> mySummary(String clientCode) {
        List<RewardRecord> all = rewardRecordMapper.selectList(new LambdaQueryWrapper<RewardRecord>()
                .eq(RewardRecord::getReferrerClientCode, clientCode));
        BigDecimal total = BigDecimal.ZERO;
        long pending = 0;
        long granted = 0;
        for (RewardRecord r : all) {
            if (r.getRewardAmount() != null) {
                total = total.add(r.getRewardAmount());
            }
            if ("PENDING_AUDIT".equals(r.getStatus()) || "SETTLED".equals(r.getStatus())) {
                pending++;
            } else if ("GRANTED".equals(r.getStatus())) {
                granted++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalAmount", total);
        m.put("totalCount", all.size());
        m.put("pendingCount", pending);
        m.put("grantedCount", granted);
        return m;
    }

    /**
     * 我的奖励记录分页。
     *
     * @param clientCode 客户编码
     * @param page       页码
     * @param size       每页大小
     * @return 奖励分页
     */
    public PageResult<Map<String, Object>> myRewards(String clientCode, int page, int size) {
        LambdaQueryWrapper<RewardRecord> wrapper = new LambdaQueryWrapper<RewardRecord>()
                .eq(RewardRecord::getReferrerClientCode, clientCode)
                .orderByDesc(RewardRecord::getCreatedAt);
        Page<RewardRecord> result = rewardRecordMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rewardNo", r.getRewardNo());
            m.put("level", r.getLevel());
            m.put("refereeClientCode", r.getRefereeClientCode());
            m.put("serviceOrderNo", r.getServiceOrderNo());
            m.put("rewardAmount", r.getRewardAmount());
            m.put("status", r.getStatus());
            m.put("settleTime", r.getSettleTime());
            m.put("createdAt", r.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }
}
