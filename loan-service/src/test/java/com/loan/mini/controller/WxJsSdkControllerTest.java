package com.loan.mini.controller;

import com.loan.common.ResultCode;
import com.loan.context.UserContext;
import com.loan.exception.BusinessException;
import com.loan.exception.GlobalExceptionHandler;
import com.loan.infrastructure.wechat.WxJsSdkService;
import com.loan.infrastructure.wechat.WxProperties;
import com.loan.test.CurrentUserArgumentResolver;
import com.loan.test.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 微信 JS-SDK 签名接口契约测试（WxJsSdkController，此前零覆盖）。
 *
 * <p>Controller 层只做一件事：把页面 url 原样透传给 {@link WxJsSdkService} 并包信封。
 * 因此本类重点验证：是否要求登录、url 缺失 / 非法时被拒、下游异常不被吞成 500。</p>
 *
 * <p>参数合法性用例使用<b>真实</b> WxJsSdkService（凭据未配置，不会发起任何网络调用）。</p>
 */
class WxJsSdkControllerTest {

    private MockMvc mvc;
    private WxJsSdkService wxJsSdkService;

    @BeforeEach
    void setUp() {
        wxJsSdkService = mock(WxJsSdkService.class);
        mvc = build(new WxJsSdkController(wxJsSdkService));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("GET /jssdk/signature：未登录也可获取签名（当前契约：签名接口不校验登录态）")
    void signatureWorksWithoutLogin() throws Exception {
        when(wxJsSdkService.signature(eq("https://m.example.com/apply"))).thenReturn(signature());

        mvc.perform(get("/api/mini/wechat/jssdk/signature")
                        .param("url", "https://m.example.com/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.appId").value("wxoa_test"))
                .andExpect(jsonPath("$.data.nonceStr").value("nonce001"))
                .andExpect(jsonPath("$.data.signature").value("sig001"));
    }

    @Test
    @DisplayName("GET /jssdk/signature：登录态不影响签名结果，url 原样透传给 Service")
    void signatureIsIndependentOfLoginState() throws Exception {
        UserContext.setUser(TestUsers.customerUser());
        when(wxJsSdkService.signature(eq("https://m.example.com/product/P001"))).thenReturn(signature());

        mvc.perform(get("/api/mini/wechat/jssdk/signature")
                        .param("url", "https://m.example.com/product/P001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(wxJsSdkService).signature("https://m.example.com/product/P001");
    }

    @Test
    @DisplayName("GET /jssdk/signature：url 缺失时被拒（当前落到兜底处理器，返回 5000 而非 1001）")
    void signatureRejectsMissingUrl() throws Exception {
        UserContext.setUser(TestUsers.customerUser());

        mvc.perform(get("/api/mini/wechat/jssdk/signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.INTERNAL_ERROR.getCode()));

        verify(wxJsSdkService, org.mockito.Mockito.never()).signature(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("GET /jssdk/signature：url 为空串 / 纯空格时被拒（PARAM_ERROR 1001）")
    void signatureRejectsBlankUrl() throws Exception {
        mvc = build(new WxJsSdkController(new WxJsSdkService(new WxProperties())));

        mvc.perform(get("/api/mini/wechat/jssdk/signature").param("url", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        mvc.perform(get("/api/mini/wechat/jssdk/signature").param("url", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("GET /jssdk/signature：未配置真实公众号凭据时被拒（RULE_CONFIG_ERROR 4000），不发起网络调用")
    void signatureRejectsUnconfiguredCredentials() throws Exception {
        WxProperties props = new WxProperties();
        props.setOaAppid("wx_CHANGE_ME");
        props.setOaSecret("secret");
        mvc = build(new WxJsSdkController(new WxJsSdkService(props)));

        mvc.perform(get("/api/mini/wechat/jssdk/signature")
                        .param("url", "https://m.example.com/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.RULE_CONFIG_ERROR.getCode()));
    }

    @Test
    @DisplayName("GET /jssdk/signature：Service 抛业务异常时原码透传，不被吞成 5000")
    void signaturePropagatesBusinessException() throws Exception {
        when(wxJsSdkService.signature(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new BusinessException(ResultCode.THIRD_PARTY_ERROR, "微信 JS-SDK ticket 获取失败"));

        mvc.perform(get("/api/mini/wechat/jssdk/signature")
                        .param("url", "https://m.example.com/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.THIRD_PARTY_ERROR.getCode()));
    }

    @Test
    @DisplayName("GET /jssdk/signature：url 不校验协议与域名（任意字符串原样透传，签名作用域完全依赖前端）")
    void signatureDoesNotValidateUrlFormat() throws Exception {
        when(wxJsSdkService.signature(eq("javascript:alert(1)"))).thenReturn(signature());

        mvc.perform(get("/api/mini/wechat/jssdk/signature")
                        .param("url", "javascript:alert(1)"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(wxJsSdkService).signature("javascript:alert(1)");
    }

    @Test
    @DisplayName("GET /jssdk/signature：员工与渠道身份均可调用（接口无角色守卫）")
    void signatureHasNoRoleGuard() throws Exception {
        UserContext.setUser(TestUsers.channelUser());
        when(wxJsSdkService.signature(org.mockito.ArgumentMatchers.anyString())).thenReturn(signature());

        mvc.perform(get("/api/mini/wechat/jssdk/signature")
                        .param("url", "https://m.example.com/apply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private MockMvc build(WxJsSdkController controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    private Map<String, String> signature() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("appId", "wxoa_test");
        data.put("timestamp", "1700000000");
        data.put("nonceStr", "nonce001");
        data.put("signature", "sig001");
        return data;
    }
}
