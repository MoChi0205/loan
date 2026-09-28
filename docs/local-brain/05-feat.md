# 功能 Feature

> 由本地大脑自动蒸馏，生成时间 2026-09-28T14:16:28。

条目数：**158**


## 项目：abs

### MD5取模+weight定组
  - *类型:feature*
  - 目标态定组：seed=exp|version|entityKey，SLOT_BASE=10000；不用Redis Lua定桶；Redis仅计数/监控/上游缓冲
  - 关联：分流决策链C0～C7、abs · AB实验

### TabBar 当前图标几何
  - *类型:feature*
  - TabBar 使用 AppIcon size=lg（64rpx容器），tab-item 上下内边距16rpx/14rpx，选中顶部指示条48×6rpx；与原型22px图标、66px栏高存在几何对齐风险。
  - 关联：TabBar 深色主题作用域：问题与修复、TabBar 原型规范与落地、abs · AB实验

### 上游分配Redis缓冲
  - *类型:feature*
  - UpstreamAssignmentRedisCache：W1先Redis再DB失败删key；C2/批预取先Redis miss再DB；TTL=60s常量；无开关；升版不evict
  - 关联：ABS文档索引入口、子实验C2准入、批量分流预取管道、上游缓冲无Nacos配置、子实验零准入排查、abs · AB实验

### 准入与输出标签
  - *类型:feature*
  - ab_experiment_audience_tag：SINGLE_GROUP/FULL；准入引用≠本实验；输出source=本实验；tag编码SINGLE={exp}_{group}，FULL≈AT_{exp}
  - 关联：子实验C2准入、abs · AB实验

### 分流决策链C0～C7
  - *类型:feature*
  - C0非运行默认组→C1白名单→C2准入→C3已分配→C4升版懒重分→C5分层→C6长期对照slot→C7 MD5定组；顺序不可颠倒
  - 关联：ABS AB实验平台、批量分流预取管道、ABS文档索引入口、MD5取模+weight定组、TargetShuntReasonCode、子实验C2准入、版本与升版双开关、abs · AB实验

### 四角色审批中心回归
  - *域:client-lifecycle · 类型:feature*
  - 静态断言确认部门经理、老板、运营管理员、超级管理员拥有 mini:approval:view，TabBar 动态插入审批中心且路由存在；H5 构建通过
  - 关联：abs · AB实验

### 子实验C2准入
  - *类型:feature*
  - role=2须parent+tag_code；C2查父分配EXISTS+版本；SINGLE还须分组一致；白名单C1优先于C2；父仅白名单未落库→子C2仍失败
  - 关联：分流决策链C0～C7、准入与输出标签、上游分配Redis缓冲、子实验零准入排查、abs · AB实验

### 审批中心Tab权限真值
  - *域:client-lifecycle · 类型:feature*
  - TabBar 根据 mini:approval:view 动态补齐审批中心，避免角色配置与后端动态权限不一致
  - 关联：abs · AB实验

### 批量分流预取管道
  - *类型:feature*
  - BatchIShuntServiceImpl：本实验+上游批查，evaluatePhaseC；仅ASSIGNED_NEW落库；准入拒绝status=0不写表；与单用户同决策
  - 关联：分流决策链C0～C7、上游分配Redis缓冲、abs · AB实验

### 报告原型双标签交互
  - *域:operations-report · 类型:feature*
  - 原型支持在报告信息与经营诊断之间切换；经营诊断展示用户提交数据、资料状态及非产品化风险提示。
  - 关联：客户报告查询仅含风险分析数据、abs · AB实验

### 版本与升版双开关
  - *类型:feature*
  - experiment_version字符串；长期对照分配有效版本=基线|yyyy-MM或|yyyy；升版仅允许(刷表true,懒重分true)或(false,true)
  - 关联：分流决策链C0～C7、abs · AB实验


## 项目：compliance

### 贷款网络营销合规路径
  - *域:compliance · 类型:feature*
  - 需由金融机构书面委托、使用经审核内容、披露委托机构、转接至金融机构自营平台；贷款信息以金融机构自身名义发布。
  - 关联：第三方平台不等于金融持牌机构、第三方平台不得介入销售环节、金融机构直接委托证据缺口、贷款提供方披露不足、合规专项

### 金融营销合规
  - *域:compliance · 类型:feature*
  - 关联：按业务域浏览


## 项目：loan-main

### P0 Web报告详情范围校验
  - *类型:feature*
  - Web初筛报告详情将当前LoanUser传入ReportService；顾问仅本人客户、部门经理本部门、老板/运营/超管全量，非STAFF与越权客户返回拒绝。
  - 关联：小程序与H5按角色隔离展示、loan-main · 企融通后端、P1员工报告按reportNo聚合

### P0 报表范围下沉
  - *类型:feature*
  - 报告分页筛选改用数据库 EXISTS 归属过滤，避免 10 万客户时加载全部编码和超大 IN。
  - 关联：loan-main · 企融通后端

### P0客户员工报告DTO分离
  - *类型:feature*
  - 客户详情使用CustomerReportDetail，类型中不定义银行、产品、准入、匹配统计和规则日志；员工详情使用StaffReportDetail承载内部字段，服务方法也拆为customerReportDetail/staffReportDetail。
  - 关联：客户响应使用独立DTO、P0报告隔离回归通过、loan-main · 企融通后端

### P1员工报告按reportNo聚合
  - *域:operations-report · 类型:feature*
  - 新增StaffReportAggregationService与GET /api/admin/report/screening/{reportNo}/aggregate，先复用P0员工身份及归属校验，再聚合报告摘要、客户画像、材料状态、经营分析和既有匹配产品。
  - 关联：loan-main · 企融通后端、P0 Web报告详情范围校验、P1内部报告聚合DTO、P1复用既有匹配结果、P1完整回归373项通过、Web员工详情接入聚合接口、报告模板尚未实际渲染

