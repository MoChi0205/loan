# 应用 App

> 由本地大脑自动蒸馏，生成时间 2026-09-28T14:09:13。

条目数：**44**


## 项目：abs

### abs · AB实验
  - *类型:implementation*
  - AB 分流与准入
  - 关联：按项目浏览、ABS AB实验平台、ABS分层与开发规范、准入与输出标签、批量分流预取管道、ABS核心表对象、ABS文档索引入口、MD5取模+weight定组


## 项目：compliance

### 合规专项
  - *类型:implementation*
  - 金融产品网络营销办法
  - 关联：按项目浏览、经营健康六维模型、合规四件套(前置签署)、金融产品网络营销办法生效日、金融产品网络营销主体资格、第三方平台不等于金融持牌机构、该个体户执照与贷款营销不匹配、第三方平台不得介入销售环节


## 项目：loan-main

### OCR/VLM不是外部查人接口
  - *类型:implementation*
  - OCR/VLM仅处理客户上传材料；当前开发配置API Key为空，默认降级为空事实，不执行真实识别调用。
  - 关联：当前未接入免费用户信息查询、loan-main · 企融通后端

### P1今日服务台直接聚合
  - *域:service-operations · 类型:read-model*
  - 今日服务台按角色范围直接读取业务表，返回公司现场来访、员工上门外出、待回访、活跃工单；视频和电话不计入来访或外出，P4保持DTO不变切换预聚合缓存。
  - 关联：公司现场来访统计口径、每日服务名单聚合

### Web 管理端 loan-web
  - *类型:implementation*
  - Vue3 + Element Plus + Vite
  - 关联：客户三段式范围、线索创建人与归属人分离展示、构建残留待清理、企融通贷款系统重构、合规整改(资金/审核)、一键双主题切换、loan-main · 企融通后端

### loan-main · 企融通后端
  - *类型:implementation*
  - Spring/Dubbo/PolarDB 后端
  - 关联：按项目浏览、企融通贷款系统重构、顾问可切换公司客户风险、后端验证码登录、短信验证码接口、业务名称批量缓存、既有测试构造器过期、缓存故障降级

### mp-weixin 真机构建
  - *类型:implementation*
  - build:mp-weixin 受沙箱批量删除守卫影响，需授权清理
  - 关联：小程序端 loan-mini、沙箱批量删除守卫、loan-main · 企融通后端

### 内部产品审批无渠道账号
  - *域:client-lifecycle · 类型:implementation*
  - 公司员工提交产品时 t_product_approval.channel_user_id 允许为空，渠道提交仍记录渠道账号。
  - 关联：新产品状态由系统控制、loan-main · 企融通后端

### 初筛报告独立菜单
  - *域:operations-report · 类型:implementation*
  - 初筛报告从经营概览内容中移除，作为数据与报表下独立页面展示。
  - 关联：经营概览角色数据范围、loan-main · 企融通后端

### 后端验证码登录
  - *类型:implementation*
  - /api/auth/code-login 按手机号匹配员工或渠道账号并签发会话
  - 关联：loan-main · 企融通后端

### 客户DTO固定数据来源字段
  - *类型:implementation*
  - CustomerReportDetail固定返回dataSourceNotice：仅使用客户主动填写及授权上传材料，未调用外部个人信息查询接口。
  - 关联：报告固定数据来源说明、loan-main · 企融通后端

### 客户两级审批生产迁移
  - *域:client-lifecycle · 类型:implementation*
  - 生产表已具备 approval_stage、team_approver_staff_code、team_approved_at；实体查询恢复。
  - 关联：跨团队客户转移两级审批、loan-main · 企融通后端

### 客户响应使用独立DTO
  - *类型:implementation*
  - /api/mini/match/run 按登录角色分流：客户仅返回 reportNo、analysisStatus、analysisLabel、riskSummary、riskFactors、analysisNotice；员工继续使用内部匹配 DTO。
  - 关联：内部分析工具隔离条件、客户报告查询仅含风险分析数据、客户报告真实接口隔离验证、客户风险分析隔离提交、客户员工双报告快照、P0客户员工报告DTO分离、loan-main · 企融通后端

### 客户报告查询仅含风险分析数据
  - *类型:implementation*
  - 客户报告列表移除 grade、bankCount、productCount、passCount、conditionCount、rejectCount；详情底层仅返回报告元数据，再补充非产品化风险分析。
  - 关联：客户响应使用独立DTO、小程序与H5按角色隔离展示、报告原型双标签交互、loan-main · 企融通后端

