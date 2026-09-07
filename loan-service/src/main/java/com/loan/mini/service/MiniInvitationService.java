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
 * <p>绑定只记录分享引荐关系，不回写服务顾问、不生成服务归属；顾问归属由管理员直分配、
 * 分配审批或顾问公海认领产生。实际绑定由 {@link InvitationService#bind} 完成，
 * 本层只负责把内部返回映射为小程序可读的展示结构。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class MiniInvitationService {

    private final InvitationService invitationService;

    /**
     * 绑定邀请码并返回引荐人姓名。
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