### SLA与团队绩效指标
  - *域:operations-report · 类型:feature*
  - 建议统计首次跟进时长、跟进间隔、超期预警、顾问客户转化率、工单成交率和回收数量。
  - 关联：报表按归属范围统计、loan-main · 企融通后端

### TraceId 全链路贯穿
  - *类型:feature*
  - 网关生成或透传 X-Trace-Id，下游 TraceIdFilter 写入日志上下文并回写响应头。
  - 关联：网关链路访问日志、loan-main · 企融通后端

### V2 Hub TDS入参透传
  - *类型:feature*
  - 业务字段按Hub准入传入透传；policyCode/H00006由V2 ChannelAgentQueryPolicyResolver控制；hubInboundRequest快照
  - 关联：渠道准入 V2 引擎、公共能力准入条件、loan-main · 企融通后端

### Web员工详情接入聚合接口
  - *域:operations-report · 类型:feature*
  - ReportCenter和ScreeningReport员工视角详情按钮调用screeningAggregate；ScreeningReport对渠道账号保留原screeningDetail接口。
  - 关联：P1员工报告按reportNo聚合、Web内部聚合报告五模块视图、Web聚合报告构建通过

### Web菜单业务责任边界
  - *域:operations-report · 类型:feature*
  - Web菜单按客户经营、匹配规则、服务审核、运营激励、数据报表、产品渠道、系统管理分域；顾问只能申请审批，渠道只能本人录入与查看，系统配置与业务审批分离。
  - 关联：Web菜单四层权限契约、loan-main · 企融通后端

### loan现有营销合规控制
  - *域:compliance · 类型:feature*
  - 已有非审批承诺提示、客户侧隐藏产品名利率、报告/SMS模板内部审批、材料复核和审计留痕；可作为整改基础，但不能替代金融机构委托、审核与披露。
  - 关联：金融营销数据授权缺口、独立风险分析可展示数据、loan-main · 企融通后端

### 一键双主题切换
  - *类型:feature*
  - web 用 data-theme + ThemeSwitch；mini 用单例 useThemeMode()
  - 关联：墨金 Ink&Gold 设计系统、小程序端 loan-mini、TabBar 深色主题作用域：问题与修复、Web 管理端 loan-web、loan-main · 企融通后端

### 上海属地银行策略
  - *类型:feature*
  - 上海银行电子保函0.1%/季度、当日出函、无需授信开户；上海农商鑫易保函最快30分钟、敞口最高100%、已服务近3000家小微
  - 关联：银行保函产品库(18家28产品)、上海旭荣网络科技企业画像、loan-main · 企融通后端

### 全角色双模式登录
  - *域:authentication · 类型:feature*
  - 员工、渠道、客户均支持短信验证码与密码登录，账号类型显式隔离。
  - 关联：RSA传输与BCrypt存储、一次性算术验证码、短信验证码场景隔离

### 公司产品审批发布
  - *域:client-lifecycle · 类型:feature*
  - 所有员工可提交产品；老板/超管审批通过后进入全量库。
  - 关联：合作库角色边界、loan-main · 企融通后端

### 公司公海全员可见与认领
  - *域:client-lifecycle · 类型:feature*
  - 所有公司员工拥有 client:unassignedPage；业务员工角色拥有 client:claim，顾问可从公司公海认领。
  - 关联：客户释放按钮归属守卫、未分配客户直接认领、产品库角色边界、loan-main · 企融通后端

### 公海效率指标
  - *域:operations-report · 类型:feature*
  - 建议新增公海进入、认领、回收、停留时长、认领率、冷却期客户等指标。
  - 关联：报表按归属范围统计、loan-main · 企融通后端

### 单例主题 useThemeMode
  - *类型:feature*
  - theme.js 模块级 ref 共享，13 页面注入 data-theme
  - 关联：小程序端 loan-mini、loan-main · 企融通后端

### 可切换 OSS Provider
  - *类型:feature*
  - 通过 loan.oss.mode 在 local、aliyun、tencent 间切换，业务层只依赖 OssStorageService。
  - 关联：loan-main · 企融通后端

### 合规整改(资金/审核)
  - *域:compliance · 类型:feature*
  - 映射：贷款/融资→资金、审批→审核；三端(web/mini 源码+构建产物)零命中；后端 URL /api/admin/approval 保留
  - 关联：合规四件套(前置签署)、小程序端 loan-mini、企融通贷款系统重构、Web 管理端 loan-web、loan-main · 企融通后端

### 员工代客预约创建即确认 · loan-main
  - *类型:feature*
  - 员工代客创建预约后直接进入已确认状态，客户无需确认、员工无需二次确认；客户主动预约仍等待顾问接单。
  - 关联：普通外出审批与双凭证打卡

### 员工报告客户身份信息
  - *类型:feature*
  - 员工聚合报告展示客户业务ID、企业或个人名称、联系人、脱敏手机号及脱敏唯一身份标识。
  - 关联：审计客户身份关联查询、经营分析范围隔离缓存

### 员工新增线索自动归属
  - *域:client-lifecycle · 类型:feature*
  - 公司员工创建线索时后端按角色确定来源，并将 ownerStaffCode 设置为创建员工；前端不可伪造来源。
  - 关联：企融通贷款系统重构、线索主动释放公海、loan-main · 企融通后端

### 员工经营咨询与渠道报告模板
  - *域:loan-consult · 类型:feature*
  - 后续内部报告模板包含客户经营摘要、画像核验、五维咨询、合作渠道、可能适配进件产品、员工话术、行动计划和权限红线；模板仅限公司员工查看。
  - 关联：旭荣报告模板实例关联、报告双视角展示、loan-main · 企融通后端

