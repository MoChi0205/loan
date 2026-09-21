package com.loan.serviceops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.loan.client.entity.ClientProfile;
import com.loan.common.cache.UnifiedCacheService;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.report.mapper.ClientScreeningMapper;
import com.loan.report.service.StaffReportAggregationService;
import com.loan.serviceops.entity.ClientInsightSnapshot;
import com.loan.serviceops.mapper.ClientAppointmentMapper;
import com.loan.serviceops.mapper.ClientFollowRecordMapper;
import com.loan.serviceops.mapper.ClientInsightSnapshotMapper;
import com.loan.serviceops.mapper.StaffOutingMapper;
import com.loan.staff.entity.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 客户画像快照的写权限、版本推进与承诺性措辞门禁。 */
class ClientInsightServiceTest {

    private ClientInsightSnapshotMapper snapshotMapper;
    private ClientScreeningMapper screeningMapper;
    private ServiceOperationScopeService scopeService;
    private ClientActivityService activityService;
    private ClientInsightService service;

    @BeforeEach
    void setUp() {
        // LambdaUpdateWrapper 的列名解析依赖 TableInfo 缓存；纯 mock 环境下需手动初始化。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ClientInsightSnapshot.class);
        snapshotMapper = mock(ClientInsightSnapshotMapper.class);
        screeningMapper = mock(ClientScreeningMapper.class);
        scopeService = mock(ServiceOperationScopeService.class);
        activityService = mock(ClientActivityService.class);
        UnifiedCacheService cacheService = mock(UnifiedCacheService.class);
        // 只读路径的缓存回源直接透传 loader，避免测试依赖 Redis。
        when(cacheService.getOrLoad(anyString(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(2)).get());
        service = new ClientInsightService(snapshotMapper,
                mock(ClientAppointmentMapper.class),
                mock(StaffOutingMapper.class),
                mock(ClientFollowRecordMapper.class),
                screeningMapper,
                mock(StaffReportAggregationService.class),
                scopeService,
                activityService,
                new ObjectMapper(),
                cacheService);
    }

    @Test
    void onlyOwnerAdviserCanGenerate() {
        when(scopeService.requireClient("client-1")).thenReturn(client());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.generate("client-1", staff("staff-other", "ADVISER", "D1")));

        assertEquals(ResultCode.FORBIDDEN.getCode(), error.getCode());
        verify(snapshotMapper, never()).insert(any());
    }

    @Test
    void firstVersionBecomesCurrentAndKeepsCustomerSummaryClean() {
        when(scopeService.requireClient("client-1")).thenReturn(client());
        when(screeningMapper.selectOne(any())).thenReturn(null);
        when(snapshotMapper.selectOne(any())).thenReturn(null);
        when(snapshotMapper.selectCount(any())).thenReturn(0L);

        String snapshotNo = service.generate("client-1", staff("staff-owner", "ADVISER", "D1"));

        assertNotNull(snapshotNo);
        ArgumentCaptor<ClientInsightSnapshot> captor = ArgumentCaptor.forClass(ClientInsightSnapshot.class);
        verify(snapshotMapper).insert(captor.capture());
        ClientInsightSnapshot saved = captor.getValue();
        assertEquals(1, saved.getSnapshotVersion());
        assertEquals("DRAFT", saved.getStatus());
        assertEquals("RULE", saved.getGeneratedBy());
        assertEquals("staff-owner", saved.getCreatedBy());
        assertTrue(saved.getCustomerSummary().contains("第 1 版"));
        assertFalse(saved.getCustomerSummary().contains("准入"));
        assertFalse(saved.getCustomerSummary().contains("保证"));
        assertTrue(saved.getRiskFlagsJson().contains("暂无经营分析报告"));
        // 首个版本自动生效：原 CURRENT 归档语句不会触发，直接置本版本为 CURRENT。
        verify(snapshotMapper, times(1)).update(isNull(), any());
        verify(activityService).append(eq("client-1"), eq("staff-owner"), eq("INSIGHT_GENERATED"),
                eq("INSIGHT"), eq(snapshotNo), anyString(), eq("STAFF_ONLY"), eq("STAFF"),
                eq("staff-owner"), isNull());
    }

