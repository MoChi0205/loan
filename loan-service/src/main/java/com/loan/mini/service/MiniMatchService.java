package com.loan.mini.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.loan.api.dto.PageResult;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.client.service.ClientAllocationService;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.mini.dto.MiniMatchResult;
import com.loan.mini.dto.RuleHit;
import com.loan.report.entity.ClientScreening;
import com.loan.report.entity.ScreeningProduct;
import com.loan.report.mapper.ClientScreeningMapper;
import com.loan.report.mapper.ScreeningProductMapper;
import com.loan.report.service.IndustryBenchmarkService;
import com.loan.report.service.ReportQueryService;
import com.loan.screening.service.ScreeningService;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import com.loan.product.entity.BankChannel;
import com.loan.product.entity.BankProduct;
import com.loan.product.mapper.BankChannelMapper;
import com.loan.product.mapper.BankProductMapper;
import com.loan.submission.entity.ClientSubmission;
import com.loan.submission.mapper.ClientSubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 小程序端匹配与报告：客户提交经营事实 → 引擎匹配 → 生成报告；我的报告列表/详情。
 *
 * <p>P0-4 增强：未认证客户拦截（无企业信用代码且无个人认证记录 → FORBIDDEN）；
 * 匹配结果按评审决策对客脱敏（仅产品数量 + 用户评级 + 规则说明，不含产品明细）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class MiniMatchService {

    private final ScreeningService screeningService;
    private final ClientScreeningMapper screeningMapper;
    private final ClientProfileMapper clientProfileMapper;
    private final com.loan.personal.service.PersonalProfileService personalProfileService;
    private final ReportQueryService reportQueryService;
    private final StaffMapper staffMapper;
    private final ScreeningProductMapper screeningProductMapper;
    private final BankProductMapper bankProductMapper;
    private final BankChannelMapper bankChannelMapper;
    private final ClientSubmissionMapper clientSubmissionMapper;
    private final IndustryBenchmarkService industryBenchmarkService;
    private final ClientAllocationService clientAllocationService;

    /**
     * 客户发起匹配（兼容入口，返回报告编号；同样执行未认证拦截）。
     *
     * @param clientCode 客户编码
     * @param facts      经营事实（企业：annualTaxAmount/annualInvoiceAmount/foundYears/industry）
     * @param operator   操作人（客户姓名）
     * @return 报告编号
     */
    public String run(String clientCode, Map<String, Object> facts, String operator, String applyCity) {
        requireAuthenticated(clientCode);
        if (facts == null || facts.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写经营事实");
        }
        return screeningService.run(clientCode, facts, operator, applyCity);
    }

    /**
     * 客户发起匹配（P0-4 主链路）：未认证拦截 → 幂等落提交单/facts → 引擎匹配 → 脱敏结果。
     *
     * @param clientCode     客户编码
     * @param facts          经营事实（key 即 t_rule.field_code）
     * @param operator       操作人（客户姓名）
     * @param applyCity      申请城市（必填，市一级名称）
     * @param clientSubmitId 客户端幂等键（可选，同键不重复落库）
     * @return 脱敏匹配结果（不含产品名/银行名/额度/利率明细）
     */
    @Transactional(rollbackFor = Exception.class)
    public MiniMatchResult runForMini(String clientCode, Map<String, Object> facts, LoanUser user,
                                      String applyCity, String clientSubmitId) {
        requireAuthenticated(clientCode);
        if (facts == null || facts.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写经营事实");
        }
        if (!StringUtils.hasText(applyCity)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择申请城市");
        }
        // P0-3：员工替客匹配——目标客户已归属他人且当前员工非归属人时，必须已通过归属审批
        if (user != null && LoanUser.TYPE_STAFF.equals(user.getUserType())) {
            ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                    .eq(ClientProfile::getClientCode, clientCode).last("limit 1"));
            if (client != null && StringUtils.hasText(client.getOwnerStaffCode())
                    && !client.getOwnerStaffCode().equals(user.getUserNo())
                    && !clientAllocationService.hasApprovedOwnership(clientCode, user.getUserNo())) {
                throw new BusinessException(ResultCode.FORBIDDEN,
                        "该客户已归属他人，请先在「客户分配」中发起归属审批后再匹配");
            }
        }
        String operator = user == null ? "客户" : user.getName();
        String reportNo = screeningService.run(clientCode, facts, operator, applyCity, clientSubmitId);
        Map<String, Object> detail = reportQueryService.miniDetail(reportNo, clientCode);
        MiniMatchResult result = new MiniMatchResult();
        result.setReportNo((String) detail.get("reportNo"));
        result.setGrade((String) detail.get("grade"));
        result.setTotalResult((String) detail.get("totalResult"));
        result.setProductCount(detail.get("productCount") == null ? 0
                : ((Number) detail.get("productCount")).intValue());
        result.setRating((String) detail.get("rating"));
        result.setRuleLogs(toRuleHits(detail.get("ruleLogs")));
        return result;
    }

    /**
     * 我的报告列表（客户视角）。
     *
     * @param clientCode 客户编码
     * @param page       页码
     * @param size       每页大小
     * @return 报告分页
     */
    public PageResult<Map<String, Object>> myReports(String clientCode, int page, int size) {
        LambdaQueryWrapper<ClientScreening> wrapper = new LambdaQueryWrapper<ClientScreening>()
                .eq(ClientScreening::getClientProfileCode, clientCode)
                .orderByDesc(ClientScreening::getCreatedAt);
        Page<ClientScreening> result = screeningMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reportNo", s.getReportNo());
            m.put("grade", s.getGrade());
            m.put("bankCount", s.getBankCount());
            m.put("productCount", s.getProductCount());
            m.put("passCount", s.getPassCount());
            m.put("conditionCount", s.getConditionCount());
            m.put("rejectCount", s.getRejectCount());
            m.put("status", s.getStatus());
            m.put("createdAt", s.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 我的匹配历史（同报告列表，别名接口）。
     *
     * @param clientCode 客户编码
     * @param page       页码
     * @param size       每页大小
     * @return 匹配历史分页
     */
    public PageResult<Map<String, Object>> myMatches(String clientCode, int page, int size) {
        return myReports(clientCode, page, size);
    }

    /**
     * 报告详情（校验归属，返回脱敏内容，委托 {@link ReportQueryService}）。
     *
     * @param reportNo   报告编号
     * @param clientCode 客户编码
     * @return 报告详情（不含产品明细）
     */
    public Map<String, Object> reportDetail(String reportNo, String clientCode) {
        return reportQueryService.miniDetail(reportNo, clientCode);
    }

    /* ==================== C3 / C11：报告列表（角色二分 + 四维查询） ==================== */

    /**
     * 客户报告列表（C3）：仅按日期区间筛选自己的报告。
     *
     * <p>客户无权跨用户检索，本方法<b>不接受</b>任何客户标识类参数，
     * 强制以当前 clientCode 为过滤条件，防止越权查看他人报告。
     *
     * @param clientCode 客户编码（来自登录态，非前端传参）
     * @param page       页码
     * @param size       每页大小
     * @param dateRange  日期区间：today / 7d / 30d / all（空按 all 处理）
     * @return 报告分页
     */
    public PageResult<Map<String, Object>> myReportsByDate(String clientCode, int page, int size, String dateRange) {
        LambdaQueryWrapper<ClientScreening> wrapper = new LambdaQueryWrapper<ClientScreening>()
                .eq(ClientScreening::getClientProfileCode, clientCode);
        LocalDateTime from = resolveDateFrom(dateRange);
        if (from != null) {
            wrapper.ge(ClientScreening::getCreatedAt, from);
        }
        wrapper.orderByDesc(ClientScreening::getCreatedAt);
        Page<ClientScreening> result = screeningMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.build(page, size, result.getTotal(), toReportRows(result.getRecords()));
    }

    /**
     * 企业员工报告列表（C11 四维查询）：全量可见 + 手机号/客户姓名 + 公司名/信用代码 + 归属 + 日期。
     *
     * <p>实现采用两步查询：先在 t_client_profile 上按客户维度条件筛出 clientCode 集合，
     * 再以 {@code IN} 条件查 t_client_screening，避免跨表联表带来的索引失效。
     *
     * <p><b>敏感字段处理：</b>手机号与统一社会信用代码在库内以 SHA-256 摘要存储
     * （phone_hash / credit_code_hash），故精确匹配前需对入参做同样的摘要计算，
     * 不以明文比对，也不在 SQL 中出现明文。
     *
     * @param page      页码
     * @param size      每页大小
     * @param query     手机号（精确）或客户姓名（模糊）
     * @param credit    公司名称（模糊）或统一社会信用代码（精确，18 位）
     * @param owner     归属：me（归属到我）/ staff（归属到员工）/ all（全量）
     * @param dateRange 日期区间
     * @param staffCode 当前员工工号（用于 owner=me 判定）
     * @return 报告分页
     */
    public PageResult<Map<String, Object>> allReports(int page, int size, String query, String credit,
                                                      String owner, String dateRange, String staffCode) {
        // 第一步：按客户维度条件筛 clientCode（无任何条件时跳过，直接全量查报告）
        boolean needClientFilter = StringUtils.hasText(query) || StringUtils.hasText(credit)
                || "me".equals(owner) || "staff".equals(owner);
        List<String> clientCodes = null;
        if (needClientFilter) {
            LambdaQueryWrapper<ClientProfile> cw = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(query)) {
                String q = query.trim();
                if (q.matches("\\d{6,}")) {
                    // 纯数字按手机号精确匹配（摘要比对）
                    cw.eq(ClientProfile::getPhoneHash, sha256(q));
                } else {
                    // 其余按客户姓名模糊匹配
                    cw.like(ClientProfile::getContactName, q);
                }
            }
            if (StringUtils.hasText(credit)) {
                String c = credit.trim();
                if (c.matches("[0-9A-Za-z]{18}")) {
                    // 18 位按统一社会信用代码精确匹配（摘要比对）
                    cw.eq(ClientProfile::getCreditCodeHash, sha256(c));
                } else {
                    cw.like(ClientProfile::getEnterpriseName, c);
                }
            }
            if ("me".equals(owner)) {
                cw.eq(ClientProfile::getOwnerStaffCode, staffCode);
            } else if ("staff".equals(owner)) {
                cw.isNotNull(ClientProfile::getOwnerStaffCode);
            }
            List<ClientProfile> clients = clientProfileMapper.selectList(cw);
            clientCodes = clients.stream().map(ClientProfile::getClientCode).collect(Collectors.toList());
            if (clientCodes.isEmpty()) {
                return PageResult.build(page, size, 0L, new ArrayList<Map<String, Object>>());
            }
        }

        // 第二步：查报告（客户集合 + 日期区间）
        LambdaQueryWrapper<ClientScreening> wrapper = new LambdaQueryWrapper<>();
        if (clientCodes != null) {
            wrapper.in(ClientScreening::getClientProfileCode, clientCodes);
        }
        LocalDateTime from = resolveDateFrom(dateRange);
        if (from != null) {
            wrapper.ge(ClientScreening::getCreatedAt, from);
        }
        wrapper.orderByDesc(ClientScreening::getCreatedAt);
        Page<ClientScreening> result = screeningMapper.selectPage(new Page<>(page, size), wrapper);

        // 第三步：补客户维度展示字段（归属人 / 企业名 / 手机掩码）
        Map<String, ClientProfile> profileMap = loadProfiles(result.getRecords());
        List<String> staffCodes = result.getRecords().stream()
                .map(ClientScreening::getClientProfileCode)
                .map(profileMap::get)
                .filter(p -> p != null && StringUtils.hasText(p.getOwnerStaffCode()))
                .map(ClientProfile::getOwnerStaffCode)
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> staffNameMap = loadStaffNames(staffCodes);
        List<Map<String, Object>> rows = result.getRecords().stream().map(s -> {
            Map<String, Object> m = toReportRow(s);
            ClientProfile p = profileMap.get(s.getClientProfileCode());
            if (p != null) {
                m.put("clientName", p.getContactName());
                m.put("entName", p.getEnterpriseName());
                m.put("contactPhone", maskPhone(p.getPhone()));
                m.put("ownerStaffCode", p.getOwnerStaffCode());
                m.put("ownerStaffName", staffNameMap.getOrDefault(p.getOwnerStaffCode(), p.getOwnerStaffCode()));
            }
            return m;
        }).collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), rows);
    }

    /* ==================== C4：报告命中的银行产品 ==================== */

    /**
     * 报告命中的银行产品列表（C4）：企业员工陪访解读用。
     *
     * <p>数据源 t_screening_product（ScreeningService 落库）→ 关联 t_bank_product / t_bank_channel
     * 补齐产品名 / 银行名 / 额度 / 利率 / 期限。产品名与银行名仅员工可见，对客脱敏。
     *
     * @param reportNo 报告编号
     * @return 命中产品列表（无明细时为空）
     */
    public List<Map<String, Object>> reportProducts(String reportNo) {
        List<ScreeningProduct> list = screeningProductMapper.selectList(
                new LambdaQueryWrapper<ScreeningProduct>()
                        .eq(ScreeningProduct::getReportNo, reportNo)
                        .orderByDesc(ScreeningProduct::getMatchScore));
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        // 一次 IN 查询补齐产品与渠道（防 N+1）
        List<String> productCodes = list.stream()
                .map(ScreeningProduct::getProductCode).filter(StringUtils::hasText).distinct()
                .collect(Collectors.toList());
        Map<String, BankProduct> productMap = new LinkedHashMap<>();
        if (!productCodes.isEmpty()) {
            for (BankProduct p : bankProductMapper.selectList(
                    new LambdaQueryWrapper<BankProduct>().in(BankProduct::getProductCode, productCodes))) {
                productMap.put(p.getProductCode(), p);
            }
        }
        List<String> channelCodes = productMap.values().stream()
                .map(BankProduct::getBankChannelCode).filter(StringUtils::hasText).distinct()
                .collect(Collectors.toList());
        Map<String, BankChannel> channelMap = new LinkedHashMap<>();
        if (!channelCodes.isEmpty()) {
            for (BankChannel bc : bankChannelMapper.selectList(
                    new LambdaQueryWrapper<BankChannel>().in(BankChannel::getChannelCode, channelCodes))) {
                channelMap.put(bc.getChannelCode(), bc);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScreeningProduct sp : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productCode", sp.getProductCode());
            m.put("hitResult", sp.getHitResult());
            m.put("matchScore", sp.getMatchScore());
            BankProduct p = productMap.get(sp.getProductCode());
            if (p != null) {
                m.put("productName", p.getProductName());
                BankChannel bc = channelMap.get(p.getBankChannelCode());
                m.put("bankName", bc == null ? null : bc.getBankName());
                m.put("amountRange", rangeText(p.getAmountMin(), p.getAmountMax(), "万"));
                m.put("rate", rangeText(p.getRateMin(), p.getRateMax(), "%"));
                m.put("term", termText(p.getTermMin(), p.getTermMax()));
            }
            rows.add(m);
        }
        return rows;
    }

    /** 数值区间文案：min-max + 单位（与 MiniProductService 同口径，空值与相等值统一处理） */
    private String rangeText(java.math.BigDecimal min, java.math.BigDecimal max, String unit) {
        if (min == null && max == null) return null;
        if (min != null && max != null && min.compareTo(max) == 0) {
            return min.stripTrailingZeros().toPlainString() + unit;
        }
        String lo = min == null ? "?" : min.stripTrailingZeros().toPlainString();
        String hi = max == null ? "?" : max.stripTrailingZeros().toPlainString();
        return lo + "-" + hi + unit;
    }

    /** 期限区间文案：12-36个月（单值：12个月） */
    private String termText(Integer min, Integer max) {
        if (min == null && max == null) return null;
        if (min != null && min.equals(max)) return min + "个月";
        String lo = min == null ? "?" : String.valueOf(min);
        String hi = max == null ? "?" : String.valueOf(max);
        return lo + "-" + hi + "个月";
    }

    /* ==================== C5：经营诊断 ==================== */

    /**
     * 企业经营诊断（C5）：基于报告与提交的经营事实生成 KPI / 建议 / 风险 / 历年数据 / 多维统计。
     *
     * <p>合规口径：话术规避承诺性表述（对齐评审决策 08-28）；
     * 数据源 = t_client_submission.data_json（facts）+ t_client_screening（匹配结果）。
     * 无历年数据源时 yearData 返回当年 1 行；industryAvg 为预设行业基准（行业数据接入前用常量）。
     *
     * @param reportNo   报告编号
     * @param clientCode 客户编码（客户视角必传用于归属校验；员工视角传 null 表示不校验）
     * @return 诊断结果
     */
    public Map<String, Object> reportDiagnosis(String reportNo, String clientCode) {
        ClientScreening screening = screeningMapper.selectOne(new LambdaQueryWrapper<ClientScreening>()
                .eq(ClientScreening::getReportNo, reportNo));
        if (screening == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "报告不存在");
        }
        if (clientCode != null && !clientCode.equals(screening.getClientProfileCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能查看自己的报告");
        }
        ClientSubmission submission = latestSubmission(screening.getMatchTraceUuid());
        Map<String, Object> facts = loadFacts(submission);
        // 客群：优先取提交单维度，缺失兜底 ENTERPRISE（零回归）
        String customerGroup = (submission != null && StringUtils.hasText(submission.getCustomerGroup()))
                ? submission.getCustomerGroup() : "ENTERPRISE";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportNo", reportNo);
        data.put("generatedAt", LocalDateTime.now());
        // materialVersion：无 OCR 回灌时为 v1；有 OCR 回灌时按 _ocrMeta.version 递增（T2）
        data.put("materialVersion", resolveMaterialVersion(facts));
        data.put("kpi", buildKpis(screening, facts));
        data.put("suggestions", buildSuggestions(screening, facts));
        data.put("risks", buildRisks(facts));
        data.put("yearData", buildYearData(facts));
        data.put("dimensions", buildDimensions(screening, facts, customerGroup));
        return data;
    }

    /* ==================== C5 诊断算法私有方法 ==================== */

    /** 读取提交单对应的经营事实（t_client_submission.data_json） */
    private Map<String, Object> loadFacts(ClientSubmission submission) {
        if (submission == null || !StringUtils.hasText(submission.getDataJson())) {
            return new LinkedHashMap<>();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(submission.getDataJson(),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /** 取报告关联的最新提交单（按 matchTraceUuid） */
    private ClientSubmission latestSubmission(String matchTraceUuid) {
        if (!StringUtils.hasText(matchTraceUuid)) {
            return null;
        }
        List<ClientSubmission> subs = clientSubmissionMapper.selectList(
                new LambdaQueryWrapper<ClientSubmission>()
                        .eq(ClientSubmission::getMatchTraceNo, matchTraceUuid)
                        .orderByDesc(ClientSubmission::getCreatedAt).last("limit 1"));
        return subs.isEmpty() ? null : subs.get(0);
    }

    /** materialVersion 由 data_json 内嵌的 _ocrMeta.version 派生；无 OCR 回灌时为 v1（T2） */
    @SuppressWarnings("unchecked")
    private String resolveMaterialVersion(Map<String, Object> facts) {
        Object ocrMeta = facts == null ? null : facts.get("_ocrMeta");
        if (ocrMeta instanceof Map) {
            Object v = ((Map<?, ?>) ocrMeta).get("version");
            if (v instanceof Number) {
                return "v" + ((Number) v).intValue();
            }
        }
        return "v1";
    }

    /** facts 数值提取：Integer/Long/Double/BigDecimal/String 统一转 double，异常归 0 */
    private double num(Object v) {
        if (v == null) return 0;
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 元 → 万元文本（最多 2 位小数，去尾零） */
    private String wan(double yuan) {
        if (yuan <= 0) return "0";
        java.math.BigDecimal w = java.math.BigDecimal.valueOf(yuan / 10000.0)
                .setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        return w.toPlainString();
    }

    /** KPI：年纳税 / 年开票 / 成立年限 / 命中产品数 */
    private List<Map<String, Object>> buildKpis(ClientScreening screening, Map<String, Object> facts) {
        double tax = num(facts.get("annualTaxAmount"));
        double invoice = num(facts.get("annualInvoiceAmount"));
        double years = num(facts.get("foundYears"));
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(kpi("年纳税额", wan(tax) + " 万元", amountTone(tax), "近一年纳税总额（申报口径）"));
        list.add(kpi("年开票额", wan(invoice) + " 万元", amountTone(invoice), "近一年增值税开票总额"));
        list.add(kpi("成立年限", ((int) years) + " 年", years >= 3 ? "success" : "warning",
                "企业注册经营时长"));
        list.add(kpi("命中产品数", (screening.getProductCount() == null ? 0 : screening.getProductCount()) + " 款",
                screening.getProductCount() != null && screening.getProductCount() >= 3 ? "success" : "neutral",
                "当前可进件匹配数量"));
        return list;
    }

    private Map<String, Object> kpi(String label, String value, String tone, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        m.put("tone", tone);
        m.put("desc", desc);
        return m;
    }

    private String amountTone(double amount) {
        if (amount >= 100000) return "success";
        if (amount >= 10000) return "warning";
        return "danger";
    }

    /** 经营建议：条件触发模板文案（规避承诺性表述） */
    private List<Map<String, Object>> buildSuggestions(ClientScreening screening, Map<String, Object> facts) {
        double tax = num(facts.get("annualTaxAmount"));
        double invoice = num(facts.get("annualInvoiceAmount"));
        double years = num(facts.get("foundYears"));
        int productCount = screening.getProductCount() == null ? 0 : screening.getProductCount();
        List<Map<String, Object>> list = new ArrayList<>();
        if (years > 0 && years < 2) {
            list.add(suggestion("经营时长", "info", "成立年限较短，建议补充连续经营佐证材料，以提升银行准入核验认可度。"));
        }
        if (tax > 0 && tax < 10000) {
            list.add(suggestion("纳税数据", "warning", "纳税规模偏低，建议梳理近 12 个月完税凭证，评估是否满足普惠类产品纳税门槛。"));
        }
        if (tax > 0 && invoice > tax * 8) {
            list.add(suggestion("票据管理", "warning", "开票与纳税比例偏高，建议核对票据与申报口径一致性，避免进件核验补料。"));
        }
        if (productCount >= 3) {
            list.add(suggestion("多产品比价", "success", "当前可匹配产品较丰富，建议结合额度、利率、期限综合比选，提升获批概率。"));
        }
        if (list.isEmpty()) {
            list.add(suggestion("资料完备", "success", "建议按清单一次性提交经营材料，缩短进件审批周期。"));
        }
        return list;
    }

    private Map<String, Object> suggestion(String type, String tagType, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("tagType", tagType);
        m.put("content", content);
        return m;
    }

    /** 风险提示：阈值触发 */
    private List<Map<String, Object>> buildRisks(Map<String, Object> facts) {
        double tax = num(facts.get("annualTaxAmount"));
        double invoice = num(facts.get("annualInvoiceAmount"));
        double years = num(facts.get("foundYears"));
        List<Map<String, Object>> list = new ArrayList<>();
        if (tax > 0 && tax < 10000) {
            list.add(risk("高", "年纳税额不足 1 万元，多数普惠信贷产品设有纳税门槛，可进件范围受限。"));
        }
        if (years > 0 && years < 1) {
            list.add(risk("高", "成立不足 1 年，部分银行要求经营满一定期限，需重点筛选支持新设企业的产品。"));
        }
        if (tax == 0 && invoice == 0) {
            list.add(risk("中", "未提交纳税 / 开票数据，匹配结果基于有限事实，建议补充材料后重新匹配。"));
        }
        if (list.isEmpty()) {
            list.add(risk("中", "材料时效性可能影响核验，请确认上传的经营材料为最新版本。"));
        }
        return list;
    }

    private Map<String, Object> risk(String level, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level);
        m.put("content", content);
        return m;
    }

    /** 历年营业数据：无历年数据源，返回当年 1 行（利润无数据源显示 —） */
    private List<Map<String, Object>> buildYearData(Map<String, Object> facts) {
        double tax = num(facts.get("annualTaxAmount"));
        double invoice = num(facts.get("annualInvoiceAmount"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("year", String.valueOf(LocalDateTime.now().getYear()));
        row.put("revenue", invoice > 0 ? wan(invoice) : "—");
        row.put("tax", tax > 0 ? wan(tax) : "—");
        row.put("invoice", invoice > 0 ? wan(invoice) : "—");
        row.put("profit", "—");
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(row);
        return list;
    }

    /** 多维统计：5 维度归一化 0-100 + 行业均值（#4a 表化；查不到落回硬编码常量 45/50/55/60/55） */
    private List<Map<String, Object>> buildDimensions(ClientScreening screening, Map<String, Object> facts, String customerGroup) {
        double tax = num(facts.get("annualTaxAmount"));
        double invoice = num(facts.get("annualInvoiceAmount"));
        double years = num(facts.get("foundYears"));
        int productCount = screening.getProductCount() == null ? 0 : screening.getProductCount();
        String rawIndustry = facts.get("industry") == null ? null : String.valueOf(facts.get("industry"));
        // 查行业基准均值表；查不到（含 DEFAULT 缺数据）返回 null，逐维度落回硬编码常量
        Map<String, Integer> avgMap = industryBenchmarkService.avgByDimension(rawIndustry, customerGroup);
        boolean fromTable = avgMap != null;
        Integer taxAvg = pick(avgMap, IndustryBenchmarkService.DIM_TAX_INTENSITY, 45);
        Integer invoiceAvg = pick(avgMap, IndustryBenchmarkService.DIM_INVOICE_SCALE, 50);
        Integer yearsAvg = pick(avgMap, IndustryBenchmarkService.DIM_OPERATE_YEARS, 55);
        Integer healthAvg = pick(avgMap, IndustryBenchmarkService.DIM_FINANCIAL_HEALTH, 60);
        Integer matchAvg = pick(avgMap, IndustryBenchmarkService.DIM_MATCH_OVERALL, 55);
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(dimension("纳税强度", levelScore(tax), taxAvg, IndustryBenchmarkService.DIM_TAX_INTENSITY, fromTable));
        list.add(dimension("开票规模", levelScore(invoice), invoiceAvg, IndustryBenchmarkService.DIM_INVOICE_SCALE, fromTable));
        list.add(dimension("经营时长", years >= 5 ? 100 : years >= 3 ? 80 : years >= 1 ? 60 : years > 0 ? 40 : 20,
                yearsAvg, IndustryBenchmarkService.DIM_OPERATE_YEARS, fromTable));
        list.add(dimension("财务健康", (invoice > 0 && tax > 0 && invoice <= tax * 8) ? 80 : 50,
                healthAvg, IndustryBenchmarkService.DIM_FINANCIAL_HEALTH, fromTable));
        list.add(dimension("综合匹配", productCount >= 3 ? 90 : productCount == 2 ? 70 : productCount == 1 ? 50 : 30,
                matchAvg, IndustryBenchmarkService.DIM_MATCH_OVERALL, fromTable));
        return list;
    }

    /** 维度均值取值：表有则取表值，否则落回硬编码常量（零回归） */
    private Integer pick(Map<String, Integer> map, String key, int fallback) {
        if (map == null) {
            return fallback;
        }
        Integer v = map.get(key);
        return v == null ? fallback : v;
    }

    private Map<String, Object> dimension(String name, int value, int avg, String dimCode, boolean fromTable) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        m.put("industryAvg", avg);
        m.put("dimensionCode", dimCode);
        m.put("benchmarkSource", fromTable ? "DB" : "FALLBACK");
        return m;
    }

    /** 金额档位 → 0-100 */
    private int levelScore(double amount) {
        if (amount >= 500000) return 100;
        if (amount >= 100000) return 80;
        if (amount >= 10000) return 60;
        if (amount > 0) return 40;
        return 20;
    }

    /* ==================== 私有工具方法 ==================== */

    /** ClientScreening 列表 → 报告行（客户视角字段） */
    private List<Map<String, Object>> toReportRows(List<ClientScreening> list) {
        return list.stream().map(this::toReportRow).collect(Collectors.toList());
    }

    /** 单条报告 → Map（脱敏，不含产品明细） */
    private Map<String, Object> toReportRow(ClientScreening s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportNo", s.getReportNo());
        m.put("grade", s.getGrade());
        m.put("bankCount", s.getBankCount());
        m.put("productCount", s.getProductCount());
        m.put("passCount", s.getPassCount());
        m.put("conditionCount", s.getConditionCount());
        m.put("rejectCount", s.getRejectCount());
        m.put("status", s.getStatus());
        m.put("createdAt", s.getCreatedAt());
        return m;
    }

    /** 批量加载报告关联的客户档案 */
    private Map<String, ClientProfile> loadProfiles(List<ClientScreening> records) {
        List<String> codes = records.stream()
                .map(ClientScreening::getClientProfileCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (codes.isEmpty()) {
            return new LinkedHashMap<>();
        }
        List<ClientProfile> profiles = clientProfileMapper.selectList(
                new LambdaQueryWrapper<ClientProfile>().in(ClientProfile::getClientCode, codes));
        Map<String, ClientProfile> map = new LinkedHashMap<>();
        for (ClientProfile p : profiles) {
            map.put(p.getClientCode(), p);
        }
        return map;
    }

    /** 日期区间 → 起始时间（null 表示不限） */
    private LocalDateTime resolveDateFrom(String dateRange) {
        if (!StringUtils.hasText(dateRange) || "all".equals(dateRange)) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        switch (dateRange) {
            case "today": return now.toLocalDate().atStartOfDay();
            case "7d":    return now.minusDays(7);
            case "30d":   return now.minusDays(30);
            default:      return null;
        }
    }

    /** 手机号掩码：138****0001 */
    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 员工工号 → 姓名（单查，接 t_staff；查不到返回工号兜底）。
     */
    private String staffName(String staffCode) {
        if (!StringUtils.hasText(staffCode)) {
            return null;
        }
        List<Staff> list = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                .eq(Staff::getStaffCode, staffCode).last("limit 1"));
        if (list.isEmpty() || !StringUtils.hasText(list.get(0).getStaffName())) {
            return staffCode;
        }
        return list.get(0).getStaffName();
    }

    /** 员工工号集合 → 姓名 Map（批量，防 N+1；查不到保留工号） */
    private Map<String, String> loadStaffNames(List<String> staffCodes) {
        Map<String, String> map = new LinkedHashMap<>();
        if (staffCodes == null || staffCodes.isEmpty()) {
            return map;
        }
        List<Staff> staffs = staffMapper.selectList(new LambdaQueryWrapper<Staff>()
                .in(Staff::getStaffCode, staffCodes));
        for (Staff s : staffs) {
            map.put(s.getStaffCode(), StringUtils.hasText(s.getStaffName()) ? s.getStaffName() : s.getStaffCode());
        }
        // 未命中的工号原样返回
        for (String code : staffCodes) {
            map.putIfAbsent(code, code);
        }
        return map;
    }

    /** SHA-256 摘要（与 AuthService / SmsService 保持一致的实现） */
    private String sha256(String raw) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.COMMON_ERROR, "摘要计算失败");
        }
    }

    /**
     * 未认证拦截：无企业信用代码（含企业名占位）且无个人认证记录 → FORBIDDEN。
     *
     * @param clientCode 客户编码
     */
    private void requireAuthenticated(String clientCode) {
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode));
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户档案不存在");
        }
        boolean enterpriseAuth = StringUtils.hasText(client.getCreditCodeHash())
                || StringUtils.hasText(client.getEnterpriseName());
        boolean personalAuth = personalProfileService.hasAuthenticated(client.getClientCode());
        if (!enterpriseAuth && !personalAuth) {
            throw new BusinessException(ResultCode.FORBIDDEN, "请先完成身份认证");
        }
    }

    /**
     * 规则日志（Map）转脱敏 DTO 列表。
     *
     * @param ruleLogs ReportQueryService 返回的规则日志列表
     * @return RuleHit 列表
     */
    @SuppressWarnings("unchecked")
    private List<RuleHit> toRuleHits(Object ruleLogs) {
        List<RuleHit> hits = new ArrayList<>();
        if (ruleLogs instanceof List) {
            for (Object o : (List<Object>) ruleLogs) {
                if (o instanceof Map) {
                    Map<String, Object> row = (Map<String, Object>) o;
                    RuleHit hit = new RuleHit();
                    hit.setRuleCode((String) row.get("ruleCode"));
                    hit.setExpression((String) row.get("expression"));
                    hit.setResult((String) row.get("result"));
                    hits.add(hit);
                }
            }
        }
        return hits;
    }
}
