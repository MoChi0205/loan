# 设计 Design

> 由本地大脑自动蒸馏，生成时间 2026-09-28T14:16:28。

条目数：**190**


## 项目：abs

### ABS分层与开发规范
  - *类型:design-rule*
  - Controller→Facade→Service→Repository→Mapper；四阶段代码流程；.cursorrules=项目规则（无独立ABS Cursor Skill）
  - 关联：ABS AB实验平台、ABS文档索引入口、abs · AB实验

### ABS核心表对象
  - *类型:design-rule*
  - ab_experiments/group_bindings/domain_config/whitelist/audience_tag；assignments_user_no|cust_no|mobile_md5_encrypt；DDL见建表SQL与生产升级脚本
  - 关联：ABS AB实验平台、abs · AB实验

### AppIcon tab 专用变体
  - *类型:design-rule*
  - AppIcon 新增 variant=tab，仅 TabBar 使用；通过 TAB_RAWS 对齐原型 home/match/report/order/person/users/leads/shield 字形。
  - 关联：TabBar 重绘构建验证通过、TabBar 原型规范与落地、abs · AB实验

### 上游缓冲无Nacos配置
  - *类型:design-rule*
  - 本次Redis缓冲不上Nacos：TTL/开关均代码常量；复用现有StringRedisTemplate；发版代码即生效
  - 关联：上游分配Redis缓冲、abs · AB实验


## 项目：compliance

### 企业经营多维分析报告模板V1
  - *域:compliance · 类型:design-rule*
  - 形成18个正文模块和2个附录的合规经营分析模板，覆盖数据完整度、经营持续性、收支趋势、资金留存、客户/供应商集中度、交易类型、融资相关收支、税票流水一致性、财务结构、公私往来、核对清单和行动建议。
  - 关联：经营分析五模块、合规专项

### 报告双视角展示
  - *域:compliance · 类型:design-rule*
  - 客户视角只展示合法合规的经营分析；公司员工视角在此基础上增加内部画像、合作渠道、可能适配进件产品、材料缺口和服务建议；渠道与其他非员工不可见。
  - 关联：小程序与H5按角色隔离展示、员工经营咨询与渠道报告模板、三端双视角报告主链路、渠道合作保持现状、合规专项

### 第13条营销电话规则的适用边界
  - *域:compliance · 类型:design-rule*
  - 第13条要求营销电话提供拒绝或退订选择，但它是网络营销行为规范，不能脱离第2、3条单独把所有电话营销纳入本办法。
  - 关联：纯电话邀约不当然属于网络营销、合规专项

### 第三方平台不等于金融持牌机构
  - *域:compliance · 类型:design-rule*
  - 第三方平台无需因营销服务当然取得金融机构牌照，但须有金融机构依法委托并符合监管要求；不得转委托。
  - 关联：金融产品网络营销主体资格、贷款网络营销合规路径、合规专项

### 经营健康六维模型
  - *域:loan-consult · 类型:design-rule*
  - 页面使用盈利、成长、偿债、现金流、经营稳定、合规资质六维，并同时展示行业基准与证据。
  - 关联：单企业经营诊断报告原型V1、合规专项


## 项目：crm

### CRM 403 鉴权与页头标题修复
  - *类型:spec*
  - name: crm-403-auth-fix
  - 关联：项目索引 · crm

### CRM Docker 部署与运维
  - *类型:spec*
  - name: crm-ops-deployment
  - 关联：项目索引 · crm

### CRM 三机生产 Docker 部署（自建 MySQL + COS）
  - *类型:spec*
  - name: crm-cloud-mysql-deploy
  - 关联：项目索引 · crm

### CRM 公海、资源池与分配任务
  - *类型:spec*
  - name: crm-public-sea-allocation
  - 关联：项目索引 · crm

### CRM 前后端 API 契约强制检查
  - *类型:spec*
  - name: crm-api-contract-check
  - 关联：项目索引 · crm

### CRM 前端严格检查（强制）
  - *类型:spec*
  - name: crm-frontend-strict-check
  - 关联：项目索引 · crm

### CRM 前端开发规范（强制）
  - *类型:spec*
  - name: crm-frontend-standards
  - 关联：项目索引 · crm

### CRM 前端表单与筛选 UX 规范
  - *类型:spec*
  - name: crm-frontend-form-ux
  - 关联：项目索引 · crm

### CRM 历史教训（强制前置阅读）
  - *类型:spec*
  - name: crm-lessons-learned
  - 关联：项目索引 · crm

### CRM 后端开发规范（强制）
  - *类型:spec*
  - name: crm-backend-standards
  - 关联：项目索引 · crm

### CRM 客户资质与跟进 — 后端规范
  - *类型:spec*
  - name: crm-customer-profile-backend
  - 关联：项目索引 · crm

### CRM 客户跟进与资质
  - *类型:spec*
  - name: crm-customer-follow
  - 关联：项目索引 · crm

### CRM 布局与多页签导航
  - *类型:spec*
  - name: crm-layout-navigation
  - 关联：项目索引 · crm

### CRM 开发总门禁（每次必遵）
  - *类型:spec*
  - name: crm-dev-gate
  - 关联：项目索引 · crm

### CRM 手机号归属地（phone_area）
  - *类型:spec*
  - name: crm-phone-area
  - 关联：项目索引 · crm

### CRM 数据插入与新增（强制规范）
  - *类型:spec*
  - name: crm-data-insert-tenant
  - 关联：项目索引 · crm

### CRM 日期时间规范
  - *类型:spec*
  - name: crm-frontend-datetime
  - 关联：项目索引 · crm

### CRM 枚举语义与变更规范（当前口径）
  - *类型:spec*
  - name: crm-enum-semantics
  - 关联：项目索引 · crm

### CRM 生产发版（Mac pack → scp → 150.239 up）
  - *类型:spec*
  - name: crm-prod-release
  - 关联：项目索引 · crm