    @Test
    void newVersionWaitsForReviewWhenCurrentExists() {
        when(scopeService.requireClient("client-1")).thenReturn(client());
        when(screeningMapper.selectOne(any())).thenReturn(null);
        ClientInsightSnapshot previous = new ClientInsightSnapshot();
        previous.setSnapshotVersion(2);
        previous.setStatus("CURRENT");
        when(snapshotMapper.selectOne(any())).thenReturn(previous);
        when(snapshotMapper.selectCount(any())).thenReturn(1L);

        service.generate("client-1", staff("staff-owner", "ADVISER", "D1"));

        ArgumentCaptor<ClientInsightSnapshot> captor = ArgumentCaptor.forClass(ClientInsightSnapshot.class);
        verify(snapshotMapper).insert(captor.capture());
        assertEquals(3, captor.getValue().getSnapshotVersion());
        // 已有生效版本时不再自动生效，等复核；生成过程不应写 CURRENT。
        verify(snapshotMapper, never()).update(isNull(), any());
    }

    @Test
    void reviewApproveArchivesOldCurrentThenActivatesNewVersion() {
        when(snapshotMapper.selectOne(any())).thenReturn(pendingSnapshot());
        when(scopeService.requireClient("client-1")).thenReturn(client());
        Staff owner = new Staff();
        owner.setStaffCode("staff-owner");
        owner.setDeptCode("D1");
        when(scopeService.findStaff("staff-owner")).thenReturn(owner);
        LoanUser manager = staff("staff-mgr", "DEPT_MANAGER", "D1");

        service.review("client-1", "insight-9", "APPROVE", "维度完整", manager);

        // 两次更新：先归档原 CURRENT，再置本版本为 CURRENT。
        verify(snapshotMapper, times(2)).update(isNull(), any());
        verify(activityService).append(eq("client-1"), eq("staff-mgr"), eq("INSIGHT_REVIEWED"),
                eq("INSIGHT"), eq("insight-9"), anyString(), eq("STAFF_ONLY"), eq("STAFF"),
                eq("staff-mgr"), isNull());
    }

    @Test
    void reviewRejectsSelfReviewAndMissingRemark() {
        when(snapshotMapper.selectOne(any())).thenReturn(pendingSnapshot());
        when(scopeService.requireClient("client-1")).thenReturn(client());
        Staff owner = new Staff();
        owner.setStaffCode("staff-owner");
        owner.setDeptCode("D1");
        when(scopeService.findStaff("staff-owner")).thenReturn(owner);

        // 归属顾问本人不能复核自己的画像。
        BusinessException self = assertThrows(BusinessException.class,
                () -> service.review("client-1", "insight-9", "APPROVE", null,
                        staff("staff-owner", "ADVISER", "D1")));
        assertEquals(ResultCode.FORBIDDEN.getCode(), self.getCode());

        // 驳回必须填写复核意见。
        BusinessException remark = assertThrows(BusinessException.class,
                () -> service.review("client-1", "insight-9", "REJECT", "  ",
                        staff("staff-mgr", "DEPT_MANAGER", "D1")));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), remark.getCode());
        verify(snapshotMapper, never()).update(isNull(), any());
    }

    private ClientInsightSnapshot pendingSnapshot() {
        ClientInsightSnapshot snapshot = new ClientInsightSnapshot();
        snapshot.setSnapshotNo("insight-9");
        snapshot.setClientCode("client-1");
        snapshot.setSnapshotVersion(3);
        snapshot.setStatus("DRAFT");
        return snapshot;
    }

    private ClientProfile client() {
        ClientProfile client = new ClientProfile();
        client.setClientCode("client-1");
        client.setOwnerStaffCode("staff-owner");
        client.setCustomerGroup("ENTERPRISE");
        client.setStatus("ACTIVE");
        client.setCreatedAt(LocalDateTime.now());
        client.setLastFollowedAt(LocalDateTime.now());
        return client;
    }

    private LoanUser staff(String userNo, String roleCode, String deptCode) {
        LoanUser user = new LoanUser();
        user.setUserNo(userNo);
        user.setUserType(LoanUser.TYPE_STAFF);
        user.setRoleCode(roleCode);
        user.setDeptCode(deptCode);
        user.setName(userNo);
        return user;
    }
}