### 团队公海菜单已新增
  - *域:operations-report · 类型:feature*
  - Web客户经营菜单新增团队公海直达入口，后端按TEAM_SEA与部门编码隔离。
  - 关联：客户范围语义拆分、loan-main · 企融通后端

### 复核事实进入内部匹配链路
  - *域:material-ocr · 类型:feature*
  - VLM有facts时先进入PENDING_REVIEW；人工通过后仅补空回灌提交单；ScreeningService阻止待复核客户执行，并合并已复核事实后进入规则引擎和匹配审计。当前Mock配置下实际主要使用手填facts。
  - 关联：当前OCR为Mock未调用AI、材料分类契约不一致

### 外出出发返回双打卡
  - *域:service-operations · 类型:workflow-rule*
  - 上门拜访必须完成出发和返回两次员工主动打卡；出发进入进行中，返回后完成，失败不得伪造成功并应写操作日志。
  - 关联：上门拜访无需审批、打卡单点定位边界、预约到店与员工外出链路

### 外出打卡与预约履约联动
  - *域:service-operations · 类型:workflow*
  - 上门出发打卡原子进入IN_PROGRESS并驱动预约CONFIRMED→SERVING；返回打卡进入COMPLETED并驱动预约完成，定位请求立即AES加密且不返回列表。
  - 关联：外出打卡加密单点定位

### 审计客户身份关联查询
  - *类型:feature*
  - 审计列表支持客户业务ID、姓名、企业名模糊查询，手机号、信用代码、身份证号通过SHA-256摘要精确查询；响应只返回脱敏信息。
  - 关联：审计规则中文名称、员工报告客户身份信息

### 客户三段式范围
  - *域:client-lifecycle · 类型:feature*
  - 客户档案页统一为“我的客户｜全司已分配客户｜公司公海”；后端按归属人与未分配条件隔离数据。
  - 关联：企融通贷款系统重构、Web 管理端 loan-web、loan-main · 企融通后端

### 客户公海直接认领
  - *域:client-lifecycle · 类型:feature*
  - 未分配客户允许所有公司员工直接认领；已有归属才发起转移审批。
  - 关联：用户查询菜单、loan-main · 企融通后端

### 客户综合关键词搜索
  - *域:client-lifecycle · 类型:feature*
  - 客户档案分页查询 keyword 支持身份证、统一社会信用代码、企业名称、联系人和手机号；手机号、信用代码、身份证使用摘要精确匹配，身份证通过客户业务编码关联个人档案。
  - 关联：loan-main · 企融通后端

### 客户跟进刷新回收基准
  - *域:customer-followup · 类型:workflow*
  - 仅当前归属顾问可新增客户级跟进；写入后同步刷新t_client_profile.last_followed_at，并按CUSTOMER/STAFF_ONLY裁剪活动回放。
  - 关联：服务运营P1真实记录链路

### 客户运营统计接口
  - *域:operations-report · 类型:feature*
  - 新增 /api/admin/report/operations，统一输出客户资产、公海效率、分配回收、跟进 SLA 与近周期转化流量。
  - 关联：首次跟进自然小时、报表快照与流量分离、经营指标采集缺口、公海停留周期口径、TSE经营报表经验、loan-main · 企融通后端

### 异常 TraceId 关联
  - *类型:feature*
  - 异常响应回填当前 MDC traceId，客户端可用 traceUuid 直接关联 access/error 日志。
  - 关联：全局异常响应契约、loan-main · 企融通后端

### 我的审批全员入口
  - *域:client-lifecycle · 类型:feature*
  - 全部公司员工可查看本人产品、下载、客户认领/转移申请；审核动作仍按角色授权。
  - 关联：跨团队客户转移两级审批、工作台角色化待办、loan-main · 企融通后端

### 我的客户跟进入口
  - *域:client-lifecycle · 类型:feature*
  - 我的客户按 user.userNo 判断归属，所有内部归属员工显示跟进按钮；列表显示最近跟进或尚未跟进。
  - 关联：loan-main · 企融通后端

### 报告日期模糊筛选
  - *类型:feature*
  - 内部报告支持 yyyy-MM-dd、yyyy/MM/dd、yyyyMMdd、中文年月日及年月区间；渠道报告 SQL 同步支持日期字符串搜索，并保留名称与报告编号查询。
  - 关联：报告业务显示名、loan-main · 企融通后端

### 报表按归属范围统计
  - *域:operations-report · 类型:feature*
  - 建议报表统一按我的/团队/全司/公司公海/团队公海切换，线索同时区分创建人和当前归属人。
  - 关联：经营统计角色范围、公海效率指标、SLA与团队绩效指标、TSE经营报表经验、loan-main · 企融通后端

### 接口查询耗时
  - *类型:feature*
  - AccessLogFilter 输出 X-Query-Time-Ms、Server-Timing，并在访问日志记录 cost；当前是完整 HTTP 请求耗时。
  - 关联：OSS 对象存储抽象、loan-main · 企融通后端

### 政策性担保增信通道
  - *类型:feature*
  - 嘉定科创贷批次担保 + 上海市专精特新专项担保计划(单户上限3000万)、信用贷最高30%贴息；用于弥补实缴偏低
  - 关联：贷款咨询专家团六阶段SOP、上海旭荣网络科技企业画像、loan-main · 企融通后端

### 无客户信息报告编号
  - *类型:feature*
  - 新初筛报告编号采用 report_yyyyMMddHHmmssSSS_三位序号，不包含企业名或客户姓名；中文企业/姓名只用于报告显示名。
  - 关联：报告业务显示名、loan-main · 企融通后端

### 旧经营总览范围修复
  - *域:operations-report · 类型:feature*
  - 旧总览漏斗、客群/产品/工单分布与奖励统计已按本人/团队/全司归属范围过滤；空用户和未知角色默认拒绝。
  - 关联：经营统计角色范围、loan-main · 企融通后端

