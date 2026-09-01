package com.loan.personal.controller;

import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.personal.dto.PersonalAuthRequest;
import com.loan.personal.service.PersonalProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 个人认证与个人档案接口：
 * <ul>
 *   <li>小程序侧：POST /api/mini/auth/personal（登录后可认证，Mock 三要素）</li>
 *   <li>管理端：GET /api/admin/personal/{clientCode}（读个人档，脱敏）</li>
 * </ul>
 *
 * @author loan-platform
 */
@RestController
@RequiredArgsConstructor
public class PersonalController {

    private final PersonalProfileService personalProfileService;

    /**
     * 小程序个人认证（Mock 三要素校验 + 落库留痕）。
     *
     * @param req  认证请求
     * @param user 当前客户
     * @return 认证结果（脱敏）
     */
    @PostMapping("/api/mini/auth/personal")
    public Result<Map<String, Object>> personalAuth(@RequestBody PersonalAuthRequest req, @CurrentUser LoanUser user) {
        String clientCode = user == null || user.getUserNo() == null ? null : user.getUserNo();
        if (clientCode == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        return Result.ok(personalProfileService.personalAuth(clientCode, req));
    }

    /**
     * 管理端读个人档案（敏感字段脱敏）。
     *
     * @param clientCode 客户编码
     * @return 个人档
     */
    @GetMapping("/api/admin/personal/{clientCode}")
    public Result<Map<String, Object>> getPersonal(@PathVariable String clientCode) {
        return Result.ok(personalProfileService.getByClientCode(clientCode));
    }
}
