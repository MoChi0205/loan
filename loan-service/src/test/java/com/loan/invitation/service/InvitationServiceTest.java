package com.loan.invitation.service;

import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.common.ResultCode;
import com.loan.common.service.BusinessNameService;
import com.loan.exception.BusinessException;
import com.loan.invitation.entity.Invitation;
import com.loan.invitation.mapper.InvitationMapper;
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
 * 分享引荐与服务顾问归属分离，任何邀请类型都不回写 owner_staff_code、不生成线索；
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
    private BusinessNameService businessNameService;
    private InvitationService service;

    @BeforeEach
    void setUp() {
        service = new InvitationService(invitationMapper, clientProfileMapper, businessNameService);
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
        inv.setReferrerClientCode("clientA");
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
        when(invitationMapper.consume(anyString(), anyLong(), anyString(), any())).thenReturn(1);
        when(businessNameService.referrerName(inv)).thenReturn("李四");

        Map<String, Object> result = service.bind("INV1", "clientA", 7L);

        assertEquals(1, inv.getUsedFlag());
        assertEquals(7L, inv.getUsedByClientId());
        assertEquals("clientA", inv.getUsedByClientCode());
        verify(invitationMapper).consume(anyString(), anyLong(), anyString(), any());
        verify(businessNameService).referrerName(inv);
        verify(clientProfileMapper, never()).updateById(any());
        assertEquals("CUSTOMER", result.get("referrerType"));
        assertEquals("refC", result.get("referrerClientCode"));
        assertEquals("李四", result.get("referrerName"));
    }

    // ---------- bind 成功：员工引荐只记分享关系 ----------

    @Test
    @DisplayName("bind：ADVISER 引荐 → 返回分享人姓名，不改客户归属")
    void bind_adviserReferrer() {
        Invitation inv = activeInvitation("INV9", "ADVISER", 5L);
        Staff staff = staff("S001", "张三");

        when(invitationMapper.selectOne(any())).thenReturn(inv);
        when(invitationMapper.consume(anyString(), anyLong(), anyString(), any())).thenReturn(1);
        when(businessNameService.referrerName(inv)).thenReturn(staff.getStaffName());

        Map<String, Object> result = service.bind("INV9", "clientA", 7L);

        assertEquals("张三", result.get("referrerName"));
        verify(clientProfileMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("bind：同一客户重复提交已消费邀请码 → 幂等返回，不重复更新")
    void bind_sameClientIdempotent() {
        Invitation inv = activeInvitation("INV9", "ADVISER", 5L);
        inv.setUsedFlag(1);
        inv.setUsedByClientCode("clientA");
        when(invitationMapper.selectOne(any())).thenReturn(inv);
        service.bind("INV9", "clientA", 7L);
        verify(invitationMapper, never()).consume(anyString(), anyLong(), anyString(), any());
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
