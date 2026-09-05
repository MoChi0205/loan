package com.loan.facade.impl;

import com.loan.api.dto.Result;
import com.loan.api.dto.match.MatchRequestDTO;
import com.loan.api.dto.match.MatchResultDTO;
import com.loan.api.dto.match.ModuleMatchDTO;
import com.loan.api.dto.match.ProductMatchDTO;
import com.loan.api.dto.match.StepMatchDTO;
import com.loan.api.facade.MatchFacade;
import com.loan.engine.demo.DemoPlanProvider;
import com.loan.engine.dto.MatchResultVO;
import com.loan.engine.dto.ModuleMatchVO;
import com.loan.engine.dto.ProductMatchVO;
import com.loan.engine.dto.StepMatchVO;
import com.loan.engine.enums.CustomerGroup;
import com.loan.engine.execute.AdmissionContext;
import com.loan.engine.execute.ProductPlan;
import com.loan.engine.service.MatchService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 匹配服务实现。
 *
 * <p>开发阶段用 {@code @Service}（Spring Bean，HTTP 内部调用）；后期需要其他系统经 Dubbo 跨系统
 * 调用时，将本类注解改为 {@code @DubboService(version = "1.0.0")} 并确保 Nacos 里 dubbo 注册地址
 * 已配置真实值即可（契约已下沉 loan-api）。
 *
 * @author loan-platform
 */
@Service
public class MatchFacadeImpl implements MatchFacade {

    /** 示例计划提供器（阶段一内存构造；正式匹配切 DB 计划加载） */
    @Resource
    private DemoPlanProvider demoPlanProvider;

    /** 匹配编排服务 */
    @Resource
    private MatchService matchService;

    @Override
    public Result<MatchResultDTO> shadowMatch(MatchRequestDTO request) {
        try {
            CustomerGroup group = CustomerGroup.fromCode(request.getCustomerGroup());
            if (group == null) {
                group = CustomerGroup.ENTERPRISE;
            }
            AdmissionContext context = AdmissionContext.builder()
                    .traceUuid(UUID.randomUUID().toString().replace("-", ""))
                    .customerGroup(group)
                    .fieldValues(request.getFacts())
                    .build();

            List<ProductPlan> productPlans = Collections.singletonList(buildDemoProductPlan());
            MatchResultVO vo = matchService.match(context, productPlans);
            return Result.ok(toDTO(vo));
        } catch (Exception e) {
            return Result.fail(3000, "匹配执行失败: " + e.getMessage());
        }
    }

    /**
     * 构造示例产品-计划绑定（对应初始化数据里的武汉企业税贷产品）。
     */
    private ProductPlan buildDemoProductPlan() {
        return new ProductPlan(9001L, "DEMO_WUHAN_TAX", "武汉某行·企业税贷（示例）", demoPlanProvider.buildEnterpriseTaxPlan());
    }

    /**
     * VO → DTO 转换（engine 内部 VO 与 loan-api 跨系统契约分离）。
     */
    private MatchResultDTO toDTO(MatchResultVO vo) {
        MatchResultDTO dto = new MatchResultDTO();
        dto.setGrade(vo.getGrade());
        dto.setBankCount(vo.getBankCount());
        dto.setProductCount(vo.getProductCount());
        dto.setPassCount(vo.getPassCount());
        dto.setConditionCount(vo.getConditionCount());
        dto.setRejectCount(vo.getRejectCount());
        for (ProductMatchVO p : vo.getProducts()) {
            ProductMatchDTO pd = new ProductMatchDTO();
            pd.setProductId(p.getProductId());
            pd.setProductCode(p.getProductCode());
            pd.setProductName(p.getProductName());
            pd.setTotalResult(p.getTotalResult());
            for (ModuleMatchVO m : p.getModules()) {
                ModuleMatchDTO md = new ModuleMatchDTO();
                md.setModuleCode(m.getModuleCode());
                md.setModuleName(m.getModuleName());
                md.setLogicType(m.getLogicType());
                md.setGlobalPre(m.isGlobalPre());
                md.setModulePassed(m.isModulePassed());
                for (StepMatchVO s : m.getSteps()) {
                    StepMatchDTO sd = new StepMatchDTO();
                    sd.setRuleCode(s.getRuleCode());
                    sd.setRuleName(s.getRuleName());
                    sd.setFieldCode(s.getFieldCode());
                    sd.setExpression(s.getExpression());
                    sd.setStepResult(s.getStepResult());
                    sd.setDetail(s.getDetail());
                    md.getSteps().add(sd);
                }
                pd.getModules().add(md);
            }
            dto.getProducts().add(pd);
        }
        return dto;
    }
}