### 普通外出审批与双凭证打卡
  - *类型:feature*
  - 员工普通外出允许不关联客户和预约，但须填写计划时间、本人提交、主管审批；出发和返回均强制照片与单点定位，定位只保存密文。
  - 关联：员工外出审核迁移状态、员工代客预约创建即确认 · loan-main

### 智能材料准备清单
  - *域:client-lifecycle · 类型:feature*
  - 按企业贷/个人贷场景展示必传、建议材料、用途、有效期与必传完成度，不再依赖通用资料类型下拉。
  - 关联：企业贷基础必传材料、个人贷基础必传材料、材料流程引导卡、loan-main · 企融通后端

### 服务运营P1真实记录链路
  - *域:service-operations · 类型:implementation*
  - 已实现预约、现场到店与远程履约、上门外出双打卡、客户级跟进、统一活动回放及今日服务台的Entity/Mapper/Service/Controller链路。
  - 关联：服务运营P0四表模型、服务状态原子流转、客户跟进刷新回收基准、服务运营P1验证基线、服务运营P2 Web统一工作区

### 未分配客户直接认领
  - *域:client-lifecycle · 类型:feature*
  - 客户公海仅含未分配客户，员工认领即时归属；仅查重命中已归属他人时进入转移审批。
  - 关联：公司公海全员可见与认领、loan-main · 企融通后端

### 本人客户认领闭环
  - *类型:feature*
  - 用户查询返回 ownedByMe；已归属本人时隐藏申请按钮并显示当前已是我的客户，后端继续幂等。
  - 关联：用户查询菜单、loan-main · 企融通后端

### 消息未读单一口径
  - *类型:feature*
  - 首页铃铛、消息中心和我的页统一调用 unread-count；消息列表与未读数按同一 userNo 查询，读完清理缓存。
  - 关联：认定原型基线、loan-main · 企融通后端

### 电子保函趋势(免授信/免开户)
  - *类型:feature*
  - 多行已上线分钟级出函：平安纯信用零保证金额度最高1000万，5分钟出函；兴业半日；工行/招行/上海银行当日
  - 关联：银行保函产品库(18家28产品)、loan-main · 企融通后端

### 短信预实现
  - *类型:feature*
  - 短信验证码当前沿用 tse 的策略化预实现：Redis 频控、模板、发送记录，通道暂为 MOCK。
  - 关联：loan-main · 企融通后端

### 精准初筛事实优先级
  - *类型:feature*
  - 初筛优先读取客户最近已复核材料事实，页面手填值只补充空字段，不覆盖已核验数据。
  - 关联：材料复核后才能精准初筛、loan-main · 企融通后端

### 线索主动释放公海
  - *域:client-lifecycle · 类型:feature*
  - 归属人可释放自己的线索到公司公海；释放写入冷却截止时间并记录流转审计。
  - 关联：员工新增线索自动归属、loan-main · 企融通后端

### 线索自动归属与释放流程
  - *域:client-lifecycle · 类型:feature*
  - 员工新增线索由 LeadService 直接设置 ownerStaffCode=当前员工；主动 release 后清空归属并进入公司公海，带 7 天本人冷却。
  - 关联：线索创建人与归属人分离展示、loan-main · 企融通后端

### 线索菜单更名线索管理
  - *域:client-lifecycle · 类型:feature*
  - 线索公海改为线索管理，页面内部继续区分我的线索与线索公海。
  - 关联：客户范围语义拆分、loan-main · 企融通后端

### 诊断行动闭环
  - *域:loan-consult · 类型:feature*
  - 建议落为30/60/90天任务，包含责任人、状态和复核目标，使报告从展示转为经营改善闭环。
  - 关联：单企业经营诊断报告原型V1、loan-main · 企融通后端

### 请求响应审计日志
  - *类型:feature*
  - 服务端访问日志记录 traceId、请求 Query/Body 摘要、响应摘要、业务码、状态与耗时。
  - 关联：参数日志安全控制、数据变化排查链路、loan-main · 企融通后端

### 贷款咨询/旭荣
  - *域:loan-consult · 类型:feature*
  - 关联：按业务域浏览

### 跨团队客户转移两级审批
  - *域:client-lifecycle · 类型:feature*
  - 跨团队转移先由申请人团队负责人初审，再由老板或超级管理员终审。
  - 关联：用户查询菜单、客户两级审批生产迁移、我的审批全员入口、loan-main · 企融通后端

### 阿里云 OSS 配置开关
  - *类型:feature*
  - 通过 loan.oss.mode=aliyun 与 loan.oss.aliyun.* 配置启用，默认 local，避免 Bean 冲突。
  - 关联：OSS 对象存储抽象、loan-main · 企融通后端

### 陌生客户先进入线索流程
  - *域:client-lifecycle · 类型:feature*
  - 客户不存在时先新增线索并完成客户转化；未分配或他人客户先完成认领审批。
  - 关联：工单仅可为本人客户创建、loan-main · 企融通后端

### 预约到店与员工外出链路
  - *域:service-operations · 类型:feature-plan*
  - 预约到店使用统一状态机，员工外出独立建模并可配置审批、出发、返回和服务关联，避免把外出作为预约附属字段。
  - 关联：客户活动时间线回放、每日服务名单聚合、客户服务方式四分类、外出出发返回双打卡、预约双发起入口

### 首次跟进自然小时
  - *类型:feature*
  - 从归属生效到该归属周期首次真实跟进，按自然小时计算；未首跟不进入平均值。
  - 关联：客户运营统计接口、经营指标采集缺口、客户生命周期事件、loan-main · 企融通后端


## 项目：loan-mini

### H5登录方式切换规范
  - *域:authentication · 类型:decision*
  - H5微信登录与手机号验证码登录采用互斥状态，通过文字入口往返切换，不同时堆叠两套表单。
  - 关联：登录设计真实代码基线、loan-mini · 小程序/H5

