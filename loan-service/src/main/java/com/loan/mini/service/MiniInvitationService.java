package com.loan.mini.service;

import com.loan.invitation.service.InvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小程序端邀请绑定薄封装：绑定成功后返回引荐人昵称/姓名给小程序展示。
 *
 * <p>绑定副作用（回写归属顾问 + 生成归属线索）在 {@link InvitationService#bind} 内完成，
 * 本层只负责把内部返回映射为小程序可读的展示结构。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class MiniInvitationService {

    private final InvitationService invitationService;

    /**
     * 绑定邀请码并返回顾问昵称。
     *
     * @param inviteCode 邀请码
     * @param clientCode 客户编码
     * @param clientId   客户档案内部 ID
     * @return {referrerType, referrerName}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bind(String inviteCode, String clientCode, Long clientId) {
        Map<String, Object> bind = invitationService.bind(inviteCode, clientCode, clientId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("referrerType", bind.get("referrerType"));
        m.put("referrerName", bind.get("referrerName"));
        return m;
    }
}