### CRM 离页通话浮窗（CallFloatingDock）
  - *类型:spec*
  - name: crm-outbound-floating-dock
  - 关联：项目索引 · crm

### CRM 统一批量任务框架
  - *类型:spec*
  - name: crm-batch-job-framework
  - 关联：项目索引 · crm

### CRM 统一确认弹窗
  - *类型:spec*
  - name: crm-frontend-confirm-dialog
  - 关联：项目索引 · crm

### CRM 统计分析 — 枚举与漏斗口径（权威 Skill）
  - *类型:spec*
  - name: crm-analytics-enums
  - 关联：项目索引 · crm

### CRM 菜单全栈联调
  - *类型:spec*
  - name: crm-menu-fullstack
  - 关联：项目索引 · crm

### CRM 菜单前端
  - *类型:spec*
  - name: crm-menu-frontend
  - 关联：项目索引 · crm

### CRM 菜单后端
  - *类型:spec*
  - name: crm-menu-backend
  - 关联：项目索引 · crm

### CRM 话机外呼模块（权威 Skill）
  - *类型:spec*
  - name: crm-outbound-module
  - 关联：项目索引 · crm

### CRM 通话单写库分层（强制）
  - *类型:spec*
  - name: crm-cti-call-record-write
  - 关联：项目索引 · crm

### CRM 通话时长 / 录音生产排查
  - *类型:spec*
  - name: crm-call-duration-troubleshoot
  - 关联：项目索引 · crm

### CRM漏斗图UI/UX优化技能
  - *类型:spec*
  - 1. **枚举与统计口径（必读）**：[crm-analytics-enums](../crm-analytics-enums/SKILL.md) · [docs2/统计分析/枚举定义.md](../../tse/docs2/统计分析/枚举定义.md)
  - 关联：项目索引 · crm

### 客户列表排序规范
  - *类型:spec*
  - name: crm-customer-list-sort
  - 关联：项目索引 · crm


## 项目：deepseek-harness

### Applying the DeepSeek Harness Documentation Standard
  - *类型:spec*
  - name: dsh-doc-standards
  - 关联：项目索引 · deepseek-harness

### Archive DeepSeek Harness Agent Notes
  - *类型:spec*
  - name: dsh-archive-agent-notes
  - 关联：项目索引 · deepseek-harness

### DSH Pre-Push Checks
  - *类型:spec*
  - name: dsh-pre-push-checks
  - 关联：项目索引 · deepseek-harness

### DeepSeek Harness Prose Standard
  - *类型:spec*
  - name: dsh-prose-standard
  - 关联：项目索引 · deepseek-harness

### Develop Dynamic Cordis Plugins
  - *类型:spec*
  - name: cordis-plugin-development
  - 关联：项目索引 · deepseek-harness

### Editing Cordis compositions
  - *类型:spec*
  - name: editing-cordis-compositions
  - 关联：项目索引 · deepseek-harness

### Finding DeepSeek Harness Simplifications
  - *类型:spec*
  - name: dsh-find-simplifications
  - 关联：项目索引 · deepseek-harness

### Landing an official GitHub PR stack
  - *类型:spec*
  - name: dsh-merging-stacked-prs
  - 关联：项目索引 · deepseek-harness

### Record Browser GIF
  - *类型:spec*
  - name: record-browser-gif
  - 关联：项目索引 · deepseek-harness

### Reviewing a DeepSeek-Harness PR
  - *类型:spec*
  - name: dsh-code-review
  - 关联：项目索引 · deepseek-harness

### Synchronizing the DeepSeek Harness Documentation Site
  - *类型:spec*
  - name: dsh-doc-site-sync
  - 关联：项目索引 · deepseek-harness

### Translating DeepSeek-Harness docs
  - *类型:spec*
  - name: dsh-translate-docs
  - 关联：项目索引 · deepseek-harness

### Trimming Chain-of-Thought Leakage
  - *类型:spec*
  - name: dsh-trim-cot-leakage
  - 关联：项目索引 · deepseek-harness


## 项目：loan-main

### AI 生成主题配图
  - *类型:design-rule*
  - login-bg-dark/light.png 文字风格随主题变化
  - 关联：墨金 Ink&Gold 设计系统、loan-main · 企融通后端

### P1内部报告聚合DTO
  - *域:operations-report · 类型:design-rule*
  - StaffAggregatedReport拆分StaffClientProfile、StaffMaterialSummary、StaffMatchedProduct，手机号和统一社会信用代码掩码，固定记录数据来源说明。
  - 关联：P1员工报告按reportNo聚合

### TSE客户范围参考
  - *类型:design-rule*
  - TSE按归属范围划分：SALES本人、MANAGER本团队、ADMIN本企业；公海分企业池与团队池。
  - 关联：客户范围语义拆分、loan-main · 企融通后端

### TSE经营报表经验
  - *域:operations-report · 类型:design-rule*
  - TSE将客户中心、公海分配、实时看板、客户漏斗、沟通统计、成交金额按实际关单日统计结合为经营分析体系。
  - 关联：客户运营统计接口、报表按归属范围统计、loan-main · 企融通后端

### TSE菜单矩阵单一真源
  - *类型:design-rule*
  - TSE采用角色权限矩阵作为唯一真源，再派生菜单种子、文档和校验脚本；修改顺序为矩阵、种子、生成文档、角色菜单验证。
  - 关联：Web菜单四层权限契约、loan-main · 企融通后端

### V2对外结果映射现网枚举
  - *类型:design-rule*
  - 切主只换决策核。Hub status 通过1/拒绝2；Hit单号与身份证 status 通过0/拒绝1；前缀 resultMap 通过true/拒绝false。内部 FinalOutcome 与 mockValue 不得原样返回。
  - 关联：渠道准入 V2 引擎、llm-wiki 知识库、loan-main · 企融通后端

