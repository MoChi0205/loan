package com.loan.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.api.dto.PageResult;
import com.loan.audit.entity.MatchRuleLog;
import com.loan.audit.entity.MatchTrace;
import com.loan.audit.mapper.MatchRuleLogMapper;
import com.loan.audit.mapper.MatchTraceMapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.engine.catalog.RuleCatalog;
import com.loan.infrastructure.security.HashUtils;
import com.loan.personal.entity.PersonalProfile;
import com.loan.personal.mapper.PersonalProfileMapper;
import com.loan.rule.entity.Rule;
import com.loan.rule.mapper.RuleMapper;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 审计服务：按 traceUuid 查询匹配全链路时间线（含命中产品与规则明细）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final MatchTraceMapper matchTraceMapper;
    private final MatchRuleLogMapper matchRuleLogMapper;
    private final ClientProfileMapper clientProfileMapper;
    private final PersonalProfileMapper personalProfileMapper;
    private final RuleMapper ruleMapper;

    /**
     * 按 traceUuid 查询审计时间线。
     *
     * @param traceUuid 链路 UUID
     * @return { trace, rules }，不存在返回 null
     */
    public Map<String, Object> queryByTraceUuid(String traceUuid) {
        if (!StringUtils.hasText(traceUuid)) {
            return null;
        }
        MatchTrace trace = matchTraceMapper.selectOne(
                new LambdaQueryWrapper<MatchTrace>().eq(MatchTrace::getTraceUuid, traceUuid));
        if (trace == null) {
            return null;
        }
        List<MatchRuleLog> rules = matchRuleLogMapper.selectList(
                new LambdaQueryWrapper<MatchRuleLog>()
                        .eq(MatchRuleLog::getTraceId, trace.getId())
                        .orderByAsc(MatchRuleLog::getId));
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, ClientProfile> profiles = profiles(Collections.singletonList(trace));
        Map<String, PersonalProfile> personalProfiles = personalProfiles(profiles.keySet());
        result.put("trace", toTraceView(trace, profiles.get(trace.getClientProfileCode()),
                personalProfiles.get(trace.getClientProfileCode())));
        result.put("rules", toRuleViews(rules));
        return result;
    }

    /**
     * 分页查询审计记录（按时间倒序，支持多维过滤）。
     *
     * @param traceUuid     链路 UUID（模糊）
     * @param customerGroup 客群（ENTERPRISE / PERSONAL）
     * @param totalResult   匹配结果（PASS / CONDITION / REJECT / ...）
     * @param mismatchFlag  异常标记（1=存在异常分支）
     * @param startTime     开始时间（yyyy-MM-dd HH:mm:ss）
     * @param endTime       结束时间
     * @param page          页码
     * @param size          每页大小
     * @return 审计记录分页
     */
    public PageResult<Map<String, Object>> page(String traceUuid, String keyword,
                                       String customerGroup, String totalResult,
                                       Integer mismatchFlag, String startTime, String endTime,
                                       int page, int size) {
        LambdaQueryWrapper<MatchTrace> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(traceUuid)) {
            wrapper.like(MatchTrace::getTraceUuid, traceUuid.trim());
        }
        if (StringUtils.hasText(keyword)) {
            Set<String> clientCodes = matchingClientCodes(keyword.trim());
            if (clientCodes.isEmpty()) {
                return PageResult.build(page, size, 0L, Collections.emptyList());
            }
            wrapper.in(MatchTrace::getClientProfileCode, clientCodes);
        }
        if (StringUtils.hasText(customerGroup)) {
            wrapper.eq(MatchTrace::getCustomerGroup, customerGroup.trim());
        }
        if (StringUtils.hasText(totalResult)) {
            wrapper.eq(MatchTrace::getTotalResult, totalResult.trim());
        }
        if (mismatchFlag != null) {
            wrapper.eq(MatchTrace::getMismatchFlag, mismatchFlag);
        }
        if (StringUtils.hasText(startTime)) {
            wrapper.ge(MatchTrace::getExecutedAt, startTime.trim());
        }
        if (StringUtils.hasText(endTime)) {
            wrapper.le(MatchTrace::getExecutedAt, endTime.trim());
        }
        wrapper.orderByDesc(MatchTrace::getExecutedAt);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<MatchTrace> result =
                matchTraceMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
        Map<String, ClientProfile> profiles = profiles(result.getRecords());
        Map<String, PersonalProfile> personals = personalProfiles(profiles.keySet());
        List<Map<String, Object>> records = result.getRecords().stream()
                .map(trace -> toTraceView(trace, profiles.get(trace.getClientProfileCode()),
                        personals.get(trace.getClientProfileCode())))
                .collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /** 姓名/企业名/客户业务ID模糊，手机号/信用代码/身份证号按哈希精确查询。 */
    private Set<String> matchingClientCodes(String keyword) {
        Set<String> codes = new HashSet<>();
        LambdaQueryWrapper<ClientProfile> profileQuery = new LambdaQueryWrapper<ClientProfile>()
                .like(ClientProfile::getClientCode, keyword)
                .or().like(ClientProfile::getContactName, keyword)
                .or().like(ClientProfile::getEnterpriseName, keyword);
        if (keyword.matches("\\d{11}")) {
            profileQuery.or().eq(ClientProfile::getPhoneHash, HashUtils.sha256Hex(keyword));
        }
        if (keyword.matches("[0-9A-Za-z]{18}")) {
            profileQuery.or().eq(ClientProfile::getCreditCodeHash, HashUtils.sha256Hex(keyword));
        }
        clientProfileMapper.selectList(profileQuery).stream().map(ClientProfile::getClientCode)
                .filter(StringUtils::hasText).forEach(codes::add);
        if (keyword.matches("\\d{17}[0-9Xx]")) {
            personalProfileMapper.selectList(new LambdaQueryWrapper<PersonalProfile>()
                            .eq(PersonalProfile::getIdCardHash, HashUtils.sha256Hex(keyword.toUpperCase())))
                    .stream().map(PersonalProfile::getClientProfileCode)
                    .filter(StringUtils::hasText).forEach(codes::add);
        }
        return codes;
    }

    private Map<String, ClientProfile> profiles(List<MatchTrace> traces) {
        List<String> codes = traces.stream().map(MatchTrace::getClientProfileCode)
                .filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        if (codes.isEmpty()) return Collections.emptyMap();
        return clientProfileMapper.selectList(new LambdaQueryWrapper<ClientProfile>()
                        .in(ClientProfile::getClientCode, codes)).stream()
                .collect(Collectors.toMap(ClientProfile::getClientCode, Function.identity(), (a, b) -> a));
    }

    private Map<String, PersonalProfile> personalProfiles(Set<String> codes) {
        if (codes.isEmpty()) return Collections.emptyMap();
        return personalProfileMapper.selectList(new LambdaQueryWrapper<PersonalProfile>()
                        .in(PersonalProfile::getClientProfileCode, codes)).stream()
                .collect(Collectors.toMap(PersonalProfile::getClientProfileCode, Function.identity(), (a, b) -> a));
    }

    private Map<String, Object> toTraceView(MatchTrace trace, ClientProfile profile, PersonalProfile personal) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", trace.getId());
        view.put("traceUuid", trace.getTraceUuid());
        view.put("clientProfileCode", trace.getClientProfileCode());
        view.put("submissionNo", trace.getSubmissionNo());
        view.put("customerGroup", trace.getCustomerGroup());
        view.put("totalResult", trace.getTotalResult());
        view.put("hitCount", trace.getHitCount());
        view.put("stepCount", trace.getStepCount());
        view.put("durationMs", trace.getDurationMs());
        view.put("mismatchFlag", trace.getMismatchFlag());
        view.put("executedAt", trace.getExecutedAt());
        if (profile != null) {
            view.put("contactName", profile.getContactName());
            view.put("enterpriseName", profile.getEnterpriseName());
            view.put("contactPhoneMasked", DesensitizeUtils.phone(profile.getPhone()));
            if ("ENTERPRISE".equals(profile.getCustomerGroup())) {
                view.put("identityType", "统一社会信用代码");
                view.put("identityMasked", maskCreditCode(profile.getCreditCode()));
            } else if (personal != null) {
                view.put("identityType", "身份证号");
                view.put("identityMasked", DesensitizeUtils.idCard(personal.getIdCardNo()));
                if (!StringUtils.hasText(profile.getContactName())) view.put("contactName", personal.getRealName());
            }
        }
        return view;
    }

    private List<Map<String, Object>> toRuleViews(List<MatchRuleLog> rows) {
        Set<String> codes = rows.stream().map(MatchRuleLog::getRuleCode)
                .filter(StringUtils::hasText).collect(Collectors.toSet());
        Map<String, String> names = codes.isEmpty() ? new HashMap<>()
                : ruleMapper.selectList(new LambdaQueryWrapper<Rule>().in(Rule::getRuleCode, codes)).stream()
                .collect(Collectors.toMap(Rule::getRuleCode, Rule::getRuleName, (a, b) -> a));
        List<Map<String, Object>> result = new ArrayList<>();
        for (MatchRuleLog row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ruleCode", row.getRuleCode());
            item.put("ruleName", resolveRuleName(row.getRuleCode(), names));
            item.put("expression", row.getExpression());
            item.put("stepResult", row.getStepResult());
            item.put("handlerStepResult", row.getHandlerStepResult());
            item.put("mismatchFlag", row.getMismatchFlag());
            item.put("executedAt", row.getExecutedAt());
            result.add(item);
        }
        return result;
    }

    private String resolveRuleName(String code, Map<String, String> names) {
        if (StringUtils.hasText(names.get(code))) return names.get(code);
        RuleCatalog catalog = RuleCatalog.fromCode(code);
        return catalog == null ? code : catalog.getDisplayName();
    }

    private String maskCreditCode(String value) {
        if (!StringUtils.hasText(value) || value.length() < 8) return value;
        return value.substring(0, 4) + "**********" + value.substring(value.length() - 4);
    }
}
