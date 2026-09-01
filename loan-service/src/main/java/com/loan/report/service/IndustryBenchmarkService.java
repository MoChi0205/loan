package com.loan.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.report.entity.IndustryBenchmark;
import com.loan.report.mapper.IndustryBenchmarkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行业基准均值服务（#4a 行业均值数据源表化）。
 *
 * <p>职责：
 * <ul>
 *   <li>应用启动预热 ACTIVE 行到内存 {@code Map}（避免每次诊断都查库）；</li>
 *   <li>{@link #resolveIndustryCode(String)} 将用户自由文本行业归一化为行业编码
 *       （空→DEFAULT，精确匹配→双向包含匹配→DEFAULT 兜底）；</li>
 *   <li>{@link #avgByDimension(String, String)} 返回维度→均值映射，
 *       查不到返 {@code null} 让调用方落回硬编码常量（零回归）；</li>
 *   <li>{@link #metaByDimension(String, String)} 附带样本量 / 数据来源等元信息。</li>
 * </ul>
 *
 * <p>缓存刷新：本轮仅 {@code @PostConstruct} 预热 + {@link #refresh()} 手动方法；
 * 定时刷新后续接入 XXL-Job（红线 #2 禁 {@code @Scheduled}）。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class IndustryBenchmarkService {

    /** 维度编码常量（与 t_industry_benchmark.dimension_code 一致） */
    public static final String DIM_TAX_INTENSITY = "TAX_INTENSITY";
    public static final String DIM_INVOICE_SCALE = "INVOICE_SCALE";
    public static final String DIM_OPERATE_YEARS = "OPERATE_YEARS";
    public static final String DIM_FINANCIAL_HEALTH = "FINANCIAL_HEALTH";
    public static final String DIM_MATCH_OVERALL = "MATCH_OVERALL";

    /** 全行业兜底编码 */
    public static final String DEFAULT_CODE = "DEFAULT";

    /** 默认客群 */
    public static final String DEFAULT_GROUP = "ENTERPRISE";

    private final IndustryBenchmarkMapper mapper;

    /** 缓存：cacheKey(industryCode|customerGroup) -> 维度编码 -> 均值 */
    private final Map<String, Map<String, Integer>> cache = new ConcurrentHashMap<String, Map<String, Integer>>(16);

    /** 行业名称(小写) -> 行业编码（归一化索引） */
    private final Map<String, String> nameToCode = new ConcurrentHashMap<String, String>(32);

    /**
     * 应用启动预热（ACTIVE 行）。
     */
    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 重新加载 ACTIVE 行到内存（可被 XXL-Job / 管理端调用）。
     *
     * @return 加载行数
     */
    public int refresh() {
        List<IndustryBenchmark> all = mapper.selectList(
                new LambdaQueryWrapper<IndustryBenchmark>().eq(IndustryBenchmark::getStatus, "ACTIVE"));
        cache.clear();
        nameToCode.clear();
        for (IndustryBenchmark b : all) {
            if (b.getIndustryCode() == null || b.getDimensionCode() == null || b.getAvgScore() == null) {
                continue;
            }
            String group = StringUtils.hasText(b.getCustomerGroup()) ? b.getCustomerGroup() : DEFAULT_GROUP;
            String key = cacheKey(b.getIndustryCode(), group);
            Map<String, Integer> dims = cache.get(key);
            if (dims == null) {
                dims = new HashMap<String, Integer>();
                cache.put(key, dims);
            }
            dims.put(b.getDimensionCode(), b.getAvgScore());
            // 建立行业名称索引（小写，用于自由文本匹配）
            if (StringUtils.hasText(b.getIndustryName())) {
                nameToCode.put(b.getIndustryName().trim().toLowerCase(), b.getIndustryCode());
            }
        }
        return all.size();
    }

    /**
     * 将用户自由文本行业归一化为行业编码。
     *
     * <p>规则：① 空 → DEFAULT；② 精确匹配（忽略大小写、去空格）→ 命中；
     * ③ 双向包含匹配（名称含关键词 或 关键词含名称）→ 命中；
     * ④ 均不命中 → DEFAULT。
     *
     * @param rawIndustry 原始行业文本（可能为 null）
     * @return 行业编码
     */
    public String resolveIndustryCode(String rawIndustry) {
        if (!StringUtils.hasText(rawIndustry)) {
            return DEFAULT_CODE;
        }
        String r = rawIndustry.trim().toLowerCase();
        // ② 精确匹配
        if (nameToCode.containsKey(r)) {
            return nameToCode.get(r);
        }
        // ③ 双向包含匹配
        for (Map.Entry<String, String> entry : nameToCode.entrySet()) {
            String name = entry.getKey();
            if (name.contains(r) || r.contains(name)) {
                return entry.getValue();
            }
        }
        // ④ 兜底 DEFAULT
        return DEFAULT_CODE;
    }

    /**
     * 取某行业（自由文本）+ 客群的多维均值映射。
     *
     * @param rawIndustry    原始行业文本
     * @param customerGroup 客群（可选，空按 ENTERPRISE）
     * @return 维度编码 -> 均值；查不到（含 DEFAULT 缺数据）返回 {@code null}
     */
    public Map<String, Integer> avgByDimension(String rawIndustry, String customerGroup) {
        String code = resolveIndustryCode(rawIndustry);
        String group = StringUtils.hasText(customerGroup) ? customerGroup : DEFAULT_GROUP;
        return cache.get(cacheKey(code, group));
    }

    /**
     * 取某行业 + 客群的多维均值元信息（样本量 / 数据来源 / 统计周期）。
     *
     * @param rawIndustry    原始行业文本
     * @param customerGroup 客群（可选）
     * @return 维度编码 -> 元信息 Map（含 sampleSize / dataSource / statPeriod / avgScore）
     */
    public Map<String, Map<String, Object>> metaByDimension(String rawIndustry, String customerGroup) {
        String code = resolveIndustryCode(rawIndustry);
        String group = StringUtils.hasText(customerGroup) ? customerGroup : DEFAULT_GROUP;
        List<IndustryBenchmark> rows = mapper.selectList(
                new LambdaQueryWrapper<IndustryBenchmark>()
                        .eq(IndustryBenchmark::getIndustryCode, code)
                        .eq(IndustryBenchmark::getCustomerGroup, group)
                        .eq(IndustryBenchmark::getStatus, "ACTIVE"));
        Map<String, Map<String, Object>> result = new LinkedHashMap<String, Map<String, Object>>();
        for (IndustryBenchmark b : rows) {
            Map<String, Object> meta = new LinkedHashMap<String, Object>();
            meta.put("avgScore", b.getAvgScore());
            meta.put("sampleSize", b.getSampleSize());
            meta.put("dataSource", b.getDataSource());
            meta.put("statPeriod", b.getStatPeriod());
            meta.put("industryCode", b.getIndustryCode());
            meta.put("industryName", b.getIndustryName());
            result.put(b.getDimensionCode(), meta);
        }
        return result;
    }

    /**
     * 预热缓存中是否已存在某行业的 ACTIVE 数据。
     */
    public boolean isLoaded() {
        return !cache.isEmpty();
    }

    /**
     * 供单元测试 / 外部注入：直接覆盖内存缓存（不查库）。
     */
    public void putForTest(String industryCode, String customerGroup, Map<String, Integer> dims) {
        cache.put(cacheKey(industryCode, customerGroup), new HashMap<String, Integer>(dims));
    }

    private String cacheKey(String code, String group) {
        return code + "|" + group;
    }

    /** 维度编码常量（列表，便于调用方遍历） */
    public static List<String> allDimensionCodes() {
        List<String> list = new ArrayList<String>();
        list.add(DIM_TAX_INTENSITY);
        list.add(DIM_INVOICE_SCALE);
        list.add(DIM_OPERATE_YEARS);
        list.add(DIM_FINANCIAL_HEALTH);
        list.add(DIM_MATCH_OVERALL);
        return list;
    }
}