### Web 下拉浮层视口边界
  - *类型:design-rule*
  - 全局 el-select popper 限制最大宽度与高度，列表区域滚动，文字省略；由 Popper 自动翻转避免遮挡弹窗内容。
  - 关联：小程序风险分析步骤导航

### Web内部聚合报告五模块视图
  - *域:operations-report · 类型:design-rule*
  - StaffAggregatedReport展示员工内部提示、客户画像、材料状态、经营分析、现有匹配结果和数据来源说明。
  - 关联：Web员工详情接入聚合接口

### 上门与远程服务统计隔离
  - *域:service-operations · 类型:metric-rule*
  - HOME_VISIT 计入员工外出服务；VIDEO_MEETING 与 PHONE_CONSULT 仅计入相应履约记录，均不计入公司到店，视频和电话也不计入员工外出。
  - 关联：客户服务方式四分类、每日服务名单聚合、上门拜访无需审批

### 产品状态下拉弹窗边界
  - *类型:design-rule*
  - 产品编辑弹窗内容区限制为视口高度并内部滚动，底部状态选择器向上展开，避免选项超出弹窗背景。

### 公海停留周期口径
  - *域:operations-report · 类型:design-rule*
  - 同一客户每次进入公海形成独立停留周期；主动释放、经理回收、系统回收均记录入池原因；公司公海与团队公海切换时结束旧周期并开启新周期。
  - 关联：公海停留自然小时口径与实现、客户运营统计接口、loan-main · 企融通后端

### 公海菜单语义统一
  - *域:client-lifecycle · 类型:design-rule*
  - 线索模块仅保留线索公海，客户档案仅保留客户公海；不再混用公司公海和客户公海。
  - 关联：loan-main · 企融通后端

### 内部分析工具隔离条件
  - *域:compliance · 类型:design-rule*
  - 应以角色和接口双重隔离产品库、规则、命中产品与银行数据；客户接口仅返回非产品化风险指标；对客报告不得包含产品数量、银行数量、可进件或匹配度。
  - 关联：纯内部规则引擎通常不属网络营销、独立风险分析可展示数据、客户响应使用独立DTO、客户报告隐藏银行覆盖、对客行业基准禁止默认值、员工产品渠道可见性、loan-main · 企融通后端

### 单企业经营诊断报告原型V1
  - *域:loan-consult · 类型:design-rule*
  - 墨金 Web 原型按结论、健康度、六维诊断、风险、趋势、融资策略、30/60/90行动计划和数据依据组织。
  - 关联：诊断行动闭环、诊断数据来源状态、经营健康六维模型、旭荣案例诊断模式、loan-main · 企融通后端

### 单行提示条
  - *类型:design-rule*
  - 操作说明使用单行省略、图标34px和顶部边界，避免布局粘连
  - 关联：loan-main · 企融通后端

### 原型空白区说明
  - *类型:design-rule*
  - 产品卡底部说明误用大空态导致大片留白，已改为紧凑提示条
  - 关联：loan-main · 企融通后端

### 墨金 Ink&Gold 设计系统
  - *类型:design-rule*
  - 双主题令牌：深墨蓝 #16203A / 暗底 #0E1626 / 暖金 #D9A441·#E0AE4E·#F2C879 / 信息蓝 #4C7BD9；已归档 docs/design-system/
  - 关联：图标统一 AppIcon、AI 生成主题配图、一键双主题切换、小程序首页重做、企融通贷款系统重构、loan-main · 企融通后端

### 外部数据来源可追溯模型
  - *域:operations-report · 类型:design-rule*
  - 如以后接入外部数据，每字段记录来源、查询时间、授权编号、原始响应摘要、可信度及是否允许对客展示；经营分析与内部银行匹配继续分轨。
  - 关联：经营结论证据可追溯、loan-main · 企融通后端

### 审计规则中文名称
  - *类型:design-rule*
  - 审计规则执行明细优先读取t_rule.rule_name，内置规则回退RuleCatalog中文名，不再把ruleCode作为主展示。
  - 关联：审计客户身份关联查询

### 客户员工双报告快照
  - *类型:design-rule*
  - 建议按reportNo+audienceType保存CUSTOMER/STAFF独立快照；客户快照服务端生成时即不含内部字段，避免依赖前端隐藏。
  - 关联：客户响应使用独立DTO、loan-main · 企融通后端

### 客户活动时间线回放
  - *域:audit-traceability · 类型:architecture*
  - 以追加式统一活动事件读模型聚合画像、材料、跟进、预约、到店、外出和工单动作，按角色裁剪可见字段并保留来源与操作者。
  - 关联：客户画像版本化快照方案、预约到店与员工外出链路、三端客户服务DTO分层、预约改期保留历史

### 客户画像版本化快照方案
  - *域:customer-insight · 类型:architecture*
  - 客户画像按快照版本保存维度、风险提示、经营建议、来源材料、数据期间和核验状态；客户端只读裁剪摘要，员工端读取完整来源链。
  - 关联：客户活动时间线回放

### 客户范围语义拆分
  - *域:client-lifecycle · 类型:design-rule*
  - 当前系统：我的客户=本人归属；团队已分配=本部门成员归属；全司已分配=全公司有归属；公司公海=ENTERPRISE未归属；团队公海=TEAM未归属。
  - 关联：线索菜单更名线索管理、团队公海菜单已新增、TSE客户范围参考、loan-main · 企融通后端

### 客户释放按钮归属守卫
  - *域:client-lifecycle · 类型:design-rule*
  - 释放回公海仅在我的客户且当前归属本人时展示，公海待分配客户不展示释放。
  - 关联：公司公海全员可见与认领、loan-main · 企融通后端

### 工作台角色化待办
  - *类型:design-rule*
  - 顾问展示本人认领/下载申请、工单和线索；经理展示团队认领审批；运营展示下载/分配/奖励；老板和超管展示产品、下载、分配及奖励待办。
  - 关联：我的审批全员入口、loan-main · 企融通后端

