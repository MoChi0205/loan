package com.loan.personal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.client.entity.ClientProfile;
import com.loan.client.mapper.ClientProfileMapper;
import com.loan.common.ResultCode;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.security.HashUtils;
import com.loan.personal.dto.PersonalAuthRequest;
import com.loan.personal.entity.PersonalAuth;
import com.loan.personal.entity.PersonalProfile;
import com.loan.personal.mapper.PersonalAuthMapper;
import com.loan.personal.mapper.PersonalProfileMapper;
import com.loan.utils.DesensitizeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人认证服务（Mock 三要素 + 落库留痕，Q5 决策）。
 *
 * <p>Mock：仅做身份证格式校验 + 唯一性查重，不接真实服务商；
 * {@code t_personal_auth} 记录 auth_status=SUCCESS、fail_reason='MOCK'。
 *
 * @author loan-platform
 */
@Service
@RequiredArgsConstructor
public class PersonalProfileService {

    private static final String MOCK_MARK = "MOCK";

    private final PersonalProfileMapper personalProfileMapper;
    private final PersonalAuthMapper personalAuthMapper;
    private final ClientProfileMapper clientProfileMapper;

    /**
     * 个人认证（Mock 三要素校验 + 落库留痕）。
     *
     * @param clientCode 客户编码
     * @param req        认证请求
     * @return 认证结果（敏感字段仅返回脱敏值）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> personalAuth(String clientCode, PersonalAuthRequest req) {
        if (!StringUtils.hasText(clientCode)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "客户必填");
        }
        if (req == null || !StringUtils.hasText(req.getRealName()) || !StringUtils.hasText(req.getIdCardNo())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "姓名与身份证号必填");
        }
        if (!req.getIdCardNo().matches("^\\d{17}[\\dXx]$")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "身份证号格式不正确");
        }
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode));
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户档案不存在");
        }
        // 身份证唯一性校验（hash 查重，排除自己）
        String idCardHash = HashUtils.sha256Hex(req.getIdCardNo());
        List<PersonalProfile> dup = personalProfileMapper.selectList(new LambdaQueryWrapper<PersonalProfile>()
                .eq(PersonalProfile::getIdCardHash, idCardHash)
                .ne(PersonalProfile::getClientProfileCode, client.getClientCode()));
        if (!dup.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该身份证号已认证");
        }
        // 落库个人档案（upsert，1:1）
        PersonalProfile profile = personalProfileMapper.selectOne(new LambdaQueryWrapper<PersonalProfile>()
                .eq(PersonalProfile::getClientProfileCode, client.getClientCode()));
        boolean isNew = profile == null;
        if (isNew) {
            profile = new PersonalProfile();
            profile.setClientProfileCode(client.getClientCode());
        }
        profile.setRealName(req.getRealName());
        profile.setIdCardNo(req.getIdCardNo());
        profile.setIdCardHash(idCardHash);
        profile.setAge(req.getAge());
        profile.setCity(req.getCity());
        profile.setHouseFlag(req.getHouseFlag());
        profile.setCarFlag(req.getCarFlag());
        profile.setSocialSecurityFlag(req.getSocialSecurityFlag());
        profile.setFundFlag(req.getFundFlag());
        profile.setUpdatedBy("mini");
        if (isNew) {
            profile.setCreatedBy("mini");
            personalProfileMapper.insert(profile);
        } else {
            personalProfileMapper.updateById(profile);
        }
        // 落认证留痕（Mock 三要素）
        PersonalAuth auth = new PersonalAuth();
        auth.setClientProfileCode(client.getClientCode());
        auth.setAuthType("PHONE_THREE_ELEMENT");
        auth.setAuthStatus("SUCCESS");
        auth.setFailReason(MOCK_MARK);
        auth.setAuthTime(LocalDateTime.now());
        auth.setCreatedBy("mini");
        personalAuthMapper.insert(auth);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clientCode", clientCode);
        m.put("realName", DesensitizeUtils.name(profile.getRealName()));
        m.put("idCardNo", DesensitizeUtils.idCard(profile.getIdCardNo()));
        m.put("authStatus", "SUCCESS");
        m.put("authType", "PHONE_THREE_ELEMENT");
        return m;
    }

    /**
     * 管理端读个人档（脱敏）。
     *
     * @param clientCode 客户编码
     * @return 个人档（脱敏后），无则返回空 map
     */
    public Map<String, Object> getByClientCode(String clientCode) {
        ClientProfile client = clientProfileMapper.selectOne(new LambdaQueryWrapper<ClientProfile>()
                .eq(ClientProfile::getClientCode, clientCode));
        if (client == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "客户档案不存在");
        }
        PersonalProfile profile = personalProfileMapper.selectOne(new LambdaQueryWrapper<PersonalProfile>()
                .eq(PersonalProfile::getClientProfileCode, client.getClientCode()));
        Map<String, Object> m = new LinkedHashMap<>();
        if (profile == null) {
            return m;
        }
        m.put("clientProfileCode", profile.getClientProfileCode());
        m.put("realName", DesensitizeUtils.name(profile.getRealName()));
        m.put("idCardNo", DesensitizeUtils.idCard(profile.getIdCardNo()));
        m.put("age", profile.getAge());
        m.put("city", profile.getCity());
        m.put("houseFlag", profile.getHouseFlag());
        m.put("carFlag", profile.getCarFlag());
        m.put("socialSecurityFlag", profile.getSocialSecurityFlag());
        m.put("fundFlag", profile.getFundFlag());
        m.put("createdAt", profile.getCreatedAt());
        m.put("updatedAt", profile.getUpdatedAt());
        return m;
    }

    /**
     * 客户是否已完成个人认证（存在 PHONE_THREE_ELEMENT SUCCESS 留痕）。
     *
     * @param clientProfileCode 客户编码（业务ID）
     * @return true 已认证
     */
    public boolean hasAuthenticated(String clientProfileCode) {
        if (!StringUtils.hasText(clientProfileCode)) {
            return false;
        }
        Long count = personalAuthMapper.selectCount(new LambdaQueryWrapper<PersonalAuth>()
                .eq(PersonalAuth::getClientProfileCode, clientProfileCode)
                .eq(PersonalAuth::getAuthStatus, "SUCCESS"));
        return count != null && count > 0;
    }
}
