package com.loan.serviceops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loan.api.dto.PageResult;
import com.loan.client.entity.ClientProfile;
import com.loan.common.ResultCode;
import com.loan.common.cache.UnifiedCacheService;
import com.loan.common.util.BizIdGenerator;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.report.dto.StaffAggregatedReport;
import com.loan.report.dto.StaffMaterialSummary;
import com.loan.report.entity.ClientScreening;
import com.loan.report.mapper.ClientScreeningMapper;
import com.loan.report.service.StaffReportAggregationService;
import com.loan.serviceops.dto.ClientInsightDTO;
import com.loan.serviceops.entity.ClientAppointment;
import com.loan.serviceops.entity.ClientFollowRecord;
import com.loan.serviceops.entity.ClientInsightSnapshot;
import com.loan.serviceops.entity.StaffOuting;
import com.loan.serviceops.mapper.ClientAppointmentMapper;
import com.loan.serviceops.mapper.ClientFollowRecordMapper;
import com.loan.serviceops.mapper.ClientInsightSnapshotMapper;
import com.loan.serviceops.mapper.StaffOutingMapper;
import com.loan.serviceops.security.ServiceOperationAccessPolicy;
import com.loan.staff.entity.Staff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客户画像版本快照服务：生成、读取与人工复核。
 *
 * <p>设计要点（对应 09-21 方案 §3.1）：
 * <ul>
 *   <li>画像按版本链保存，不覆盖旧值；同一客户最多一个 {@code CURRENT}；</li>
 *   <li>维度与来源链只对员工端放开，客户视角只读 {@code customerSummary}；</li>
 *   <li>风险提示只写经营层面的提示，不写成审批结论；客户摘要用固定模板，不含承诺性措辞；</li>
 *   <li>生成与复核都写入 {@code t_client_activity_event}，可回放。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientInsightService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_REVIEWED = "REVIEWED";
    public static final String STATUS_CURRENT = "CURRENT";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 当前无 AI 生成链路，画像维度由规则聚合产出。 */
    private static final String GENERATED_BY_RULE = "RULE";

    /** 超过该天数未跟进即提示。 */
    private static final int STALE_FOLLOW_DAYS = 30;

    /** 已到店口径：到店、服务中、已完成均视为实际到店。 */
    private static final Collection<String> ARRIVED_STATUSES =
            Arrays.asList("ARRIVED", "SERVING", "COMPLETED");

    /** 客户端摘要禁用的承诺性措辞。 */
    private static final List<String> COMMITTING_WORDS =
            Arrays.asList("准入", "通过", "命中", "保证", "审批", "包过");

    private final ClientInsightSnapshotMapper snapshotMapper;
    private final ClientAppointmentMapper appointmentMapper;
    private final StaffOutingMapper outingMapper;
    private final ClientFollowRecordMapper followMapper;
    private final ClientScreeningMapper screeningMapper;
    private final StaffReportAggregationService aggregationService;
    private final ServiceOperationScopeService scopeService;
    private final ClientActivityService activityService;
    private final ObjectMapper objectMapper;
    private final UnifiedCacheService cacheService;

    /**
     * 生成一版新的画像快照。
     *
     * <p>写权限比可见范围更窄：只有客户当前归属顾问本人可以生成，主管及以上通过复核环节介入。
     *
     * @param clientCode 客户业务编号
     * @param user       当前登录用户
     * @return 新快照业务编号
     */
    @Transactional(rollbackFor = Exception.class)
    public String generate(String clientCode, LoanUser user) {
        ClientProfile client = scopeService.requireClient(clientCode);
        scopeService.requireClientVisibleToStaff(user, client);
        if (!user.getUserNo().equals(client.getOwnerStaffCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅当前归属顾问可以生成客户画像");
        }

        int version = nextVersion(clientCode);
        StaffAggregatedReport aggregated = aggregateQuietly(client, user);
        Map<String, Object> analysis = aggregated == null ? null : aggregated.getBusinessAnalysis();
        Map<String, Object> counts = serviceCounts(clientCode);
        StaffMaterialSummary material = aggregated == null ? null : aggregated.getMaterialStatus();

        ClientInsightSnapshot snapshot = new ClientInsightSnapshot();
        snapshot.setSnapshotNo(BizIdGenerator.generate("insight"));
        snapshot.setClientCode(clientCode);
        snapshot.setReportNo(aggregated == null ? null : aggregated.getReportNo());
        snapshot.setSnapshotVersion(version);
        snapshot.setDimensionJson(writeJson(buildDimension(client, aggregated, counts)));
        snapshot.setRiskFlagsJson(writeJson(collectRisks(analysis, material, client, counts)));
        snapshot.setAdviceJson(writeJson(collectAdvice(analysis, counts)));
        snapshot.setSourceSummaryJson(writeJson(buildSourceSummary(aggregated, material, counts)));
        snapshot.setCustomerSummary(requireNonCommitting(
                buildCustomerSummary(version, material)));
        snapshot.setGeneratedAt(LocalDateTime.now());
        snapshot.setGeneratedBy(GENERATED_BY_RULE);
        snapshot.setStatus(STATUS_DRAFT);
        snapshot.setCreatedBy(operatorName(user));
        snapshot.setUpdatedBy(operatorName(user));
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshot.setUpdatedAt(LocalDateTime.now());
        snapshotMapper.insert(snapshot);

        // 首个版本直接生效，避免画像页在无人复核时长期空白；后续版本仍由复核切换。
        if (currentCount(clientCode) == 0) {
            snapshotMapper.update(null, new LambdaUpdateWrapper<ClientInsightSnapshot>()
                    .eq(ClientInsightSnapshot::getSnapshotNo, snapshot.getSnapshotNo())
                    .set(ClientInsightSnapshot::getStatus, STATUS_CURRENT)
                    .set(ClientInsightSnapshot::getUpdatedBy, operatorName(user))
                    .set(ClientInsightSnapshot::getUpdatedAt, LocalDateTime.now()));
        }

        activityService.append(clientCode, user.getUserNo(), "INSIGHT_GENERATED", "INSIGHT",
                snapshot.getSnapshotNo(), "客户画像快照已生成（第 " + version + " 版）",
                ClientActivityService.VISIBILITY_STAFF_ONLY, LoanUser.TYPE_STAFF, user.getUserNo(), null);
        cacheService.evict(insightCacheKey(clientCode));
        return snapshot.getSnapshotNo();
    }

    /** 读取当前生效画像；不存在时返回 null，由前端展示空态。 */
    public ClientInsightDTO current(String clientCode, LoanUser user) {
        ClientProfile client = scopeService.requireClient(clientCode);
        scopeService.requireClientVisibleToStaff(user, client);
        return cacheService.getOrLoad(insightCacheKey(clientCode),
                new TypeReference<ClientInsightDTO>() { }, () -> loadCurrent(clientCode));
    }

    private ClientInsightDTO loadCurrent(String clientCode) {
        ClientInsightSnapshot snapshot = snapshotMapper.selectOne(new LambdaQueryWrapper<ClientInsightSnapshot>()
                .eq(ClientInsightSnapshot::getClientCode, clientCode)
                .eq(ClientInsightSnapshot::getStatus, STATUS_CURRENT)
                .last("limit 1"));
        return snapshot == null ? null : toDto(snapshot);
    }

    private String insightCacheKey(String clientCode) {
        return "insight:client:" + clientCode + ":current";
    }

    /** 分页读取版本链，便于回放画像变化。 */
    public PageResult<ClientInsightDTO> history(String clientCode, int page, int size, LoanUser user) {
        ClientProfile client = scopeService.requireClient(clientCode);
        scopeService.requireClientVisibleToStaff(user, client);
        Page<ClientInsightSnapshot> result = snapshotMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ClientInsightSnapshot>()
                        .eq(ClientInsightSnapshot::getClientCode, clientCode)
                        .orderByDesc(ClientInsightSnapshot::getSnapshotVersion)
                        .orderByDesc(ClientInsightSnapshot::getId));
        List<ClientInsightDTO> records = result.getRecords().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return PageResult.build(page, size, result.getTotal(), records);
    }

    /**
     * 人工复核：通过后本版本成为 CURRENT，原 CURRENT 归档；驳回则本版本归档留痕。
     */
    @Transactional(rollbackFor = Exception.class)
    public void review(String clientCode, String snapshotNo, String decision, String remark, LoanUser user) {
        if (!StringUtils.hasText(snapshotNo)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "快照编号不能为空");
        }
        ClientInsightSnapshot snapshot = snapshotMapper.selectOne(new LambdaQueryWrapper<ClientInsightSnapshot>()
                .eq(ClientInsightSnapshot::getSnapshotNo, snapshotNo)
                .eq(ClientInsightSnapshot::getClientCode, clientCode)
                .last("limit 1"));
        if (snapshot == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "画像快照不存在");
        }
        ClientProfile client = scopeService.requireClient(clientCode);
        scopeService.requireClientVisibleToStaff(user, client);
        Staff owner = scopeService.findStaff(client.getOwnerStaffCode());
        if (!ServiceOperationAccessPolicy.canReviewInsight(user, client.getOwnerStaffCode(),
                owner == null ? null : owner.getDeptCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权复核客户画像");
        }
        String normalized = StringUtils.hasText(decision) ? decision.trim().toUpperCase() : "";
        if (!"APPROVE".equals(normalized) && !"REJECT".equals(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "复核结论不合法");
        }
        if ("REJECT".equals(normalized) && !StringUtils.hasText(remark)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "驳回必须填写复核意见");
        }
        if (STATUS_ARCHIVED.equals(snapshot.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该版本已归档，不可复核");
        }

        LocalDateTime now = LocalDateTime.now();
        String operator = operatorName(user);
        if ("APPROVE".equals(normalized)) {
            // 先归档原 CURRENT，再置新版本，避免 uk_insight_client_current 唯一键冲突。
            snapshotMapper.update(null, new LambdaUpdateWrapper<ClientInsightSnapshot>()
                    .eq(ClientInsightSnapshot::getClientCode, clientCode)
                    .eq(ClientInsightSnapshot::getStatus, STATUS_CURRENT)
                    .set(ClientInsightSnapshot::getStatus, STATUS_ARCHIVED)
                    .set(ClientInsightSnapshot::getUpdatedBy, operator)
                    .set(ClientInsightSnapshot::getUpdatedAt, now));
            snapshotMapper.update(null, new LambdaUpdateWrapper<ClientInsightSnapshot>()
                    .eq(ClientInsightSnapshot::getSnapshotNo, snapshotNo)
                    .set(ClientInsightSnapshot::getStatus, STATUS_CURRENT)
                    .set(ClientInsightSnapshot::getReviewedBy, operator)
                    .set(ClientInsightSnapshot::getReviewedAt, now)
                    .set(ClientInsightSnapshot::getReviewRemark, trimToNull(remark))
                    .set(ClientInsightSnapshot::getUpdatedBy, operator)
                    .set(ClientInsightSnapshot::getUpdatedAt, now));
        } else {
            snapshotMapper.update(null, new LambdaUpdateWrapper<ClientInsightSnapshot>()
                    .eq(ClientInsightSnapshot::getSnapshotNo, snapshotNo)
                    .set(ClientInsightSnapshot::getStatus, STATUS_ARCHIVED)
                    .set(ClientInsightSnapshot::getReviewedBy, operator)
                    .set(ClientInsightSnapshot::getReviewedAt, now)
                    .set(ClientInsightSnapshot::getReviewRemark, trimToNull(remark))
                    .set(ClientInsightSnapshot::getUpdatedBy, operator)
                    .set(ClientInsightSnapshot::getUpdatedAt, now));
        }

        activityService.append(clientCode, user.getUserNo(), "INSIGHT_REVIEWED", "INSIGHT", snapshotNo,
                "APPROVE".equals(normalized) ? "客户画像复核通过并生效" : "客户画像复核驳回",
                ClientActivityService.VISIBILITY_STAFF_ONLY, LoanUser.TYPE_STAFF, user.getUserNo(), null);
        cacheService.evict(insightCacheKey(clientCode));
    }

    /**
     * 复用员工报告聚合，但允许降级：报告链路异常时不阻断快照生成，风险提示里明确标注。
     *
     * <p>这是有意的权衡——画像快照本身不是审批依据，缺报告维度也要留下服务侧记录。
     */
    private StaffAggregatedReport aggregateQuietly(ClientProfile client, LoanUser user) {
        ClientScreening screening = latestScreening(client.getClientCode());
        if (screening == null) {
            return null;
        }
        try {
            return aggregationService.aggregate(screening.getReportNo(), user);
        } catch (Exception e) {
            log.warn("[Insight] 报告聚合降级 clientCode={} reportNo={} reason={}",
                    client.getClientCode(), screening.getReportNo(), e.getMessage());
            return null;
        }
    }

    private ClientScreening latestScreening(String clientCode) {
        return screeningMapper.selectOne(new LambdaQueryWrapper<ClientScreening>()
                .eq(ClientScreening::getClientProfileCode, clientCode)
                .orderByDesc(ClientScreening::getCreatedAt)
                .orderByDesc(ClientScreening::getId)
                .last("limit 1"));
    }

    private int nextVersion(String clientCode) {
        ClientInsightSnapshot latest = snapshotMapper.selectOne(new LambdaQueryWrapper<ClientInsightSnapshot>()
                .eq(ClientInsightSnapshot::getClientCode, clientCode)
                .orderByDesc(ClientInsightSnapshot::getSnapshotVersion)
                .last("limit 1"));
        return latest == null || latest.getSnapshotVersion() == null ? 1 : latest.getSnapshotVersion() + 1;
    }

    private long currentCount(String clientCode) {
        Long count = snapshotMapper.selectCount(new LambdaQueryWrapper<ClientInsightSnapshot>()
                .eq(ClientInsightSnapshot::getClientCode, clientCode)
                .eq(ClientInsightSnapshot::getStatus, STATUS_CURRENT));
        return count == null ? 0L : count;
    }

    private Map<String, Object> buildDimension(ClientProfile client, StaffAggregatedReport aggregated,
                                               Map<String, Object> counts) {
        Map<String, Object> dimension = new LinkedHashMap<>();
        dimension.put("customerGroup", client.getCustomerGroup());
        dimension.put("source", client.getSource());
        dimension.put("serviceRecords", counts);
        if (aggregated == null) {
            dimension.put("reportAvailable", Boolean.FALSE);
            dimension.put("materialCompleteness", null);
            dimension.put("business", null);
            return dimension;
        }
        dimension.put("reportAvailable", Boolean.TRUE);
        dimension.put("reportNo", aggregated.getReportNo());
        dimension.put("materialCompleteness", aggregated.getMaterialStatus());
        Map<String, Object> analysis = aggregated.getBusinessAnalysis();
        if (analysis != null) {
            dimension.put("business", analysis.get("dimensions"));
            dimension.put("kpi", analysis.get("kpi"));
        }
        return dimension;
    }

    private Map<String, Object> serviceCounts(String clientCode) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("appointmentTotal", countAppointments(clientCode, null));
        counts.put("arrivedCount", countAppointments(clientCode, ARRIVED_STATUSES));
        counts.put("noShowCount", countAppointments(clientCode, Arrays.asList("NO_SHOW")));
        counts.put("outingTotal", countOutings(clientCode, null));
        counts.put("outingCompleted", countOutings(clientCode, Arrays.asList("COMPLETED")));
        counts.put("followTotal", countFollows(clientCode, null));
        counts.put("pendingFollows", countFollows(clientCode, Boolean.TRUE));
        return counts;
    }

    private long countAppointments(String clientCode, Collection<String> statuses) {
        LambdaQueryWrapper<ClientAppointment> wrapper = new LambdaQueryWrapper<ClientAppointment>()
                .eq(ClientAppointment::getClientCode, clientCode);
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in(ClientAppointment::getStatus, statuses);
        }
        Long count = appointmentMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private long countOutings(String clientCode, Collection<String> statuses) {
        LambdaQueryWrapper<StaffOuting> wrapper = new LambdaQueryWrapper<StaffOuting>()
                .eq(StaffOuting::getClientCode, clientCode);
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in(StaffOuting::getStatus, statuses);
        }
        Long count = outingMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private long countFollows(String clientCode, Boolean pendingOnly) {
        LambdaQueryWrapper<ClientFollowRecord> wrapper = new LambdaQueryWrapper<ClientFollowRecord>()
                .eq(ClientFollowRecord::getClientCode, clientCode);
        if (Boolean.TRUE.equals(pendingOnly)) {
            wrapper.isNotNull(ClientFollowRecord::getNextFollowAt)
                    .ge(ClientFollowRecord::getNextFollowAt, LocalDateTime.now());
        }
        Long count = followMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private List<String> collectRisks(Map<String, Object> analysis, StaffMaterialSummary material,
                                      ClientProfile client, Map<String, Object> counts) {
        LinkedHashSet<String> flags = new LinkedHashSet<>();
        if (analysis != null) {
            flags.addAll(texts(analysis.get("risks")));
        }
        if (material == null) {
            flags.add("暂无经营分析报告，画像仅覆盖建档与服务记录");
        } else {
            if (positive(material.getPendingReviewCount()) > 0) {
                flags.add("有 " + material.getPendingReviewCount() + " 份材料待核验");
            }
            if (positive(material.getRejectedCount()) > 0) {
                flags.add("有 " + material.getRejectedCount() + " 份材料需客户补充后重新提交");
            }
        }
        LocalDateTime lastTouch = client.getLastFollowedAt() == null
                ? client.getCreatedAt() : client.getLastFollowedAt();
        if (lastTouch != null && ChronoUnit.DAYS.between(lastTouch, LocalDateTime.now()) >= STALE_FOLLOW_DAYS) {
            flags.add("距上次跟进已超过 " + STALE_FOLLOW_DAYS + " 天");
        }
        long noShow = asLong(counts.get("noShowCount"));
        if (noShow > 0) {
            flags.add("历史未到场 " + noShow + " 次，建议服务前再次确认");
        }
        return new ArrayList<>(flags);
    }

    private List<String> collectAdvice(Map<String, Object> analysis, Map<String, Object> counts) {
        LinkedHashSet<String> advice = new LinkedHashSet<>();
        if (analysis != null) {
            advice.addAll(texts(analysis.get("suggestions")));
        }
        long pending = asLong(counts.get("pendingFollows"));
        if (pending > 0) {
            advice.add("有 " + pending + " 项待回访安排，建议按计划日期闭环");
        }
        if (advice.isEmpty()) {
            advice.add("补齐客户经营资料后重新生成画像，可获得更完整的经营维度");
        }
        return new ArrayList<>(advice);
    }

    private Map<String, Object> buildSourceSummary(StaffAggregatedReport aggregated, StaffMaterialSummary material,
                                                   Map<String, Object> counts) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("dataSourceNotice", aggregated == null
                ? "本画像仅使用客户主动填写及授权上传材料；未调用外部个人信息查询接口"
                : aggregated.getDataSourceNotice());
        source.put("reportNo", aggregated == null ? null : aggregated.getReportNo());
        source.put("materialVersion", material == null ? "v1" : material.getMaterialVersion());
        source.put("submissionNo", material == null ? null : material.getSubmissionNo());
        source.put("submittedAt", material == null ? null : material.getSubmittedAt());
        source.put("materialReview", material == null ? null : materialReviewState(material));
        source.put("dataPeriod", "以客户最近一次授权上传材料为准");
        source.put("serviceRecords", counts);
        source.put("aggregated", aggregated != null);
        return source;
    }

    private Map<String, Object> materialReviewState(StaffMaterialSummary material) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("total", material.getReviewTotal());
        state.put("pending", material.getPendingReviewCount());
        state.put("approved", material.getApprovedCount());
        state.put("rejected", material.getRejectedCount());
        state.put("verified", positive(material.getReviewTotal()) > 0
                && positive(material.getPendingReviewCount()) == 0
                && positive(material.getRejectedCount()) == 0);
        state.put("latestReviewAt", material.getLatestReviewAt());
        return state;
    }

    /** 客户端摘要固定模板：只说进度与下一步，不做任何承诺性表述。 */
    private String buildCustomerSummary(int version, StaffMaterialSummary material) {
        String verify = "资料尚未提交";
        if (material != null) {
            int total = positive(material.getReviewTotal());
            int pending = positive(material.getPendingReviewCount());
            int rejected = positive(material.getRejectedCount());
            if (rejected > 0) {
                verify = "有材料需要补充后重新提交";
            } else if (pending > 0) {
                verify = "资料核验中";
            } else if (total > 0) {
                verify = "资料已核验";
            }
        }
        return "经营分析已更新至第 " + version + " 版，" + verify + "；服务顾问将按约定与你确认下一步安排。";
    }

    private String requireNonCommitting(String text) {
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "客户摘要生成失败");
        }
        for (String word : COMMITTING_WORDS) {
            if (text.contains(word)) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "客户摘要含承诺性措辞，已阻断");
            }
        }
        return text;
    }

    private ClientInsightDTO toDto(ClientInsightSnapshot snapshot) {
        ClientInsightDTO dto = new ClientInsightDTO();
        dto.setSnapshotNo(snapshot.getSnapshotNo());
        dto.setClientCode(snapshot.getClientCode());
        dto.setReportNo(snapshot.getReportNo());
        dto.setSnapshotVersion(snapshot.getSnapshotVersion());
        dto.setStatus(snapshot.getStatus());
        dto.setCurrent(STATUS_CURRENT.equals(snapshot.getStatus()));
        dto.setGeneratedAt(snapshot.getGeneratedAt());
        dto.setGeneratedBy(snapshot.getGeneratedBy());
        dto.setDimension(readJson(snapshot.getDimensionJson()));
        dto.setRiskFlags(readJson(snapshot.getRiskFlagsJson()));
        dto.setAdvice(readJson(snapshot.getAdviceJson()));
        dto.setSourceSummary(readJson(snapshot.getSourceSummaryJson()));
        dto.setCustomerSummary(snapshot.getCustomerSummary());
        dto.setReviewedBy(snapshot.getReviewedBy());
        dto.setReviewedAt(snapshot.getReviewedAt());
        dto.setReviewRemark(snapshot.getReviewRemark());
        return dto;
    }

    private Object readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            // 历史数据序列化异常不阻断读取，原样返回由前端兜底展示。
            return json;
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "画像数据序列化失败");
        }
    }

    private List<String> texts(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    result.add(String.valueOf(item));
                }
            }
        } else if (value != null && StringUtils.hasText(String.valueOf(value))) {
            result.add(String.valueOf(value));
        }
        return result;
    }

    private int positive(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private String operatorName(LoanUser user) {
        if (user == null) {
            return "system";
        }
        return StringUtils.hasText(user.getName()) ? user.getName() : user.getUserNo();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