### 报告业务显示名
  - *类型:design-rule*
  - 报告列表与详情优先展示企业名称，个人客户回退姓名，标题统一为【企业/姓名】【YYYY年MM月DD日】。
  - 关联：无客户信息报告编号、报告日期模糊筛选、loan-main · 企融通后端

### 报表快照与流量分离
  - *类型:design-rule*
  - 客户资产和 SLA 使用当前快照，公海流转、分配回收、转化使用近 30/90/180 天流量；成交按 deal_time，其余阶段按 created_at。
  - 关联：客户运营统计接口、loan-main · 企融通后端

### 报表金额万元展示
  - *类型:design-rule*
  - 成交金额与奖励金额按万元展示，使用十进制字符串移位保留全部有效小数，不进行四舍五入。
  - 关联：loan-main · 企融通后端

### 旭荣网络内部经营咨询报告
  - *域:loan-consult · 类型:design-rule*
  - 生成仅员工用于电话与来访咨询的报告，按画像核验、材料补充、多维经营分析、30分钟沟通流程和行动清单组织；禁止客户端展示或直接转发。
  - 关联：旭荣画像证据边界、线下来访咨询五维框架、对客报告定位为经营分析、loan-main · 企融通后端

### 服务运营三端DTO边界
  - *域:cross-terminal · 类型:dto-boundary*
  - 客户DTO仅含顾问姓名、预约地点、状态和下一步；员工DTO含内部履约信息；渠道DTO固定拒绝且不携带客户名单、银行产品或准入字段。
  - 关联：三端客户服务DTO分层、服务名单分级权限策略

### 材料页诚实状态文案
  - *域:compliance · 类型:design-rule*
  - 小程序、H5、Web删除未实现的一致性自动核验承诺，改为分类上传、识别状态和人工复核的真实提示。
  - 关联：AI识别真实状态模型、真实材料链路回归基线

### 独立风险分析可展示数据
  - *域:compliance · 类型:design-rule*
  - 宜展示资料完整度、数据来源/日期、经营稳定性、现金流压力、负债结构、信用风险因素、材料异常和改善建议；结果必须与银行产品、准入和审批概率脱钩。
  - 关联：无银行委托时的产品定位、loan现有营销合规控制、内部分析工具隔离条件、对客报告定位为经营分析、loan-main · 企融通后端

### 线下来访咨询五维框架
  - *域:loan-consult · 类型:design-rule*
  - 员工咨询围绕经营持续性、现金流与资金留存、应收与客户集中度、资本负债与固定支出、财税账户规范五维展开，输出核对项和经营改善动作。
  - 关联：旭荣网络内部经营咨询报告、loan-main · 企融通后端

### 经营分析五模块
  - *域:operations-report · 类型:design-rule*
  - 建议页面由数据依据、经营概览、多维分析、重点发现、行动建议与边界说明构成；每条结论必须附数据依据和适用期间。
  - 关联：经营维度按数据可用性开放、企业经营多维分析报告模板V1、loan-main · 企融通后端

### 经营建议四段式文案
  - *域:operations-report · 类型:design-rule*
  - 建议统一采用观察事实、数据依据、经营影响、改进行动四段式，使用核对、关注、完善、跟踪等中性词，避免可进件、符合准入、通过率、预计额度和产品推荐。
  - 关联：无委托时应移除的对客数据、经营结论证据可追溯、loan-main · 企融通后端

### 经营概览图表重绘
  - *域:operations-report · 类型:design-rule*
  - 基于 ECharts 增加资产环图、流转横向柱图和渐变指标卡，适配深浅主题。
  - 关联：经营概览角色数据范围、loan-main · 企融通后端、经营分析 ECharts 数据提供器异常

### 经营维度按数据可用性开放
  - *域:operations-report · 类型:design-rule*
  - 当前字段支持经营持续性、开票、纳税、资料完整度；现金流、偿债压力、盈利质量和客户集中度须取得流水、财报或合同的真实结构化数据后再展示。
  - 关联：经营分析五模块、loan-main · 企融通后端

### 角色化报表命名
  - *域:operations-report · 类型:design-rule*
  - 老板与超级管理员显示经营概览；运营、部门经理、顾问显示实时看板。
  - 关联：角色化看板数据范围、loan-main · 企融通后端

### 认定原型基线
  - *类型:design-rule*
  - 三端视觉与交互以 docs/prototypes/redesign-all-roles-v1.html 为唯一基线。
  - 关联：消息未读单一口径、loan-main · 企融通后端

### 预约改期保留历史
  - *域:audit-traceability · 类型:data-rule*
  - 改期不得覆盖原预约；原记录标记RESCHEDULED并关联新预约，使客户服务过程可审计和完整回放。
  - 关联：预约变更五分钟截止线、客户活动时间线回放

### 风险分析与银行提交分轨
  - *域:compliance · 类型:design-rule*
  - 风险分析阶段不推荐或排名银行产品；若客户后续主动指定机构并单独授权提交，应采用独立流程，避免自动多行分发、系统推荐、批量推送或在分析报告中嵌入申请通道。
  - 关联：匹配引擎可能越过纯营销边界、loan-main · 企融通后端


## 项目：loan-mini

### AppIcon 新增 xs 规格
  - *类型:design-rule*
  - AppIcon 增加24rpx（12px）xs规格，用于我的页弱化身份标识。
  - 关联：我的页新增身份行、loan-mini · 小程序/H5

### H5与小程序视觉交互审计
  - *类型:design-rule*
  - 当前无可用Figma链接、节点ID或MCP工具，采用现有原型、loan-mini同源H5代码与预览图审计；发现预览图与当前实现版本不一致，需统一原型与代码真源后再做视觉微调。
  - 关联：loan-mini · 小程序/H5

### H5手机号次登录
  - *类型:design-rule*
  - 手机号验证码作为 H5 次入口，微信一键登录保持主入口，不添加密码页和记住我
  - 关联：loan-mini · 小程序/H5

