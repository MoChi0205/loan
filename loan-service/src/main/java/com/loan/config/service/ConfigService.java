package com.loan.config.service;

import com.loan.org.mapper.DepartmentMapper;
import com.loan.product.mapper.BankChannelMapper;
import com.loan.product.mapper.BankProductMapper;
import com.loan.report.mapper.ReportTemplateMapper;
import com.loan.reward.mapper.RewardRuleMapper;
import com.loan.rule.mapper.RuleMapper;
import com.loan.sms.mapper.SmsTemplateMapper;
import com.loan.staff.mapper.StaffMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置向导服务：统计各配置项完成度（首次使用引导）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final DepartmentMapper departmentMapper;
    private final StaffMapper staffMapper;
    private final BankChannelMapper bankChannelMapper;
    private final RuleMapper ruleMapper;
    private final BankProductMapper bankProductMapper;
    private final RewardRuleMapper rewardRuleMapper;
    private final SmsTemplateMapper smsTemplateMapper;
    private final ReportTemplateMapper reportTemplateMapper;

    /**
     * 配置完成度总览。
     */
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("departmentCount", departmentMapper.selectCount(null));
        m.put("staffCount", staffMapper.selectCount(null));
        m.put("channelCount", bankChannelMapper.selectCount(null));
        m.put("ruleCount", ruleMapper.selectCount(null));
        m.put("productCount", bankProductMapper.selectCount(null));
        m.put("rewardRuleCount", rewardRuleMapper.selectCount(null));
        m.put("smsTemplateCount", smsTemplateMapper.selectCount(null));
        m.put("reportTemplateCount", reportTemplateMapper.selectCount(null));
        return m;
    }
}
