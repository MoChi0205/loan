package com.loan.api.facade;

import com.loan.api.dto.Result;
import com.loan.api.dto.match.MatchRequestDTO;
import com.loan.api.dto.match.MatchResultDTO;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 匹配服务契约（Dubbo 跨系统接口）。
 *
 * <p>供其他业务系统经 Dubbo 调用规则引擎做客户准入匹配，实现「多业务系统解耦」。
 * 客户端匹配出参只含档位 + 数量，产品/银行明细仅内部系统可见（合规边界）。
 *
 * @author loan-platform
 */
@DubboService(version = "1.0.0")
public interface MatchFacade {

    /**
     * 影子执行匹配（dryRun，不落线上审计）。
     *
     * @param request 匹配请求（客群 + 经营事实）
     * @return 匹配结果（档位 + 数量 + 产品结果树）
     */
    Result<MatchResultDTO> shadowMatch(MatchRequestDTO request);
}