### TabBar 原型规范与落地
  - *类型:design-rule*
  - 主导航图标约22–24px、激活态暖金、无顶部激活指示条；TabBar使用专用图标变体落地，不影响通用 AppIcon。
  - 关联：当前角色底部导航、AppIcon tab 专用变体、TabBar 当前图标几何、loan-mini · 小程序/H5

### TabBar 合并页（线索录入/我的客户）
  - *类型:design-rule*
  - D74：录入线索+录入产品→「线索录入」；我的客户+我的报告→「我的客户」。7 角色 tab 收敛 ≤5。合并页容器只管切换与底部导航，子视图靠 active 侦听刷新
  - 关联：小程序「我的客户」+公海认领、合并页组件化踩坑、loan-mini · 小程序/H5

### 三端双视角报告主链路
  - *域:operations-report · 类型:design-rule*
  - 客户小程序/H5读取客户经营报告；员工Web/小程序读取内部经营咨询与渠道产品报告；渠道仅供给本人产品和查看审批状态，不查看客户报告。
  - 关联：报告双视角展示、匹配池缺少合作有效性过滤、渠道合作保持现状、loan-mini · 小程序/H5

### 协议复选框对比度
  - *类型:design-rule*
  - 未选中使用明确 #718096 三像素边框，选中使用品牌蓝与外发光，不依赖可能缺失的主题变量
  - 关联：loan-mini · 小程序/H5

### 原型登录与真实代码同步
  - *类型:design-rule*
  - 原型小程序登录改为微信主入口、三步说明和默认选中协议；手机号验证码仅 H5 展示
  - 关联：loan-mini · 小程序/H5

### 图标统一 AppIcon
  - *类型:design-rule*
  - SVG 路径包 <path>、base64 data URI；默认色随主题
  - 关联：墨金 Ink&Gold 设计系统、loan-mini · 小程序/H5

### 客户菜单三范围核查
  - *域:client-lifecycle · 类型:design-rule*
  - Web 客户档案页已提供 我的客户/全司已分配客户/公司公海 三页签；小程序员工页仍为 我的客户/公海（公海内公司与团队），未直接呈现三页签。
  - 关联：Web 团队客户页签缺失、全司已分配角色范围缺口、loan-mini · 小程序/H5

### 客户风险分析报告页面原型
  - *域:compliance · 类型:design-rule*
  - 小程序客户报告原型采用信息待完善结果卡、报告信息/经营诊断双标签、风险分析说明、合规提示和重新分析操作；不展示命中产品、评级或规则日志。
  - 关联：小程序与H5按角色隔离展示、loan-mini · 小程序/H5

### 小程序登录设计基线
  - *类型:design-rule*
  - 登录改造必须以 pages/index/index.vue 真实微信登录页面为基线，只新增兼容通道
  - 关联：loan-mini · 小程序/H5

### 小程序首页紧凑数据排版
  - *类型:design-rule*
  - 快捷入口按数量自适应列宽并缩小间距与磁贴高度；顾问、报告卡片压缩纵向留白。
  - 关联：小程序风险分析步骤导航、三端 UI 构建验证

### 材料流程引导卡
  - *域:client-lifecycle · 类型:design-rule*
  - Web 初筛与小程序/H5 使用内嵌品牌流程卡，替代突兀的系统 Alert 提示。
  - 关联：智能材料准备清单、loan-mini · 小程序/H5

### 登录原型视觉基线
  - *类型:design-rule*
  - 小程序与H5登录统一采用白色导航、渐变Hero、玻璃标题卡、白色步骤卡和金色主按钮
  - 关联：loan-mini · 小程序/H5

### 登录文案单行规则
  - *域:compliance · 类型:design-rule*
  - 品牌副标题、流程说明、协议提示和合规声明应精简并尽量保持单行，避免换行破坏登录布局。
  - 关联：登录统一视觉系统、loan-mini · 小程序/H5

### 登录统一视觉系统
  - *域:compliance · 类型:design-rule*
  - 小程序与H5统一采用白色导航、深蓝到金色渐变Hero、玻璃标题卡、白色三步流程卡、金色主按钮、单行文案和紧凑合规提示。
  - 关联：登录文案单行规则、loan-mini · 小程序/H5

### 移动端代码同步原型
  - *类型:design-rule*
  - 新增redesign-mobile-code-baseline-2026-09-12.html，以loan-mini当前代码为唯一基线，覆盖七角色、动态Tab、审批权限、消息通知、客户搜索、线索与产品录入。
  - 关联：loan-mini · 小程序/H5

### 移动端视觉令牌分散
  - *类型:design-rule*
  - 首页、角色配置和TabBar存在多套硬编码色值，主题切换无法全局一致
  - 关联：三端页面重构待处理、loan-mini · 小程序/H5

### 角色身份不使用状态胶囊
  - *类型:design-rule*
  - 角色是稳定账户属性而非成功/警告等状态，不应使用高强调 Tag/Pill；建议以盾牌图标加次级文字展示。
  - 关联：角色位置 Figma 落稿规格、loan-main · 企融通后端

### 角色身份迁移到我的页
  - *类型:design-rule*
  - 首页 Hero 的角色胶囊与姓名及角色化页面重复，规划从首页移除；身份信息并入“我的”页个人资料头，作为静态账户属性弱展示。
  - 关联：首页移除角色胶囊、角色位置 Figma 落稿规格、我的页新增身份行、loan-mini · 小程序/H5

### 贷款数据可视化技能
  - *类型:design-rule*
  - 统一 AppIcon/AppEChart、角色数据范围、图表空态加载态和 10 万数据性能约束。
  - 关联：loan-mini · 小程序/H5

