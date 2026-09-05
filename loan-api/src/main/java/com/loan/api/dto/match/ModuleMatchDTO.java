package com.loan.api.dto.match;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 模块匹配结果（结果树中间节点，Dubbo 跨系统契约）。
 *
 * @author loan-platform
 */
@Data
public class ModuleMatchDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模块编码 */
    private String moduleCode;

    /** 模块名称 */
    private String moduleName;

    /** 模块逻辑（AND/OR） */
    private String logicType;

    /** 是否全局前置风控模块 */
    private boolean globalPre;

    /** 模块是否通过（AND 全过 / OR 任一过） */
    private boolean modulePassed;

    /** 步骤结果列表 */
    private List<StepMatchDTO> steps = new ArrayList<>();
}
