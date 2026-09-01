package com.loan.dict.controller;

import com.loan.common.Result;
import com.loan.dict.service.DictService;
import com.loan.dict.vo.DictItemVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 枚举字典接口（前端解析枚举值的唯一来源）。
 *
 * <p>契约：前端不得硬编码枚举值。前端启动后调用 {@code GET /api/dict/all} 拉取全部枚举字典，
 * 展示与选择时使用中文 label，存储/传输使用 code。枚举新增/调整只需改 {@link DictService}，前端无需发版。
 *
 * @author loan-platform
 */
@RestController
@RequestMapping("/api/dict")
public class DictController {

    /** 字典服务 */
    @Resource
    private DictService dictService;

    /**
     * 拉取全部枚举字典。
     *
     * @return type → 枚举条目列表
     */
    @GetMapping("/all")
    public Result<Map<String, List<DictItemVO>>> listAll() {
        return Result.ok(dictService.listAll());
    }
}