### 首页原型采用3+2快捷栅格
  - *类型:design-rule*
  - 客户首页候选原型将5个快捷入口由单行5列改为3+2栅格，恢复卡片宽度、图标呼吸感和文字可读性。
  - 关联：首页原型采用Hero叠层结构、loan-main · 企融通后端、loan-mini · 小程序/H5

### 首页原型采用Hero叠层结构
  - *类型:design-rule*
  - 快捷入口整体上移12px轻叠Hero，以建立首屏层次；顾问空态改为横向紧凑结构，报告卡增加图标锚点。
  - 关联：首页原型采用3+2快捷栅格、首页候选原型待确认、loan-main · 企融通后端、loan-mini · 小程序/H5

### 首页角色提示文案优化
  - *类型:design-rule*
  - 首页 Hero 描述与报告卡标题、提示、说明已按客户/渠道/顾问/部门经理/老板/运营管理员/超级管理员分别配置。
  - 关联：首页头像授权提示已移除、匹配诊断结果统一入口文案、loan-mini · 小程序/H5


## 项目：loan-platform

### 前端开发规范 · loan-platform
  - *类型:spec*
  - name: frontend-development
  - 关联：项目索引 · loan-platform

### 后端开发规范 · loan-platform
  - *类型:spec*
  - name: backend-development
  - 关联：项目索引 · loan-platform

### 文档归档规范
  - *类型:spec*
  - name: document-archiving
  - 关联：项目索引 · loan-platform

### 知识库检索规范
  - *类型:spec*
  - name: knowledge-base-retrieval
  - 关联：项目索引 · loan-platform


## 项目：lps

### 机构自有接口（非 OSI）：授信（Pilot Apply）
  - *类型:spec*
  - name: non-osi-apply
  - 关联：项目索引 · lps

### 机构自有接口（非 OSI）：绑卡（Pilot Bind Card）
  - *类型:spec*
  - name: non-osi-bindcard
  - 关联：项目索引 · lps

### 标准机构 OSI：授信（Pilot Apply）
  - *类型:spec*
  - name: osi-apply
  - 关联：项目索引 · lps

### 标准机构 OSI：绑卡（Pilot Bind Card）
  - *类型:spec*
  - name: osi-bindcard
  - 关联：项目索引 · lps


## 项目：market

### CDP 可靠性与容量约束（模块 1–4 · 数据底座）
  - *类型:spec*
  - name: cdp-reliability-scale
  - 关联：项目索引 · market

### CDP 可靠性与容量约束（模块 1–4）
  - *类型:spec*
  - name: cdp-reliability-scale
  - 关联：项目索引 · market


## 项目：market-cdp

### CDP 可靠性与容量 Skill
  - *类型:design-rule*
  - market CDP 模块1-4：Kafka削峰、OneID=users.id、staging异常-only、6k EPS、虚拟属性不进ingest、dict_version发布。Cursor真源：.cursor/skills/；Git副本：docs/superpowers/skills/
  - 关联：Cursor/Codex Skills 总目录、本地大脑索引、Cursor/Codex Skills、market · CDP、Skills 索引、market 项目 Skills 索引

### CDP 图01 系统总架构
  - *类型:design-rule*
  - 标准图01：系统总架构；PNG 可脑图侧栏预览
  - 关联：CDP 架构图索引、架构图

### CDP 图02 数据分层模型
  - *类型:design-rule*
  - 标准图02：数据分层模型；PNG 可脑图侧栏预览
  - 关联：CDP 架构图索引、架构图

### CDP 图03 接入认证时序
  - *类型:design-rule*
  - 标准图03：接入认证时序；PNG 可脑图侧栏预览
  - 关联：CDP 架构图索引、架构图

### CDP 图04 清洗落库管线
  - *类型:design-rule*
  - 标准图04：清洗落库管线；PNG 可脑图侧栏预览
  - 关联：CDP 架构图索引、架构图

### CDP 图05 用户身份模型
  - *类型:design-rule*
  - 标准图05：用户身份模型；PNG 可脑图侧栏预览
  - 关联：CDP 架构图索引、架构图

### CDP 图06 ID-Mapping决策
  - *类型:design-rule*
  - 标准图06：ID-Mapping决策；PNG 可脑图侧栏预览
  - 关联：CDP 架构图索引、架构图

### CDP 图07 Identify与Replay
  - *类型:design-rule*
  - 标准图07：Identify与Replay；PNG 可脑图侧栏预览
  - 关联：CDP 架构图索引、架构图

### CDP 图08 元数据发布
  - *类型:design-rule*
  - 标准图08：元数据发布；PNG 可脑图侧栏预览
  - 关联：CDP 架构图索引、架构图

### CDP 图09 JSON归一化
  - *类型:design-rule*
  - 标准图09：JSON归一化；PNG 可脑图侧栏预览
  - 关联：CDP 架构图索引、架构图

### CDP 图10 staging状态机
  - *类型:design-rule*
  - 标准图10：staging状态机；PNG 可脑图侧栏预览
  - 关联：CDP 架构图索引、架构图

### CDP 接入端设计 · market-cdp
  - *类型:design-rule*
  - 多端接入：T1 App · T2 Web · T3 HTTP/TRS · T4 Dubbo · T5 MQ → Canonical → Kafka。含端点、流程、报文模板、图11–13。
  - 关联：market · CDP、架构图、CDP 架构图索引、开发参考

### CDP 架构图索引
  - *类型:design-rule*
  - 10 张标准 PlantUML；开发 CDP 模块前先看图1/4/5/9
  - 关联：架构图、market · CDP、CDP 图01 系统总架构、CDP 图02 数据分层模型、CDP 图03 接入认证时序、CDP 图04 清洗落库管线、CDP 图05 用户身份模型、CDP 图06 ID-Mapping决策

### 多人AI编码·Facade先行
  - *类型:design-rule*
  - 并行 AI 编码最大坑是 Dubbo 接口对不齐。顺序：① Facade+DTO 入 -api ② 跨模块只依赖 -api ③ L1 详细设计评审 ④ 再 AI 写 service。入参统一为 CanonicalEvent。
  - 关联：开发参考、market · CDP、开发参考工作流、Skills 索引

