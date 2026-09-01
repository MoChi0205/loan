package com.loan.invitation.service;

import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.invitation.entity.Invitation;
import com.loan.invitation.mapper.InvitationMapper;
import com.loan.lead.entity.Lead;
import com.loan.lead.mapper.LeadMapper;
import com.loan.lead.service.LeadService;
import com.loan.staff.entity.Staff;
import com.loan.staff.mapper.StaffMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 邀请归因单测（M3 L2）：不启动 Spring 上下文，mock 全部 Mapper / LeadService。
 *
 * <p>覆盖：bind 校验链（存在/ACTIVE/未使用/未过期/非自己）→ 置 used_flag=1；
 * P0-2 员工引荐（ADVISER/BOSS）回写 client_profile.owner_staff_code（仅当原为空）+ 生成归属线索（去重）；
 * generateForClient 幂等复用未使用 CUSTOMER 码 / 新建 7 天有效码。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvitationServiceTest {

    @Mock
    private InvitationMapper invitationMapper;
    @Mock
    private ClientProfileMapper clientProfileMapper;
    @Mock
    private StaffMapper staffMapper;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private LeadService leadService;

    private InvitationService service;

    @BeforeEach
    void setUp() {
        service = new InvitationService(invitationMapper, clientProfileMapper, staffMapper, leadMapper, leadService);
    }

    // ---------- bind 校验链 ----------

    @Test
    @DisplayName("bind：邀请码为空 → 参数异常")
    void bind_blankCode() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.bind(" ", "clientA", 7L));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("bind：邀请码不存在 → 数据不存在")
    void bind_notFound() {
        when(invitationMapper.selectOne(any())).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.bind("INVx", "clientA", 7L));
        assertEquals(ResultCode.DATA_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("bind：状态已作废 → 参数异常")
    void bind_notActive() {
        Invitation inv = activeInvitation("INV1", "CUSTOMER", null);
        inv.setStatus("VOID");
        when(invitationMapper.selectOne(any())).thenReturn(inv);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.bind("INV1", "clientA", 7L));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("bind：已被使用 → 参数异常")
    void bind_alreadyUsed() {
        Invitation inv = activeInvitation("INV1", "CUSTOMER", null);
        inv.setUsedFlag(1);
        when(invitationMapper.selectOne(any())).thenReturn(inv);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.bind("INV1", "clientA", 7L));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("bind：已过期 → 参数异常")
    void bind_expired() {
        Invitation inv = activeInvitation("INV1", "CUSTOMER", null);
        inv.setExpireAt(LocalDateTime.now().minusDays(1));
        when(invitationMapper.selectOne(any())).thenReturn(inv);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.bind("INV1", "clientA", 7L));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("bind：使用自己的邀请码 → 参数异常")
    void bind_selfUse() {
        Invitation inv = activeInvitation("INV1", "CUSTOMER", null);
        inv.setUsedByClientCode("clientA");
        when(invitationMapper.selectOne(any())).thenReturn(inv);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.bind("INV1", "clientA", 7L));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    // ---------- bind 成功：客户引荐（不回写归属/不建线索） ----------

    @Test
    @DisplayName("bind：CUSTOMER 引荐 → 置 usedFlag 并回传，不回写归属、不建线索")
    void bind_customerReferrer() {
        Invitation inv = activeInvitation("INV1", "CUSTOMER", null);
        inv.setReferrerClientCode("refC");
        ClientProfile client = client(null, null, "ENTERPRISE");
        when(invitationMapper.selectOne(any())).thenReturn(inv);
        when(invitationMapper.updateById(any())).thenReturn(1);
        when(clientProfileMapper.selectOne(any())).thenReturn(client); // 总是先查客户档案

        Map<String, Object> result = service.bind("INV1", "clientA", 7L);

        assertEquals(1, inv.getUsedFlag());
        assertEquals(7L, inv.getUsedByClientId());
        assertEquals("clientA", inv.getUsedByClientCode());
        verify(invitationMapper).updateById(any());
        verify(clientProfileMapper).selectOne(any());
        verify(clientProfileMapper, never()).updateById(any()); // ownerStaffCode 为空 → 不回写
        assertEquals("CUSTOMER", result.get("referrerType"));
        assertEquals("refC", result.get("referrerClientCode"));
        assertEquals("refC", result.get("referrerName")); // ownerStaffCode 为空 → 取 referrerClientCode
        verify(leadService, never()).create(any(), any(), any());
    }

    // ---------- bind 成功：员工引荐（回写归属 + 建线索） ----------

    @Test
    @DisplayName("bind：ADVISER 引荐 → 回写 owner_staff_code 并生成归属线索")
    void bind_adviserReferrer() {
        Invitation inv = activeInvitation("INV9", "ADVISER", 5L);
        Staff staff = staff("S001", "张三");
        ClientProfile client = client(null, "hash_x", "ENTERPRISE");

        when(invitationMapper.selectOne(any())).thenReturn(inv);
        when(invitationMapper.updateById(any())).thenReturn(1);
        when(staffMapper.selectById(5L)).thenReturn(staff);
        when(staffMapper.selectOne(any())).thenReturn(staff);
        when(clientProfileMapper.selectOne(any())).thenReturn(client);
        when(clientProfileMapper.updateById(any())).thenReturn(1);
        when(leadMapper.selectCount(any())).thenReturn(0L);
        when(leadService.create(any(), any(), any())).thenReturn("LEAD1");

        Map<String, Object> result = service.bind("INV9", "clientA", 7L);

        assertEquals("S001", client.getOwnerStaffCode()); // 回写归属顾问
        verify(clientProfileMapper).updateById(any());
        ArgumentCaptor<Lead> leadCap = ArgumentCaptor.forClass(Lead.class);
        verify(leadService).create(leadCap.capture(), eq("S001"), eq("张三"));
        Lead lead = leadCap.getValue();
        assertEquals("INVITE", lead.getSource());
        assertEquals("clientA", lead.getClientProfileCode());
        assertEquals("NEW", lead.getFollowStatus());
        assertEquals("张三", result.get("referrerName"));
    }

    @Test
    @DisplayName("bind：BOSS 引荐同样回写归属（resolveStaffCode BOSS 分支）")
    void bind_bossReferrer() {
        Invitation inv = activeInvitation("INV8", "BOSS", 6L);
        Staff staff = staff("S002", "老板");
        ClientProfile client = client(null, "hash_y", "ENTERPRISE");

        when(invitationMapper.selectOne(any())).thenReturn(inv);
        when(invitationMapper.updateById(any())).thenReturn(1);
        when(staffMapper.selectById(6L)).thenReturn(staff);
        when(staffMapper.selectOne(any())).thenReturn(staff);
        when(clientProfileMapper.selectOne(any())).thenReturn(client);
        when(clientProfileMapper.updateById(any())).thenReturn(1);
        when(leadMapper.selectCount(any())).thenReturn(0L);
        when(leadService.create(any(), any(), any())).thenReturn("LEAD2");

        Map<String, Object> result = service.bind("INV8", "clientA", 7L);
        assertEquals("S002", client.getOwnerStaffCode());
        assertEquals("老板", result.get("referrerName"));
    }

    @Test
    @DisplayName("bind：客户已有归属 → 不覆盖，但仍建线索")
    void bind_existingOwnerNotOverwritten() {
        Invitation inv = activeInvitation("INV9", "ADVISER", 5L);
        Staff staff = staff("S001", "张三");
        ClientProfile client = client("EXISTING", "hash_x", "ENTERPRISE");

        when(invitationMapper.selectOne(any())).thenReturn(inv);
        when(invitationMapper.updateById(any())).thenReturn(1);
        when(staffMapper.selectById(5L)).thenReturn(staff);
        when(staffMapper.selectOne(any())).thenReturn(staff);
        when(clientProfileMapper.selectOne(any())).thenReturn(client);
        when(leadMapper.selectCount(any())).thenReturn(0L);
        when(leadService.create(any(), any(), any())).thenReturn("LEAD1");

        service.bind("INV9", "clientA", 7L);
        assertEquals("EXISTING", client.getOwnerStaffCode()); // 未被覆盖
        verify(clientProfileMapper, never()).updateById(any()); // 不回写
        verify(leadService).create(any(), any(), any()); // 仍建线索
    }

    @Test
    @DisplayName("bind：手机号哈希为空 → 跳过建线索（防空手机号）")
    void bind_phoneHashNullSkipsLead() {
        Invitation inv = activeInvitation("INV9", "ADVISER", 5L);
        Staff staff = staff("S001", "张三");
        ClientProfile client = client(null, null, "ENTERPRISE"); // phoneHash 空

        when(invitationMapper.selectOne(any())).thenReturn(inv);
        when(invitationMapper.updateById(any())).thenReturn(1);
        when(staffMapper.selectById(5L)).thenReturn(staff);
        when(staffMapper.selectOne(any())).thenReturn(staff);
        when(clientProfileMapper.selectOne(any())).thenReturn(client);
        when(clientProfileMapper.updateById(any())).thenReturn(1);

        service.bind("INV9", "clientA", 7L);
        assertEquals("S001", client.getOwnerStaffCode());
        verify(leadService, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("bind：同客户已存在 INVITE 线索 → 去重不重复建")
    void bind_dedupLead() {
        Invitation inv = activeInvitation("INV9", "ADVISER", 5L);
        Staff staff = staff("S001", "张三");
        ClientProfile client = client(null, "hash_x", "ENTERPRISE");

        when(invitationMapper.selectOne(any())).thenReturn(inv);
        when(invitationMapper.updateById(any())).thenReturn(1);
        when(staffMapper.selectById(5L)).thenReturn(staff);
        when(staffMapper.selectOne(any())).thenReturn(staff);
        when(clientProfileMapper.selectOne(any())).thenReturn(client);
        when(clientProfileMapper.updateById(any())).thenReturn(1);
        when(leadMapper.selectCount(any())).thenReturn(1L); // 已存在

        service.bind("INV9", "clientA", 7L);
        verify(leadService, never()).create(any(), any(), any());
    }

    // ---------- generateForClient 幂等 / 新建 ----------

    @Test
    @DisplayName("generateForClient：存在未使用且未过期的本人 CUSTOMER 码 → 复用")
    void generate_reuseExisting() {
        Invitation exist = new Invitation();
        exist.setInvitationCode("INVreuse");
        exist.setReferrerType("CUSTOMER");
        exist.setReferrerId(7L);
        exist.setUsedFlag(0);
        exist.setStatus("ACTIVE");
        exist.setExpireAt(LocalDateTime.now().plusDays(3));
        when(invitationMapper.selectOne(any())).thenReturn(exist);

        String code = service.generateForClient("clientA", 7L, "op");
        assertEquals("INVreuse", code);
        verify(invitationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("generateForClient：无可用码 → 新建 7 天有效 CUSTOMER 码")
    void generate_createNew() {
        when(invitationMapper.selectOne(any())).thenReturn(null);
        when(invitationMapper.insert(any())).thenReturn(1);

        String code = service.generateForClient("clientA", 7L, "op");
        assertNotNull(code);
        assertTrue(code.startsWith("INV"));

        ArgumentCaptor<Invitation> cap = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationMapper).insert(cap.capture());
        Invitation saved = cap.getValue();
        assertEquals("CUSTOMER", saved.getReferrerType());
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals(0, saved.getUsedFlag());
        assertEquals(7L, saved.getReferrerId());
        assertEquals("clientA", saved.getReferrerClientCode());
        assertNotNull(saved.getExpireAt());
        assertTrue(saved.getExpireAt().isAfter(LocalDateTime.now().plusDays(6)));
    }

    // ---------- helpers ----------

    private Invitation activeInvitation(String code, String type, Long referrerId) {
        Invitation inv = new Invitation();
        inv.setInvitationCode(code);
        inv.setStatus("ACTIVE");
        inv.setUsedFlag(0);
        inv.setReferrerType(type);
        inv.setReferrerId(referrerId);
        inv.setUsedByClientCode(null);
        inv.setExpireAt(LocalDateTime.now().plusDays(7));
        return inv;
    }

    private Staff staff(String code, String name) {
        Staff s = new Staff();
        s.setStaffCode(code);
        s.setStaffName(name);
        return s;
    }

    private ClientProfile client(String ownerStaffCode, String phoneHash, String group) {
        ClientProfile c = new ClientProfile();
        c.setClientCode("clientA");
        c.setOwnerStaffCode(ownerStaffCode);
        c.setPhoneHash(phoneHash);
        c.setCustomerGroup(group);
        c.setContactName("李四");
        return c;
    }
}