### 匹配诊断结果统一入口文案
  - *域:loan-consult · 类型:feature*
  - 报告卡入口统一表达为同时查看匹配与诊断结果，并按角色调整范围和筛选说明。
  - 关联：首页文案调整构建验证通过、首页角色提示文案优化、loan-mini · 小程序/H5

### 小程序「我的客户」+公海认领
  - *类型:feature*
  - D74：所有公司员工可查本人归属客户并认领公海（公司公海全员/团队公海仅本部门）；渠道只读本人录入、无认领（D50）。后端 GET /api/mini/client/my|sea，api_key=mini:myClients|mini:seaClients
  - 关联：客户档案三类归属视图、TabBar 合并页（线索录入/我的客户）、客户状态枚举漂移（已修）、企融通贷款系统重构、loan-mini · 小程序/H5、公司员工统一我的客户入口

### 小程序与H5按角色隔离展示
  - *域:operations-report · 类型:feature*
  - 客户界面改为资质与经营风险分析，不显示可进件、产品数、银行覆盖或评级；员工界面继续展示内部匹配能力。
  - 关联：客户报告查询仅含风险分析数据、角色隔离构建验证、客户报告真实接口隔离验证、客户风险分析报告页面原型、报告双视角展示、P0 Web报告详情范围校验、loan-mini · 小程序/H5、手机号额度按每日首次客户解锁统计

### 小程序仅支持验证码备用登录
  - *域:authentication · 类型:feature*
  - 手机号登录页只保留手机号、短信验证码和一次性随机验证码校验，不展示密码登录或密码找回。
  - 关联：小程序微信一键登录主入口、小程序不提供密码找回

### 小程序微信一键登录主入口
  - *域:authentication · 类型:ux-rule*
  - 小程序落地页将微信一键登录作为主CTA，手机号验证码仅作为备用入口。
  - 关联：小程序仅支持验证码备用登录

### 小程序我的线索按归属筛选
  - *域:client-lifecycle · 类型:feature*
  - 员工端按 ownerStaffCode 查询，释放后移出我的线索；渠道端按本人录入隔离。
  - 关联：小程序端 loan-mini、loan-mini · 小程序/H5

### 小程序风险分析步骤导航
  - *类型:feature*
  - 上一步/下一步改为内容区紧凑操作条，下一步占更大比例，避免与底部 TabBar 之间出现大块空白。
  - 关联：Web 下拉浮层视口边界、小程序首页紧凑数据排版

### 小程序首页权限菜单
  - *类型:feature*
  - 首页快捷入口按 mini:report/order/product/client 权限精确显示，渠道隐藏智能匹配
  - 关联：loan-mini · 小程序/H5

### 小程序首页重做
  - *类型:feature*
  - home.vue 按墨金效果图整页重写：问候头+深色AI卡+四宫格+报告列表；TabBar文案回正+配色随主题
  - 关联：墨金 Ink&Gold 设计系统、小程序端 loan-mini、loan-mini · 小程序/H5

### 微信一键登录主入口
  - *类型:feature*
  - 小程序默认只展示微信一键登录；H5默认也保持微信一键登录，与小程序视觉和交互一致。
  - 关联：登录协议强制同意、登录设计真实代码基线、loan-mini · 小程序/H5

### 我的页新增身份行
  - *类型:feature*
  - 我的页资料头在手机号下方以12px盾牌与次级文字展示账户角色；无胶囊背景、描边或交互。
  - 关联：AppIcon 新增 xs 规格、角色迁移构建验证通过、角色身份迁移到我的页、loan-mini · 小程序/H5

### 报告固定数据来源说明
  - *域:operations-report · 类型:feature*
  - 小程序与H5共用报告详情页固定展示：本报告仅使用客户主动填写及授权上传材料；未调用外部个人信息查询接口。模板生成前校验要求文案与真实调用状态一致。
  - 关联：当前未接入免费用户信息查询、客户DTO固定数据来源字段、loan-mini · 小程序/H5

### 渠道无公海与消息一致性
  - *域:client-lifecycle · 类型:feature*
  - 渠道合作方只能查看本人录入形成的客户，移动端和原型均不展示公海筛选；首页铃铛红点、消息中心和我的页未读数必须使用同一通知数据源。
  - 关联：loan-mini · 小程序/H5

### 登录协议默认选中
  - *类型:feature*
  - 用户协议与隐私政策默认选中；取消后所有登录入口禁用并二次拦截
  - 关联：loan-mini · 小程序/H5

### 登录隐私同意
  - *类型:feature*
  - 微信登录、验证码发送及验证码登录前均须显式勾选用户协议与隐私政策
  - 关联：loan-mini · 小程序/H5

### 线索创建人与归属人分离展示
  - *域:client-lifecycle · 类型:contract*
  - Web线索管理、小程序线索列表、首页概览和审核列表统一展示 createdBy；创建人与当前归属人是两个字段，服务端必须同时返回。
  - 关联：小程序端 loan-mini、Web 管理端 loan-web、线索自动归属与释放流程、loan-mini · 小程序/H5

### 附件上传业务接入 OSS
  - *类型:feature*
  - 小程序附件上传与预览已通过 OssStorageService 接入，本地/阿里云由 loan.oss.mode 切换。
  - 关联：loan-mini · 小程序/H5

### 首页头像授权提示已移除
  - *类型:feature*
  - 首页 Hero 保留微信头像选择能力，但不再展示“授权使用微信头像”文字，避免头像区域出现突兀提示。
  - 关联：首页角色提示文案优化、loan-mini · 小程序/H5

### 首页移除角色胶囊
  - *类型:design-decision*
  - 角色身份是稳定账户属性，不应作为高强调状态胶囊占用首页 Hero。最终方案为首页仅保留头像、问候、姓名、业务说明和消息入口，身份移至“我的”资料区弱展示。
  - 关联：首页文案调整构建验证通过、角色身份迁移到我的页、loan-mini · 小程序/H5