### 用户属性→SR users 自动加列
  - *域:capability · 类型:design-rule*
  - PolarDB meta_user_properties 新增/发布用户属性后，须自动对 StarRocks users 执行 ALTER ADD COLUMN（对齐神策元数据→宽表 DML）；sr_sync_status=2 后 Consumer 才可写该列。禁止绕过 meta 手写 DML。
  - 关联：market · CDP、CDP 架构图索引、架构图、CDP 方案澄清文档

### 采集管线 Kafka+异常Staging
  - *类型:design-rule*
  - Kafka 削峰必填；staging 仅 UNMATCHED/FAILED/CONFLICT；热路径不写 staging；批量 200–1000；DAU 30万 / 6k EPS。
  - 关联：market · CDP


## 项目：mds

### 公共能力准入与退出
  - *类型:spec*
  - name: public-capability-admission
  - 关联：项目索引 · mds

### 公司知识库 ↔ 本机大脑桥接
  - *类型:spec*
  - name: company-knowledge-brain-bridge
  - 关联：项目索引 · mds

### 准入 V2 正式决策切换（Skill）
  - *类型:spec*
  - name: admission-v2-decision-switch
  - 关联：项目索引 · mds

### 市场回传渠道 Handler 开发规范
  - *类型:spec*
  - name: market-return-channel-handler
  - 关联：项目索引 · mds

### 探索成功 → 打包为 Skill
  - *类型:spec*
  - name: package-exploration-as-skill
  - 关联：项目索引 · mds


## 项目：platform

### AI原生阶段V沉淀复用
  - *类型:design-rule*
  - 人维护与准入决策；AI复用与探索未知。含17固化/18分流/19准入退出/20建设可接手。经验进入下一轮。
  - 关联：公共能力准入条件、平台工具

### 产品/报表/SOP
  - *类型:design-rule*
  - 关联：按角色/端浏览

### 公共能力准入条件
  - *域:capability · 类型:design-rule*
  - 探索成功≠公共能力。公共须 A–H 全过+人审；三级：探索→候选→公共→退役。路径：~/.local/share/brain/notes/2026-09-11-public-capability-admission.md 与 mds/docs/skills/public-capability-admission/
  - 关联：AI原生阶段V沉淀复用、探索成功vs公共能力、本地大脑索引、探索打包为Skill、MDS 历史 Skills、MDS系统讲解稿、Skill三门禁、V2 Hub TDS入参透传

### 前端 Web/H5/小程序
  - *类型:design-rule*
  - 关联：按角色/端浏览

### 后端服务
  - *类型:design-rule*
  - 关联：按角色/端浏览

### 平台方法
  - *类型:design-rule*
  - 关联：按角色/端浏览

### 架构图
  - *类型:design-rule*
  - PlantUML/PNG；脑图选中节点可在侧栏预览
  - 关联：开发参考、CDP 架构图索引、CDP 图01 系统总架构、CDP 图02 数据分层模型、CDP 图03 接入认证时序、CDP 图04 清洗落库管线、CDP 图05 用户身份模型、CDP 图06 ID-Mapping决策

### 知识库项目隔离视图
  - *域:knowledge-management · 类型:design-rule*
  - 默认综合总览按公共知识分层；项目筛选只展示该项目和公共桥接节点；支持结论、来源和旧节点ID搜索。
  - 关联：知识分类体系

### 角色位置 Figma 落稿规格
  - *类型:design-rule*
  - 我的页资料头：姓名下方为手机号，第三行用12px盾牌+12px次级文字展示“客户账号/渠道合作方/顾问/部门经理/老板/运营管理员/超级管理员”；距手机号6px，不加底、不描边。
  - 关联：角色身份迁移到我的页、角色身份不使用状态胶囊、平台工具

### 运维/中间件
  - *类型:design-rule*
  - 关联：按角色/端浏览


## 项目：widek

### Widek SVC Dev
  - *类型:spec*
  - name: widek-svc-dev
  - 关联：项目索引 · widek


## 项目：（未归类）

### H5登录主操作冲突
  - *类型:design-rule*
  - H5同时显示微信一键登录主按钮和应使用验证码的提示，且两种微信入口难以理解。
  - 关联：Loan系统反方综合审计

### Loan 业务编码展示隔离
  - *类型:design-rule*
  - Web/小程序/报告仅展示客户、员工、规则、产品等业务名称；业务编码保留为内部路由与关联键。
  - 关联：业务ID禁止作为页面展示值、Loan 统一回归基线 2026-09-26

### Web 与微信客户打通
  - *类型:design-rule*
  - 手机号 hash 是统一身份键；手机号一键登录优先复用已有 t_client_profile.client_code，冲突账号拒绝自动合并。
  - 关联：微信手机号一键登录

### Web 子菜单以 route.name 划分页面实例
  - *类型:design-rule*
  - 共用 Vue 组件的多个子菜单必须使用 route.name 作为 RouterView key；查询参数只由页面 watcher 刷新数据，避免菜单已切换但内容停留旧页面。
  - 关联：审批子菜单文案由当前审批类型驱动、暗色表格固定列禁止背景过渡

### Web 菜单产品视角重构
  - *类型:design-rule*
  - 按客户中心、智能匹配、服务交付、审批中心、营销激励、经营分析、产品与规则、系统管理重组；客户检索/服务台/审批中心/匹配任务/诊断报告等命名统一。
  - 关联：服务台客户邀约入口、菜单父级自动补齐、菜单父模块按有权叶子裁剪

### Web 顶部导航密度治理
  - *类型:design-rule*
  - 顶部应分离全局导航、历史页签和页面标题；限制可见页签数量并提供横向滚动/全部页签，工作台欢迎区压缩为轻量上下文条。
  - 关联：Web布局遵循现有设计系统