### 旭荣报告模板实例关联
  - *域:loan-consult · 类型:implementation*
  - 上海旭荣网络科技内部报告已关联员工经营咨询与合作渠道模板；当前实例暂不填具体渠道/进件产品，待客户材料和渠道库版本核验后由授权员工补充。
  - 关联：员工经营咨询与渠道报告模板、loan-main · 企融通后端

### 服务运营P0四表模型
  - *域:service-operations · 类型:implementation*
  - 本地开发库新增预约、员工外出、客户级跟进、统一活动事件四表；全程使用业务编号关联，不修改渠道合作、银行产品、匹配或审批表。
  - 关联：按服务方式区分预约状态机、外出打卡加密单点定位、服务运营P1真实记录链路

### 本地认证测试账号
  - *域:authentication · 类型:test-data*
  - 本地种子覆盖BOSS、顾问、部门经理、运营、超管、超级管理员、渠道；统一开发密码Test123456。
  - 关联：本地认证联调环境

### 生产生命周期迁移已执行
  - *域:client-lifecycle · 类型:implementation*
  - 远程 loan_db 已创建生命周期事件表与唯一索引，补历史基线40条；二次执行新增0条。
  - 关联：历史生命周期基线估算、loan-main · 企融通后端

### 用户查询菜单
  - *域:client-lifecycle · 类型:implementation*
  - 员工可按姓名、企业、手机号、证件查询归属，并分流直接认领或转移申请。
  - 关联：客户公海直接认领、跨团队客户转移两级审批、本人客户认领闭环、loan-main · 企融通后端

### 短信验证码接口
  - *类型:implementation*
  - 复用 /api/sms/send-code 与 Redis 频控校验
  - 关联：loan-main · 企融通后端

### 经营分析范围隔离缓存
  - *类型:implementation*
  - 经营概览、运营分析和趋势使用Caffeine+Redis二级缓存；缓存键按角色、员工、部门和MY/TEAM/ALL范围隔离。
  - 关联：经营概览XXL-Job预热、员工报告客户身份信息


## 项目：loan-mini

### loan-mini · 小程序/H5
  - *类型:implementation*
  - Vue 小程序与 H5 前端
  - 关联：按项目浏览、AppIcon 新增 xs 规格、验证码登录、渠道无公海与消息一致性、客户菜单三范围核查、H5登录方式切换规范、H5与小程序实施范围、H5手机号次登录

### 客户风险分析隔离提交
  - *类型:implementation*
  - 提交 32de98a（feat(compliance): isolate customer risk analysis）包含后端角色响应隔离、小程序/H5客户风险分析展示与登录协议默认不勾选，共19个文件。
  - 关联：客户响应使用独立DTO、合规改动独立提交范围、loan-mini · 小程序/H5

### 小程序H5先材料后报告
  - *域:client-lifecycle · 类型:implementation*
  - 小程序与H5上传无需先有报告号，材料按客户进入复核；待复核时禁用匹配。
  - 关联：材料客户全链路绑定、loan-mini · 小程序/H5

### 小程序端 loan-mini
  - *类型:implementation*
  - uni-app Vue3，编译 mp-weixin + H5
  - 关联：线索创建人与归属人分离展示、小程序首页重做、构建残留待清理、合规整改(资金/审核)、mp-weixin 真机构建、一键双主题切换、单例主题 useThemeMode、小程序我的线索按归属筛选

### 当前角色底部导航
  - *类型:implementation*
  - 客户5项：首页/智能匹配/我的报告/服务单/我的；渠道与顾问4项：首页/线索录入/我的客户/我的；部门经理与运营5项含审批中心；老板与超级管理员当前源码含审批中心并将其置于智能匹配之前。
  - 关联：TabBar 原型规范与落地、loan-mini · 小程序/H5


## 项目：market-cdp

### market · CDP
  - *类型:implementation*
  - 采集/OneID/StarRocks
  - 关联：按项目浏览、Cursor/Codex Skills 总目录、CDP 可靠性与容量 Skill、OneID = users.id、采集管线 Kafka+异常Staging、设备/OAID 触点规则、CDP 方案澄清文档、CDP 架构图索引


## 项目：mds

### MDS系统讲解稿
  - *类型:implementation*
  - 无引用讲解文档：能力地图+Hub/Hit/回传/V2双开关+Mermaid流程图
  - 关联：公共能力准入条件、mds · 营销中台

