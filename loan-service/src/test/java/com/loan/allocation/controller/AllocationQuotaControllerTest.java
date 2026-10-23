package com.loan.allocation.controller;

import com.loan.allocation.entity.ClaimQuotaConfig;
import com.loan.allocation.mapper.ClaimQuotaConfigMapper;
import com.loan.allocation.service.ClaimQuotaService;
import com.loan.client.pool.ResourcePool;
import com.loan.client.pool.ResourcePoolMapper;
import com.loan.common.ResultCode;
import com.loan.context.LoanUser;
import com.loan.context.UserContext;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认领配额配置接口契约测试（AllocationQuotaController，此前零覆盖）。
 *
 * <p>Controller 层负责：三维度 scope 校验（LEAD / CLIENT / POOL:{poolCode}）、
 * 数值合法性校验、按 scope 幂等 upsert、记录更新人。
 * 配额是否真正生效由 {@code ClaimQuotaService} 承担，本类只验证「接口契约不被绕过」。</p>
 */
class AllocationQuotaControllerTest {

    private MockMvc mvc;
    private ClaimQuotaConfigMapper configMapper;
    private ClaimQuotaService claimQuotaService;
    private ResourcePoolMapper resourcePoolMapper;

    @BeforeEach
    void setUp() {
        configMapper = mock(ClaimQuotaConfigMapper.class);
        claimQuotaService = mock(ClaimQuotaService.class);
        resourcePoolMapper = mock(ResourcePoolMapper.class);
        AllocationQuotaController controller =
                new AllocationQuotaController(configMapper, claimQuotaService, resourcePoolMapper);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("GET /list：三维度配额（LEAD / CLIENT / POOL:code）全部返回，未配置行回落到生效兜底值")
    void listReturnsAllThreeDimensions() throws Exception {
        UserContext.setUser(TestUsers.boss());
        when(configMapper.selectList(isNull()))
                .thenReturn(Collections.singletonList(configRow("POOL:P1", 3, 30, 5, 1, "PUBLIC", "车贷池", "tester-B001")));
        when(claimQuotaService.limitOf(anyString())).thenReturn(5);
        when(claimQuotaService.holdingLimitOf(anyString())).thenReturn(20);
        when(claimQuotaService.protectDaysOf(anyString())).thenReturn(7);
        when(claimQuotaService.cooldownLimitOf(anyString())).thenReturn(2);
        when(claimQuotaService.returnTargetOf(anyString())).thenReturn("PUBLIC");
        when(resourcePoolMapper.selectOne(any())).thenReturn(pool("P1", "车贷资源池"));

        mvc.perform(get("/api/admin/allocation-quota/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].scope").value("LEAD"))
                .andExpect(jsonPath("$.data[0].scopeName").value("线索公海"))
                .andExpect(jsonPath("$.data[0].dailyClaimLimit").value(5))
                .andExpect(jsonPath("$.data[0].maxHolding").value(20))
                .andExpect(jsonPath("$.data[1].scope").value("CLIENT"))
                .andExpect(jsonPath("$.data[1].scopeName").value("客户公海"))
                .andExpect(jsonPath("$.data[2].scope").value("POOL:P1"))
                .andExpect(jsonPath("$.data[2].scopeName").value("车贷资源池"))
                .andExpect(jsonPath("$.data[2].dailyClaimLimit").value(3));
    }

    @Test
    @DisplayName("PUT /save：老板保存配额，参数原样落库且更新人记为当前登录人")
    void savePersistsQuotaWithCurrentUserAsOperator() throws Exception {
        LoanUser boss = TestUsers.boss();
        UserContext.setUser(boss);
        when(resourcePoolMapper.selectOne(any())).thenReturn(pool("P1", "车贷资源池"));

        mvc.perform(put("/api/admin/allocation-quota/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":["
                                + "{\"scope\":\"lead\",\"dailyClaimLimit\":10,\"maxHolding\":50,"
                                + "\"protectDays\":3,\"dailyCooldownLimit\":2,\"returnTarget\":\"original\",\"remark\":\"线索池\"},"
                                + "{\"scope\":\"POOL:P1\",\"dailyClaimLimit\":3}"
                                + "],\"deleteScopes\":[\"POOL:P9\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<ClaimQuotaConfig> captor = ArgumentCaptor.forClass(ClaimQuotaConfig.class);
        verify(configMapper, times(2)).insert(captor.capture());
        ClaimQuotaConfig lead = captor.getAllValues().get(0);
        assertEquals("LEAD", lead.getScope());
        assertEquals(Integer.valueOf(10), lead.getDailyClaimLimit());
        assertEquals(Integer.valueOf(50), lead.getMaxHolding());
        assertEquals(Integer.valueOf(3), lead.getProtectDays());
        assertEquals(Integer.valueOf(2), lead.getDailyCooldownLimit());
        assertEquals("ORIGINAL", lead.getReturnTarget());
        assertEquals(boss.getName(), lead.getUpdatedBy());
        assertEquals("POOL:P1", captor.getAllValues().get(1).getScope());
        verify(configMapper).delete(any());
        verify(claimQuotaService).evictCache();
    }

    @Test
    @DisplayName("PUT /save：顾问提交同样被接受（Controller 层未做角色守卫，权限完全依赖网关侧接口授权）")
    void saveHasNoRoleGuardInController() throws Exception {
        UserContext.setUser(TestUsers.adviser());

        mvc.perform(put("/api/admin/allocation-quota/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"scope\":\"LEAD\",\"dailyClaimLimit\":999}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<ClaimQuotaConfig> captor = ArgumentCaptor.forClass(ClaimQuotaConfig.class);
        verify(configMapper).insert(captor.capture());
        assertEquals("LEAD", captor.getValue().getScope());
        assertEquals(Integer.valueOf(999), captor.getValue().getDailyClaimLimit());
        assertEquals(TestUsers.adviser().getName(), captor.getValue().getUpdatedBy());
    }

    @Test
    @DisplayName("PUT /save：未提交 items 列表被拒（PARAM_ERROR，不是 500）")
    void saveRejectsMissingItems() throws Exception {
        UserContext.setUser(TestUsers.boss());

        mvc.perform(put("/api/admin/allocation-quota/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(configMapper, never()).insert(any(ClaimQuotaConfig.class));
        verify(claimQuotaService, never()).evictCache();
    }

    @Test
    @DisplayName("PUT /save：scope 不属于三维度（如 BOSS）被拒，且 scope 为空直接拒绝")
    void saveRejectsUnsupportedScope() throws Exception {
        UserContext.setUser(TestUsers.boss());

        mvc.perform(put("/api/admin/allocation-quota/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"scope\":\"BOSS\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        mvc.perform(put("/api/admin/allocation-quota/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"dailyClaimLimit\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(configMapper, never()).insert(any(ClaimQuotaConfig.class));
    }

    @Test
    @DisplayName("PUT /save：POOL 维度指向不存在的资源池被拒")
    void saveRejectsUnknownPool() throws Exception {
        UserContext.setUser(TestUsers.superAdmin());

        mvc.perform(put("/api/admin/allocation-quota/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"scope\":\"POOL:NOT_EXIST\",\"dailyClaimLimit\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(configMapper, never()).insert(any(ClaimQuotaConfig.class));
    }

    @Test
    @DisplayName("PUT /save：负数上限与非法回纳目标被拒（0 才表示不限）")
    void saveRejectsNegativeLimitAndBadReturnTarget() throws Exception {
        UserContext.setUser(TestUsers.boss());

        mvc.perform(put("/api/admin/allocation-quota/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"scope\":\"LEAD\",\"maxHolding\":-1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        mvc.perform(put("/api/admin/allocation-quota/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"scope\":\"LEAD\",\"returnTarget\":\"PRIVATE\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(configMapper, never()).insert(any(ClaimQuotaConfig.class));
    }

    @Test
    @DisplayName("PUT /save：LEAD / CLIENT 常驻行不可删除（deleteScopes 仅收资源池维度）")
    void saveRejectsDeletingBuiltInScopes() throws Exception {
        UserContext.setUser(TestUsers.boss());

        mvc.perform(put("/api/admin/allocation-quota/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"scope\":\"LEAD\"}],\"deleteScopes\":[\"LEAD\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(configMapper, never()).delete(any());
        verify(claimQuotaService, never()).evictCache();
    }

    private ClaimQuotaConfig configRow(String scope, int daily, int holding, int protect, int cooldown,
                                       String returnTarget, String remark, String updatedBy) {
        ClaimQuotaConfig row = new ClaimQuotaConfig();
        row.setScope(scope);
        row.setDailyClaimLimit(daily);
        row.setMaxHolding(holding);
        row.setProtectDays(protect);
        row.setDailyCooldownLimit(cooldown);
        row.setReturnTarget(returnTarget);
        row.setRemark(remark);
        row.setUpdatedBy(updatedBy);
        return row;
    }

    private ResourcePool pool(String poolCode, String poolName) {
        ResourcePool pool = new ResourcePool();
        pool.setPoolCode(poolCode);
        pool.setPoolName(poolName);
        return pool;
    }
}
