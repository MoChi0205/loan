"""当前项目 Web 菜单权限矩阵唯一真源（用于审计，不直接连接数据库）。"""

ROLES = ("CHANNEL", "ADVISER", "DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER")

MENUS = {
    "/workbench": {"name": "工作台", "domain": "工作台", "roles": set(ROLES)},
    "/lead": {"name": "线索管理", "domain": "客户经营", "roles": set(ROLES)},
    "/client": {"name": "客户档案", "domain": "客户经营", "roles": set(ROLES)},
    "/ocr": {"name": "材料识别", "domain": "客户经营", "roles": {"ADVISER", "DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/screening": {"name": "初筛任务", "domain": "匹配与规则", "roles": {"ADVISER", "DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/rule-template": {"name": "规则库", "domain": "匹配与规则", "roles": {"DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/rule": {"name": "规则集", "domain": "匹配与规则", "roles": {"DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/strategy-template": {"name": "策略方案", "domain": "匹配与规则", "roles": {"DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/plan-edit": {"name": "执行计划", "domain": "匹配与规则", "roles": {"DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/order": {"name": "服务工单", "domain": "服务与审核", "roles": set(ROLES) - {"CHANNEL"}},
    "/approval": {"name": "审批中心", "domain": "服务与审核", "roles": {"DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/sms": {"name": "短信服务", "domain": "运营与激励", "roles": {"DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/reward": {"name": "奖励发放", "domain": "运营与激励", "roles": {"DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/reward-rule": {"name": "奖励规则", "domain": "运营与激励", "roles": {"DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/audit": {"name": "审计日志", "domain": "运营与激励", "roles": {"DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/report/center": {"name": "经营概览", "domain": "数据与报表", "roles": {"ADVISER", "DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/report/trend": {"name": "趋势分析", "domain": "数据与报表", "roles": {"DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/report/screening": {"name": "初筛报告", "domain": "数据与报表", "roles": {"CHANNEL", "DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/report-template": {"name": "报告模板", "domain": "数据与报表", "roles": {"DEPT_MANAGER", "OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/product": {"name": "产品库", "domain": "产品与渠道", "roles": set(ROLES)},
    "/channel-config": {"name": "渠道档案", "domain": "产品与渠道", "roles": {"OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/channel-strategy": {"name": "渠道准入", "domain": "产品与渠道", "roles": {"OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/channel-user-list": {"name": "渠道名单", "domain": "产品与渠道", "roles": {"OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/blacklist": {"name": "风控名单", "domain": "产品与渠道", "roles": {"OPERATOR", "BOSS", "SUPER_ADMIN", "SUPER"}},
    "/org": {"name": "组织权限", "domain": "系统管理", "roles": {"OPERATOR", "SUPER_ADMIN", "SUPER"}},
    "/config-wizard": {"name": "系统配置", "domain": "系统管理", "roles": {"OPERATOR", "SUPER_ADMIN", "SUPER"}},
    "/debug": {"name": "调试中心", "domain": "系统管理", "roles": {"SUPER_ADMIN", "SUPER"}},
}

def role_paths(role):
    return {path for path, item in MENUS.items() if role in item["roles"]}