### 验证码登录
  - *类型:feature*
  - Web、H5、小程序统一手机号验证码登录入口
  - 关联：loan-mini · 小程序/H5


## 项目：market-cdp

### 设备/OAID 触点规则
  - *类型:feature*
  - D1 App $device_id；D2 H5 uuid→$device_id 镜像；O1/O2 OAID 用 $identity_oaid_lower_md5；cust_no 非 identity。
  - 关联：market · CDP


## 项目：mds

### V2 双开关
  - *类型:feature*
  - v2_enabled=影子观察(老逻辑对外,异步跑V2只写审计); v2_decision=正式决策(同步V2对外,仅enabled时生效)。关decision即回滚老路径
  - 关联：渠道准入 V2 引擎、mds · 营销中台

### 市场回传切新
  - *类型:feature*
  - marketreturn(旧)→marketad(新)双体系迁移；目标全走marketad停旧Handler；新Handler已覆盖VIVO/OPPO/小米/华为
  - 关联：MDS 营销渠道中台、MDS 历史 Skills、mds · 营销中台

### 渠道准入 V2 引擎
  - *类型:feature*
  - 配置化准入：向导Step0-5→策略绑定→原子规则执行；路由五元组(渠道+策略+实验+分组+人群)定位计划
  - 关联：MDS 营销渠道中台、MDS 历史 Skills、V2 双开关、V2 Hub TDS入参透传、V2对外结果映射现网枚举、mds · 营销中台


## 项目：platform

### Skills渐进披露结构
  - *域:skills · 类型:feature*
  - 元数据name+description常驻→激活读SKILL.md→按需references/scripts/assets。description必须WHAT+WHEN，略偏易触发。
  - 关联：探索打包为Skill、平台工具、公共 Skills

### 公共性能能力
  - *类型:feature*
  - 分页归一化、批量名称装配、二级缓存和 single-flight 回源作为后端公共能力复用。
  - 关联：业务名称批量缓存、loan-main · 企融通后端、公共风险与经验

### 图谱关联拖拽联动
  - *域:knowledge-management · 类型:ui*
  - 图谱拖动中心节点时直接邻居按衰减比例跟随；脑图拖动分支时子树整体跟随；手动位置保存并可重新自动布局。
  - 关联：知识图谱查看器


## 项目：（未归类）

### Loan Web 范围与导航同步
  - *类型:feature*
  - 提交 577060b：工作台与服务运营页面透传日期/数据范围，子菜单路由变化会刷新实际页面，审批筛选、中文画像展示和业务编号隐藏同步完善。
  - 关联：Loan 服务运营角色范围加固

### Loan 客户服务工作区闭环
  - *类型:feature*
  - 员工录入线索同步进入我的客户，服务台按角色范围，预约日期时间公共组件，通知已读按所属用户校验。
  - 关联：Loan 严格接口与菜单矩阵、Loan 统一回归基线 2026-09-26

### Loan 报告双视角当前边界
  - *类型:feature*
  - 客户报告仅展示经营分析、风险和优化建议；内部报告可含内部匹配与合作渠道参考，必须使用独立 DTO 和权限入口。
  - 关联：Loan 当前文档单一真值

### Loan 服务运营角色范围加固
  - *类型:feature*
  - 提交 7519f6e：预约、外出、日工作台支持 SELF/DEPARTMENT/COMPANY 且服务端限制角色上限；我的申请增加类型、状态、阶段、日期和关键词筛选；手机号按当日首次客户解锁计数。
  - 关联：Loan Web 范围与导航同步

### Web 页面异常壳
  - *类型:feature*
  - App.vue 使用 onErrorCaptured 将组件渲染异常转为可操作的刷新/返回工作台页面，避免点击菜单后白屏。
  - 关联：网关权限拒绝延迟执行

### Web复用页面路由同步
  - *类型:feature*
  - 审批、组织、线索、短信、产品复用页面监听route.fullPath，同步activeTab并按子页加载table。
  - 关联：审批按当前子页创建

### Web消息通知铃铛
  - *类型:feature*
  - Web顶部加入未读数轮询、消息列表、单条已读和全部已读，单条已读按当前接收人校验。
  - 关联：敏感查看超限通知

### 业务编码迁移已落库
  - *类型:feature*
  - prd loan_db 已完成合作产品、个人档案、个人认证、客户提交业务编码收尾；t_client_screening 保留当前 match_trace_uuid 结构，t_match_trace 孤立历史记录不伪造关联。
  - 关联：迁移必须读取所选 Nacos 数据源

### 今日服务台两级缓存
  - *类型:feature*
  - 今日来访、员工外出、待回访、活跃工单聚合响应按本人/部门/公司、日期和分页隔离使用 Caffeine+Redis；主动刷新回源并回写。
  - 关联：工作台写后刷新一致性

### 公司员工统一我的客户入口
  - *类型:feature*
  - 我的客户应以 owner_staff_code=当前员工为唯一口径，对顾问、经理、老板、运营、超管均提供；团队/全司视图作为额外入口。
  - 关联：小程序「我的客户」+公海认领、客户筛选维度缺口

### 后端接口验证
  - *类型:feature*
  - 9080 /loan/api/auth/health、9088 /loan/api/auth/captcha、9088 /loan/api/dict/all 均已验证返回 HTTP 200。
  - 关联：网关上游 9080

### 员工代客预约创建即确认 · 
  - *类型:feature*
  - 员工代客创建预约直接写CONFIRMED，客户无需确认，员工无需二次确认；客户主动预约仍等待顾问接单。
  - 关联：预约确认语义冲突

### 员工代客预约直接确认
  - *类型:feature*
  - Web 员工代客创建预约写入 CONFIRMED，客户无需确认、员工无需二次确认；客户主动预约仍走 REQUESTED。
  - 关联：服务台 Web 构建验证