### Web侧栏单业务域展开
  - *类型:design-rule*
  - 管理端侧栏采用手风琴结构；路由变化时自动展开当前业务域并收起其他分组，子模块以树线和小标题区分层级。
  - 关联：Web布局遵循现有设计系统、工作台今日服务五维统计

### Web布局遵循现有设计系统
  - *类型:design-rule*
  - 无具体 Figma 节点时不臆造设计稿，复用项目 CSS tokens、AppIcon 和既有组件完成响应式布局。
  - 关联：Web侧栏单业务域展开、Web 顶部导航密度治理

### Web独立子菜单页
  - *类型:design-rule*
  - 列表型页内Tab拆为独立URL与菜单叶子，组件可复用但范围由route meta固定。
  - 关联：客户范围独立路由、服务台独立路由、经营概览不重复趋势、子菜单授权兼容

### Web登录模式文案隔离
  - *类型:design-rule*
  - 密码登录提示请输入账号，短信验证码登录提示请输入手机号；避免账号与手机号语义混用。
  - 关联：找回密码弹窗交互、员工密码列迁移必需

### Web菜单二级三级层级区分
  - *类型:design-rule*
  - 二级菜单使用小标题、分隔线和主色标识，三级页面使用更紧凑缩进，不再将二级标题渲染成与页面项相似的卡片。
  - 关联：网关链路响应头只写一次

### 主题色板与弹层边界
  - *类型:design-rule*
  - 主题色板扩展为12色，采用固定6列网格与最大视口宽度，避免最后色块超出弹层。
  - 关联：折叠屏式页面切换动效、日志前导空格来源

### 审批待办与已审批分离
  - *类型:design-rule*
  - 老板进入外出审批页时只显示权限范围内待审批申请，已审批的外出记录在服务台查看，不重复出现在审批待办。
  - 关联：老板全司外出可见、外出审批通过待本人打卡

### 审批按当前子页创建
  - *类型:design-rule*
  - 附件下载审批页直接打开下载申请，外出审批页直接进入外出申请；我的申请保留类型选择器。
  - 关联：Web复用页面路由同步

### 审批申请类型卡片式选择
  - *类型:design-rule*
  - 审批中心新增申请使用系统卡片、边框、主色和焦点态，不使用原生按钮堆叠。

### 客户回放枚举统一中文展示
  - *类型:design-rule*
  - 客户回放页的来源、客群、布尔状态、生成方式、快照状态、事件类型、操作者和可见范围必须通过中文映射显示；未知枚举使用中文中性兜底，不直接输出内部英文值。
  - 关联：业务ID禁止作为页面展示值、画像结构化提示归一为业务文案

### 我的申请与审批权分离
  - *类型:design-rule*
  - 内部员工可查看本人全部申请；拥有“我的申请”菜单不代表拥有审批操作权限。
  - 关联：审批按数据可见范围

### 手机号查看审批中心入口
  - *类型:design-rule*
  - Web 审批中心新增手机号查看审批路由、待审列表、通过与驳回操作。
  - 关联：手机号查看额度外审批

### 找回密码弹窗交互
  - *类型:design-rule*
  - 弹窗打开刷新图形验证码，仅合法手机号回填，清空旧密码和短信码，补齐字段占位符和操作说明。
  - 关联：Web登录模式文案隔离、四位字母数字图形验证码、登录页Figma MCP未连接

### 折叠屏式页面切换动效
  - *类型:design-rule*
  - 页面路由使用低幅度3D折页、毛玻璃模糊和透明过渡，支持prefers-reduced-motion减少动效。
  - 关联：主题色板与弹层边界

### 服务台按角色显示数据口径
  - *类型:design-rule*
  - 顾问显示我的来访/我的外出/我的待回访/我的活跃工单；部门经理显示团队；老板和运营超管显示全公司。工作台指标和服务台列表使用相同角色前缀。
  - 关联：老板全司外出可见、角色数据范围切换由后端校验

### 服务台明细日期与聚焦
  - *类型:design-rule*
  - 服务台读取路由 date/focus 查询参数；预约和外出展示客户、员工、时间、地点、状态，今日服务台对待回访和活跃工单区块提供聚焦高亮。
  - 关联：工作台统计点击直达明细

### 服务方式无歧义文案
  - *类型:design-rule*
  - COMPANY_ON_SITE显示客户到访我司；HOME_VISIT显示员工上门拜访客户，客户端显示顾问上门拜访您。
  - 关联：服务台独立路由

### 服务日期时间公共组件
  - *类型:design-rule*
  - 预约、改期、普通外出统一使用 ServiceDateTimeRange，统一格式、默认时段和视口内下拉。

### 服务运营业务编码关联
  - *类型:design-rule*
  - 预约、外出、跟进、活动、画像、工单、提交单等核心服务表使用client_code/staff_code/order_no等唯一业务编码，已核验抽查孤儿关联为0。
  - 关联：数据库无物理外键

### 父菜单由叶子授权派生
  - *类型:design-rule*
  - 角色没有任何有效叶子菜单时不展示父模块；父授权仅从有效叶子菜单补齐。
  - 关联：菜单迁移幂等性、管理角色客户双入口、Web角色菜单审计

### 经营概览不重复趋势
  - *类型:design-rule*
  - 经营概览只保留核心指标与运营分析；成交和奖励趋势仅在趋势分析独立页展示。
  - 关联：Web独立子菜单页

### 菜单父模块按有权叶子裁剪
  - *类型:design-rule*
  - Web 父模块仅在至少一个可访问叶子页面存在时显示；非路由分区标题不得使空父模块残留。
  - 关联：Web 菜单产品视角重构

### 顶部路由页签
  - *类型:design-rule*
  - 路由页签已从内容区移动到顶部导航中间空白区域，保留切换、关闭、刷新和横向滚动。
  - 关联：工作台角色化指标