### mds · 营销中台
  - *类型:implementation*
  - 渠道中台与 superpowers
  - 关联：按项目浏览、探索成功vs公共能力、MDS 营销渠道中台、渠道准入 V2 引擎、MDS 未完成项、llm-wiki 知识库、市场回传切新、MDS 历史 Skills


## 项目：platform

### 平台工具
  - *类型:implementation*
  - Skills/大脑/跨项目
  - 关联：按项目浏览、i-have-adhd 输出风格技能、Skills渐进披露结构、AI原生阶段V沉淀复用、knowledge-capture 捕获技能、角色位置 Figma 落稿规格、GitHub Figma 图标与组件技能、本地大脑索引


## 项目：（未归类）

### Loan 腾讯云 COS 属性映射
  - *类型:implementation*
  - 参考系统 erp.oss.mode=cos/erp.oss.cos.* 需映射为当前代码识别的 loan.oss.mode=tencent、loan.oss.bucket、loan.oss.tencent.*。
  - 关联：Loan prd 基础设施配置真值、Loan 远端 Redis 与 COS 烟测

### P2外出单点双打卡
  - *类型:implementation*
  - 上门预约可创建外出记录；仅外出员工本人可执行出发/返回打卡，Web只在点击时采集单点位置，不持续跟踪。
  - 关联：服务运营P2 Web统一工作区

### P2预约履约操作映射
  - *类型:implementation*
  - 页面按主服务顾问身份和后端状态显示确认、到店、开始、完成、爽约、改期、取消及最后5分钟异常处理，不把名单可见范围误作操作权限。
  - 关联：服务运营P2 Web统一工作区

### 外出关联字段可空
  - *类型:implementation*
  - t_staff_outing client_code和appointment_no改为可空，新增GENERAL类型迁移和页面普通外出入口。
  - 关联：普通外出可不关联客户

### 客户筛选维度缺口
  - *类型:implementation*
  - 现有客户筛选仅姓名、手机号、企业名、信用代码、建档/成交时间；待补归属人、部门、客群、来源、状态、跟进时效、成交状态。
  - 关联：公司员工统一我的客户入口

### 工作台权限范围缓存验收
  - *类型:implementation*
  - 本人、部门、全公司分别生成 SELF:ADV001、DEPT:CONSULT、COMPANY:BOSS 三个 Redis 键；refresh=true 回源写缓存，普通读取命中同范围键，未发生跨范围键复用。
  - 关联：工作台命中缓存仍有秒级延迟

### 工作台聚合与性能缺口
  - *类型:implementation*
  - 首页重复请求列表和统计，报表缓存未命中仍有大集合查询；需单一BFF和按维度汇总。
  - 关联：Loan系统反方综合审计

### 待回访业务定义
  - *类型:implementation*
  - 员工为客户填写下一次跟进时间后，所选日期需处理的客户跟进事项；不是自动外呼。
  - 关联：服务台角色数据范围

### 手机号超限审批真实验收
  - *类型:implementation*
  - 远程 prd/loan 数据源验收：ADV001 前30个客户直接查看，第31个生成 MANAGER_REVIEW；DEPT001 审批后顾问重试成功。31条不同客户留痕、31条授权。
  - 关联：手机号超限分级审批、Gateway Mono Void 空流二次拒绝陷阱、手机号与网关改动统一回归

### 普通外出迁移已执行
  - *类型:implementation*
  - 已从Nacos prd/loan解析远端110.42.219.5:9306/loan_db并成功执行；client_code和appointment_no可空，outing_type支持HOME_VISIT/GENERAL。
  - 关联：普通外出可不关联客户

### 服务工单业务编码表结构
  - *类型:implementation*
  - 远程t_service_order已完成业务编码迁移，仅保留client_profile_code和owner_staff_code，不再有旧的client_profile_id/owner_staff_id。
  - 关联：迁移执行追踪缺口

### 管理角色客户双入口
  - *类型:implementation*
  - 运营、老板、超管同时拥有“我的客户”和“全司客户”，分别对应本人归属与公司范围。
  - 关联：父菜单由叶子授权派生

### 远程测试外出审批链路
  - *类型:implementation*
  - 通过Nacos prd配置连接远程测试库，创建outing_test_20260927_01并由王经理审批为READY。
  - 关联：Nacos数据源红线

### 项目免费 API 盘点
  - *类型:implementation*
  - 项目内部 /api/admin、/api/mini、/api/auth、/api/sms 等接口不产生第三方按次费用，但消耗本项目服务、数据库与 Redis 资源；本轮只读盘点确认。
  - 关联：开发环境第三方接口 Mock 状态

