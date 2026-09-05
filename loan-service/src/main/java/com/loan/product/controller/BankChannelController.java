package com.loan.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.loan.common.Result;
import com.loan.product.entity.BankChannel;
import com.loan.product.mapper.BankChannelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合作渠道 HTTP 接口（Web 管理端，渠道下拉/渠道列表）。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/admin/channel")
@RequiredArgsConstructor
public class BankChannelController {

    private final BankChannelMapper bankChannelMapper;

    /**
     * 渠道列表（仅启用，供下拉选择）。
     *
     * @return 渠道列表
     */
    @GetMapping("/list")
    public Result<List<BankChannel>> list() {
        return Result.ok(bankChannelMapper.selectList(
                new LambdaQueryWrapper<BankChannel>()
                        .eq(BankChannel::getStatus, "ACTIVE")
                        .orderByAsc(BankChannel::getId)));
    }
}