### 员工外出审核链路
  - *类型:feature*
  - 普通外出与上门拜访均为本人提交→主管审核→照片+单点位置双打卡；禁止自审。
  - 关联：普通外出可无客户关联、远端链路验证边界、外出申请未进入我的申请、外出审批缺少独立菜单

### 四位字母数字图形验证码
  - *类型:feature*
  - 图形验证码使用排除易混淆字符的4位大写字母+数字，服务端比较兼容小写输入。
  - 关联：找回密码弹窗交互

### 外出审批与照片硬门槛已确认
  - *类型:feature*
  - 业务方确认外出必须主管审批；出发和返回打卡均必须现场照片与单点位置，现有状态机与后端校验保留。
  - 关联：Loan系统反方综合审计、普通外出可不关联客户

### 外出审批缺少独立菜单
  - *类型:feature*
  - 外出审核能力存在于服务台链路，但审批中心没有外出审批子页面；团队经理因此缺少明确待审入口。
  - 关联：员工外出审核链路

### 审批子菜单文案由当前审批类型驱动
  - *类型:feature*
  - 审批中心复用页面时，标题和说明必须跟随当前子路由，避免附件下载审核等页面仍显示笼统的审批中心。
  - 关联：Web 子菜单以 route.name 划分页面实例、我的申请按角色提供筛选维度

### 客户手机号受控查看
  - *类型:feature*
  - 客户列表与详情支持申请查看原值，受当前用户数据范围校验；原值仅在当前响应和页面临时状态中显示。
  - 关联：敏感手机号查看留痕与日限额、客户手机号继续加密落库、客户敏感查看数据库迁移

### 客户手机号首次解锁额度
  - *类型:feature*
  - 顾问、运营、部门经理每日按不同客户首次解锁计数，默认 30 次；同客户重复查看不重复计数，客户额度不混入旧线索查看日志。
  - 关联：手机号超限分级审批

### 客户范围独立路由
  - *类型:feature*
  - 我的/团队/全司/公司公海/团队公海分别使用独立路由，避免共用/client造成重复高亮。
  - 关联：Web独立子菜单页

### 客户页面联系方式列
  - *类型:feature*
  - 客户档案、线索、工单、预约和报告列表独立展示联系方式列，手机号统一前三位四星号后四位。
  - 关联：手机号列表统一脱敏、工单手机号脱敏出参

### 小程序wx.login登录链路
  - *类型:feature*
  - loan-mini通过uni.login获取真实code，后端POST /api/mini/auth/login调用jscode2session，按openid hash查找或创建客户并签发JWT。
  - 关联：prd微信凭据缺失

### 小程序服务运营接口已提交
  - *类型:feature*
  - 提交包含serviceops包下MiniAppointmentController与MiniServiceTimelineController；这不等同于现有小程序页面及认证上传修改已提交。
  - 关联：21b6e2f范围已核验

### 工作台今日服务五维统计
  - *类型:feature*
  - 工作台展示今日预约、客户到访我司、员工上门外出、今日待回访、活跃工单，并可进入对应独立页面。
  - 关联：工作台复用服务台统计口径、Web侧栏单业务域展开

### 工作台统计点击直达明细
  - *类型:feature*
  - 工作台今日预约、来访、外出、待回访、活跃工单卡片携带日期与聚焦参数，跳转后直接展示对应明细。
  - 关联：服务台明细日期与聚焦

### 工作台角色化指标
  - *类型:feature*
  - 管理角色工作台现在展示范围客户、公海客户、客户转化率、成交金额；顾问和部门主管保留本人/团队口径。
  - 关联：顶部路由页签、我的申请快捷入口

### 工作台跳转保留查询条件
  - *类型:feature*
  - 服务指标跳转统一携带date、focus、serviceMethod；服务台监听路由变化并重新加载。
  - 关联：真实联动受登录会话阻塞

### 微信手机号一键登录
  - *类型:feature*
  - 小程序通过 /api/mini/auth/phone-login 使用 wx.login 与 getPhoneNumber；测试环境只接受显式 mock 凭证。
  - 关联：Web 与微信客户打通、微信测试 Mock 凭证

### 我的申请快捷入口
  - *类型:feature*
  - 审批中心和工作台提供“新增申请”快捷入口，按 DOWNLOAD_APPLY 权限显示。
  - 关联：工作台角色化指标

### 我的申请按角色提供筛选维度
  - *类型:feature*
  - 我的申请始终只查询当前员工本人；公共筛选包含申请类型、状态、提交日期和事项/意见关键词，手机号查看类型额外显示审批层级。类型选项按登录角色可能产生的申请收敛。
  - 关联：审批子菜单文案由当前审批类型驱动

### 手机号查看额度外审批
  - *类型:feature*
  - 额度内直接查看；顾问/运营超限进入部门经理审批；部门经理超限进入老板/超管审批；审批通过后重新点击完成首次解锁。
  - 关联：敏感手机号查看留痕与日限额、手机号查看审批中心入口

### 手机号超限分级审批
  - *类型:feature*
  - 顾问/运营超限进入同部门经理审批；部门经理超限进入老板/超管审批；老板、SUPER_ADMIN、SUPER 直接查看且不占额度。
  - 关联：客户手机号首次解锁额度、手机号超限审批真实验收、敏感查看只约束待审批唯一性、手机号查看接口角色边界

### 打卡定位时间按本地格式提交
  - *类型:feature*
  - 浏览器定位时间不能使用 toISOString 的 UTC 字符串直接提交给后端 LocalDateTime；前端改为本地 YYYY-MM-DDTHH:mm:ss，避免中国时区被判定为定位过期。
  - 关联：外出打卡必须真实位置

### 敏感查看超限通知
  - *类型:feature*
  - 达到日限额时通过站内通知发送给老板和部门经理，并按员工与日期幂等。
  - 关联：敏感手机号查看留痕与日限额、Web消息通知铃铛

### 普通外出可不关联客户
  - *类型:feature*
  - 员工外出拆分HOME_VISIT和GENERAL；普通外出可不关联客户或预约，需本人提交、主管审批、照片+单点位置双打卡。
  - 关联：外出审批与照片硬门槛已确认、外出关联字段可空、普通外出迁移已执行

### 普通外出可无客户关联
  - *类型:feature*
  - GENERAL 外出允许 client_code 和 appointment_no 为空；HOME_VISIT 才关联上门预约。
  - 关联：员工外出审核链路

### 普通外出真实链路验收
  - *类型:feature*
  - ADV001 的普通外出测试记录已完成审核、COS 照片上传、出发和返回双打卡；最终 COMPLETED，appointment_no 为空，两段定位密文与两张照片键均落库。
  - 关联：Loan 远端 Redis 与 COS 烟测

### 服务台客户邀约入口
  - *类型:feature*
  - /service-operations 作为服务台菜单，承载预约来访、外出、客户跟进与统一服务协同；已写入远程 prd 菜单与角色授权。
  - 关联：Web 菜单产品视角重构

### 服务台日期条件真实验证
  - *类型:feature*
  - 预约页携带date=2026-09-27和COMPANY_ON_SITE后，业务日期与服务方式保留，结果0条且无越权数据。
  - 关联：部门经理真实会话验收

### 服务台独立路由
  - *类型:feature*
  - 今日服务台、客户预约、员工外出、客户回放分别使用/service-operations子路由。
  - 关联：Web独立子菜单页、服务方式无歧义文案

### 服务运营P2 Web统一工作区
  - *类型:feature*
  - Web新增/customer-service统一入口（实际路由/service-operations），以今日服务台、预约来访、员工外出、客户回放四个标签接入P1真实接口。
  - 关联：服务运营P1真实记录链路、P2预约履约操作映射、P2外出单点双打卡、P2客户服务菜单角色边界、服务运营P2验证结果、首条真实客户预约

### 服务运营链路提交
  - *类型:feature*
  - 服务运营 P0/P1 后端、Web客户服务、预约来访、员工外出打卡、客户回放已提交于21b6e2f。
  - 关联：远程落后本地一提交、未提交改动需保留

### 画像结构化提示归一为业务文案
  - *类型:feature*
  - 风险与经营建议可能是字符串、Map 或历史 Java Map 文本；后端新快照提取 content 并中文化 level/type，前端继续兼容历史 {level=..., content=...}，不得显示 tagType 等渲染元数据。
  - 关联：客户回放枚举统一中文展示

### 短信验证码当前模拟通道
  - *类型:feature*
  - SmsService 当前生成验证码并落库到短信记录、写入 Redis，channelCode=MOCK；Nacos 虽配置腾讯短信占位密钥，但本轮代码路径未发现真实腾讯云发送实现，配置真实通道后应按供应商计费确认。
  - 关联：开发环境第三方接口 Mock 状态

### 线索自动进入客户档案
  - *类型:feature*
  - 公司员工录入线索时在同一事务创建或复用客户档案并绑定归属；渠道线索审批链路保持不变。

### 网关Nacos启动预加载
  - *类型:feature*
  - 网关现在必须显式提供nacos.server-addr与nacos.namespace，并在启动前拉取application.properties；Redis/JWT/内部令牌不再使用本地默认。
  - 关联：基础设施仅Nacos红线

### 老板全司外出可见
  - *类型:feature*
  - 老板角色在员工外出页面可查看全司外出记录、客户、计划时间、审批人和打卡状态；已审批记录不出现在待审批列表。
  - 关联：审批待办与已审批分离、服务台按角色显示数据口径

### 角色数据范围切换由后端校验
  - *类型:feature*
  - 服务台支持 SELF/DEPARTMENT/COMPANY 范围切换；顾问仅 SELF，部门经理可 SELF/DEPARTMENT，公司管理角色可三者。接口服务层拒绝角色上限之外的 scope。
  - 关联：服务台按角色显示数据口径

### 角色菜单测试数据重置
  - *类型:feature*
  - 2026-09-22 通过 Nacos prd 数据源清理历史账号和业务数据，重建 7 角色、28 个活动菜单、313 个接口权限及客户预约/外出/跟进/报告测试链路。

### 跨模块联动顺序
  - *类型:feature*
  - 线索到客户到跟进画像到预约履约通知回放；外出申请到审批到打卡；材料OCR到双视角报告；手机号查看到超限审批。
  - 关联：菜单接口契约检查

### 远程搜索防抖与序列控制
  - *类型:feature*
  - 客户回放和预约客户下拉增加220ms防抖与请求序列号，避免卡顿和旧结果覆盖。

### 远程角色登录账号种子
  - *类型:feature*
  - prd loan_db已存在BOSS、ADVISER、DEPT_MANAGER、OPERATOR、SUPER_ADMIN、SUPER七类员工账号及一个CHANNEL渠道账号，均ACTIVE且密码字段非空。
  - 关联：prd基础设施配置来源、客户服务菜单角色边界

### 部门经理真实会话验收
  - *类型:feature*
  - 王经理会话显示部门主管；工作台团队客户32、团队线索1；审批菜单含外出审批、我的申请、客户分配审核和手机号查看审批。
  - 关联：服务台日期条件真实验证

### 首条真实客户预约
  - *类型:feature*
  - 顾问ADV001通过Web客户服务页面为现有客户创建首条真实预约：2026-09-22 10:00-11:00，到公司现场，公司咨询中心，状态REQUESTED且客户确认状态PENDING。
  - 关联：服务运营P2 Web统一工作区、员工代建预约待客户确认、顾问预约客户范围实测

