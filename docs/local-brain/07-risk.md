# 风险 Risk

> 由本地大脑自动蒸馏，生成时间 2026-09-29T03:00:05。

条目数：**201**


## 项目：abs

### ABS本期明确不做
  - *类型:bug*
  - 不建split_layer/data_layer/域准入条件表/日统计表/分流流水表；进实验由业务侧判断；效果报表归数分
  - 关联：ABS AB实验平台、abs · AB实验、abs 避坑

### abs 避坑
  - *类型:risk*
  - abs 项目已知问题与反模式索引
  - 关联：避坑记录、ABS本期明确不做、子实验零准入排查、审批页导航保护

### 子实验零准入排查
  - *类型:bug*
  - 上线后status=1全无≈C2全拒。先搜【准入未通过】reasonCode；核对parent分配同field、tag status=1、父是否先分流、绑定分组是否命中
  - 关联：TargetShuntReasonCode、子实验C2准入、上游分配Redis缓冲、abs · AB实验、abs 避坑

### 审批页导航保护
  - *域:client-lifecycle · 类型:bug*
  - 无审批权限时不渲染审批中心底部导航，避免出现可见但不可用的入口
  - 关联：abs · AB实验、abs 避坑


## 项目：compliance

### 合规四件套(前置签署)
  - *域:compliance · 类型:bug*
  - 服务协议/隐私政策/数据授权书/免责声明；正式代办前未签=合规红灯全部暂停。格式条款须加粗提示，成功费须书面明示比例与触发条件
  - 关联：合规整改(资金/审核)、贷款咨询专家团六阶段SOP、合规专项、合规避坑

### 合规避坑
  - *类型:risk*
  - compliance 项目已知问题与反模式索引
  - 关联：避坑记录、合规四件套(前置签署)、该个体户执照与贷款营销不匹配、第三方平台不得介入销售环节、电话邀约事实范围决定定性

### 电话邀约事实范围决定定性
  - *域:compliance · 类型:bug*
  - 若电话线索来自网络广告、通话中发送网页或二维码、引导进入网络申请页面，可能形成网络营销链路；纯线下名单加普通电话则不同。
  - 关联：纯电话邀约不当然属于网络营销、合规专项、合规避坑

### 第三方平台不得介入销售环节
  - *域:compliance · 类型:bug*
  - 办法第20条：不得介入合同签订、资金划转、适当性测评、贷款额度测评，不得就金融产品与消费者互动咨询。
  - 关联：贷款网络营销合规路径、匹配引擎可能越过纯营销边界、合规专项、合规避坑

### 该个体户执照与贷款营销不匹配
  - *域:compliance · 类型:bug*
  - 执照一般项目含信息咨询、商务代理代办、销售代理等，不含金融许可或金融信息服务资质；仅凭执照不足以自行网络营销贷款。
  - 关联：金融产品网络营销主体资格、合规专项、合规避坑


## 项目：loan-main

### 10万客户性能审计
  - *类型:bug*
  - 重点风险是全量 selectList、内存统计、重复名称查询、深分页和缺少组合索引。
  - 关联：loan-main · 企融通后端、loan-main 避坑

### Loan 业务服务直连鉴权缺口
  - *域:authorization · 类型:risk*
  - Spring Security anyRequest permitAll，管理拦截器仅覆盖admin/channel/debug；业务服务端口暴露时可绕过网关鉴权。
  - 关联：Loan 性能与权限审计报告

### Loan 核心性能热点
  - *域:performance · 类型:risk*
  - 主要热点为上传同步等待60秒VLM、报表按维度和月份串行SQL、回收任务无界扫描、每请求多次Redis往返及文件目录扫描。
  - 关联：Loan 性能与权限审计报告

### Loan 模拟身份入口风险
  - *域:authentication · 类型:risk*
  - 员工crmUserId直接登录接口、渠道固定loan-sim-pwd旁路和微信Mock身份均已从生产代码移除；员工改走短信验证码，渠道走RSA+BCrypt，H5走短信验证码。
  - 关联：Loan 性能与权限审计报告、员工验证式手机号登录、渠道RSA与BCrypt登录、微信真实code2session登录

### P1复用既有匹配结果
  - *域:report-isolation · 类型:risk*
  - 聚合报告读取t_screening_product及现有reportDiagnosis/reportProducts，不重新执行匹配，不改变渠道合作、产品来源或规则。
  - 关联：P1员工报告按reportNo聚合

### Web 团队客户页签缺失
  - *域:client-lifecycle · 类型:bug*
  - 后端 page-lite 支持 TEAM 与 TEAM_SEA，部门经理业务矩阵要求本团队范围，但 Web 客户页只渲染 MY/ALL/COMPANY_SEA，团队范围未在菜单/页签暴露。
  - 关联：客户菜单三范围核查、loan-main · 企融通后端、loan-main 避坑

### loan-main 避坑
  - *类型:risk*
  - loan-main 项目已知问题与反模式索引
  - 关联：避坑记录、顾问可切换公司客户风险、既有测试构造器过期、缓存故障降级、渠道公海服务层硬拒绝、Web 团队客户页签缺失、全司已分配角色范围缺口、公司客户与公海范围重叠

### 一次性算术验证码
  - *域:authentication · 类型:security-control*
  - 随机算术验证码在Redis保存3分钟，校验后无论成功失败立即删除，防重放与枚举。
  - 关联：全角色双模式登录

### 三端客户服务DTO分层
  - *域:cross-terminal · 类型:permission-boundary*
  - Web员工读取完整画像和内部回放；小程序/H5只读本人预约、到店、服务进度和客户可见摘要；渠道合作链路与权限范围不变。
  - 关联：客户活动时间线回放、客户服务信息可见范围、名单可见与操作权限分离、服务运营三端DTO边界

### 业务ID禁止作为页面展示值
  - *类型:risk*
  - Web、小程序、H5 的业务 ID 仅可用于路由、请求参数、组件 key/value 和内部关联；客户、员工、部门、产品、规则、报告、订单等页面必须展示名称，名称缺失显示明确占位，禁止回退显示编码。
  - 关联：本地知识大脑、DTO服务端批量装配展示名称、业务ID展示静态回归检查、Loan 业务编码展示隔离、客户回放枚举统一中文展示、画像维度隐藏内部编号

### 主原型顶部重叠：根因与修复
  - *类型:lesson*
  - 根因是 .m-body 使用负外边距且移动端头部叠加角色胶囊/头像提示。修复为取消负外边距、移除冗余头部元素，使快捷区回到正常文档流；修改后需同步检查主原型。
  - 关联：首页代码构建检查通过、loan-main · 企融通后端、loan-main 避坑

### 企业与个人账户往来分析边界
  - *域:operations-report · 类型:bug*
  - 仅在客户明确授权并上传相关账户资料后展示；自动结果只能描述往来频繁、用途待核对并建议分类记账，不直接使用资金混同嫌疑、体外循环等定性词。
  - 关联：数据授权必要但非营销授权替代、loan-main · 企融通后端、loan-main 避坑

### 免费公开不等于可任意画像
  - *类型:bug*
  - 接口免费或信息公开不当然构成收集、关联、画像和对外提供的合法依据；接入前仍需核验来源许可、必要性、个人授权、准确性、保存期限和展示范围。
  - 关联：数据授权必要但非营销授权替代、loan-main · 企融通后端、loan-main 避坑

### 全司已分配角色范围缺口
  - *类型:bug*
  - Web 全司已分配客户仅 BOSS、OPERATOR、SUPER_ADMIN、SUPER 可见；DEPT_MANAGER 后端虽支持 ALL/TEAM 范围但前端未开放全司已分配页签。
  - 关联：客户菜单三范围核查、loan-main · 企融通后端、loan-main 避坑

### 公司客户与公海范围重叠
  - *域:client-lifecycle · 类型:bug*
  - 当前 scope=ALL 未排除 owner_staff_code 为空的客户，因此公司客户总表包含公海客户；公司客户与公司公海不是互斥集合。若产品口径要求互斥，应将公司客户定义为已分配全司客户。
  - 关联：客户档案三类归属视图、loan-main · 企融通后端、loan-main 避坑

### 公司经营报表仅员工访问
  - *域:operations-report · 类型:bug*
  - 经营概览、运营分析和趋势接口拒绝渠道与客户账号，避免公司经营数据泄露。
  - 关联：角色化看板数据范围、规则配置管理角色、loan-main · 企融通后端、loan-main 避坑

### 内部名单分级数据范围
  - *域:authorization · 类型:data-scope*
  - 每日来访、预约、外出、待回访及服务工单名单按顾问本人、经理本部门、老板运营超管全公司的范围过滤；渠道账号完全排除。
  - 关联：每日服务名单聚合、名单可见与操作权限分离、服务名单分级权限策略

### 初筛报告显式分页计数
  - *类型:bug*
  - 先显式 selectCount，再关闭分页插件自动 count，规避生产 MySQL 派生表别名语法异常。
  - 关联：loan-main · 企融通后端、loan-main 避坑

### 初筛范围SQL括号修复
  - *类型:bug*
  - EXISTS 归属范围拼接移除多余右括号，并在生产库验证顾问和团队范围查询。
  - 关联：动态SQL括号与空集合审计、loan-main · 企融通后端、loan-main 避坑

### 动态SQL括号与空集合审计
  - *类型:bug*
  - 审计117处 exists/inSql/apply/selectCount 及全部 Mapper foreach；除初筛范围多余右括号外，未发现同类缺陷，批量 IN 入口均有非空校验或 size 守卫。
  - 关联：初筛范围SQL括号修复、loan-main · 企融通后端、loan-main 避坑

### 匹配引擎可能越过纯营销边界
  - *域:compliance · 类型:bug*
  - 平台用客户事实与银行准入规则匹配全部产品，输出可进件/需补料、评分、产品与银行数量，并引导顾问沟通；高度接近第20条禁止第三方介入的贷款额度测评或互动咨询边界。
  - 关联：第三方平台不得介入销售环节、风险分析与银行提交分轨、loan-main · 企融通后端、loan-main 避坑

### 匹配池缺少合作有效性过滤
  - *类型:bug*
  - PlanLoaderService当前扫描ACTIVE策略，但未强制产品APPROVED、合作库ACTIVE且未过期；内部候选池应取四条件交集。
  - 关联：三端双视角报告主链路、loan-main · 企融通后端、loan-main 避坑

### 历史生命周期基线估算
  - *域:client-lifecycle · 类型:bug*
  - 历史客户以创建时间补归属/入池基线，并以 HISTORICAL_BASELINE_ESTIMATE 明确标识估算。
  - 关联：客户生命周期事件、生产生命周期迁移已执行、loan-main · 企融通后端、loan-main 避坑

### 合作产品活动接口暴露过宽
  - *类型:bug*
  - 现有/api/mini/partner-product/active对所有登录用户开放且渠道API白名单放行，应收口为仅STAFF并移除客户端调用。
  - 关联：员工产品渠道可见性、loan-main · 企融通后端、loan-main 避坑

### 合作库角色边界
  - *类型:bug*
  - 公司内部仅老板/超管可查看操作合作库；渠道仍通过渠道工作区读取合作产品。
  - 关联：公司产品审批发布、loan-main · 企融通后端、loan-main 避坑

### 名单可见与操作权限分离
  - *域:authorization · 类型:permission-boundary*
  - 名单权限只表示列表数据可见范围，不自动授予修改、取消、打卡或异常处理能力；每个写操作仍须按负责人和角色独立校验。
  - 关联：内部名单分级数据范围、三端客户服务DTO分层、服务名单分级权限策略

### 员工产品渠道可见性
  - *类型:bug*
  - 银行、担保、保函渠道及候选进件产品只进入STAFF内部视角；客户和CHANNEL不可见，内部信息不得复制到客户报告或公开链接。
  - 关联：内部分析工具隔离条件、渠道仍可见客户报告缺口、合作产品活动接口暴露过宽、loan-main · 企融通后端、loan-main 避坑

### 基础建库脚本与运行实体漂移
  - *类型:bug*
  - loan-db-schema.sql 与当前运行实体/迁移存在漂移，包括微信字段、生命周期表、策略业务键、计划步骤字段、match_trace_uuid 等；直接按基础脚本建库不能完成登录与报告生成。
  - 关联：报告隔离临时实测环境、loan-main · 企融通后端、loan-main 避坑

### 外出打卡加密单点定位
  - *域:privacy · 类型:data-protection*
  - 出发与返回必须顺序打卡；每次定位作为加密载荷保存，经纬度、精度、文本与采集时间不落明文列，不采集连续轨迹。
  - 关联：服务运营P0四表模型、外出打卡与预约履约联动

### 客户状态枚举漂移（已修）
  - *域:client-lifecycle · 类型:bug*
  - MiniClientService.create 曾写 status="NORMAL"（全仓唯一），schema 为 ACTIVE/DISABLED，会导致统计漏数；已改 ACTIVE，并把 DashboardService 客户口径改为「排除 DISABLED」保证统计与列表一致
  - 关联：小程序「我的客户」+公海认领、loan-main · 企融通后端、loan-main 避坑

### 对客行业基准禁止默认值
  - *类型:bug*
  - 行业对比仅在存在真实基准来源、样本量与统计期间时展示；无真实基准时显示暂无可比数据，不使用硬编码均值或模拟评分。
  - 关联：内部分析工具隔离条件、loan-main · 企融通后端、loan-main 避坑

### 当前OCR为Mock未调用AI
  - *域:material-ocr · 类型:risk*
  - application.properties配置loan.ocr.provider=mock且VLM base-url/api-key为空；上传会分类存储，但MockOcrExtractor返回空facts，不产生材料复核数据或AI提取事实。
  - 关联：loan-main · 企融通后端、复核事实进入内部匹配链路

### 打卡单点定位边界
  - *域:privacy · 类型:data-boundary*
  - 每次打卡仅在员工主动操作并授权后保存一个位置点及精度、文本、采集时间；禁止后台持续定位和保存移动轨迹，保留期限待统一确认。
  - 关联：外出出发返回双打卡

### 报告子资源统一员工范围校验
  - *类型:risk*
  - 报告详情、产品明细、经营诊断必须在服务层复用 STAFF 本人/部门/全公司范围校验，禁止通过可枚举报告编号绕过授权。
  - 关联：本地知识大脑

### 报告模板尚未实际渲染
  - *域:operations-report · 类型:risk*
  - 初筛生成只选择ACTIVE模板并保存templateCode；gradeRuleJson、disclaimerText、adviceRulesJson、watermarkConfig未参与客户/员工报告组装，reportFileKey也未生成。当前是DTO和页面动态展示，不是双模板快照或PDF。
  - 关联：P1员工报告按reportNo聚合

### 无委托时应移除的对客数据
  - *域:compliance · 类型:bug*
  - 移除多银行产品匹配、可进件/需补料、匹配产品数、银行覆盖数、匹配度、具体额度利率咨询顾问等文案，避免构成产品适配或转接暗示。
  - 关联：隐藏产品不当然排除网络营销、经营建议四段式文案、资金快速周转中性表述、loan-main · 企融通后端、loan-main 避坑

### 既有测试构造器过期
  - *类型:bug*
  - BusinessNameServiceTest 仍使用旧三参数构造器，当前生产类需要 UnifiedCacheService 四参数，导致 Maven 测试编译被阻断。
  - 关联：企融通贷款系统重构、loan-main · 企融通后端、loan-main 避坑

### 旭荣画像证据边界
  - *域:loan-consult · 类型:bug*
  - 现有旭荣画像来自历史内部记录，缺少完整原始凭证；经营诊断原型数字均为页面示例，不能作为正式财务、司法、信用或融资结论。
  - 关联：上海旭荣网络科技企业画像、旭荣网络内部经营咨询报告、loan-main · 企融通后端、loan-main 避坑

### 服务名单分级权限策略
  - *域:authorization · 类型:implementation*
  - 顾问SELF、部门经理DEPARTMENT、老板运营超管COMPANY；客户与渠道NONE。列表范围与写操作分离，普通预约操作只允许客户本人或主服务顾问。
  - 关联：内部名单分级数据范围、名单可见与操作权限分离、服务运营三端DTO边界、服务接口服务端身份收口

### 服务接口服务端身份收口
  - *域:authorization · 类型:security*
  - 客户预约与时间线的clientCode只取登录态，忽略客户端伪造的客户、顾问、工单和内部备注；工单及预约关联必须校验属于同一客户。
  - 关联：服务名单分级权限策略

### 服务状态原子流转
  - *域:concurrency · 类型:implementation*
  - 预约与外出状态更新同时匹配业务编号和预期旧状态；重复点击或并发请求只有一次成功，主记录与活动事件同事务提交或回滚。
  - 关联：服务运营P1真实记录链路

### 材料下载对象级授权缺失
  - *域:client-lifecycle · 类型:risk*
  - 小程序按fileKey预览材料只校验格式和对象存在，未校验附件归属、角色范围、审批和审计；需按附件元数据执行对象级授权。
  - 关联：Loan 性能与权限审计报告

### 材料分类契约不一致
  - *域:material-ocr · 类型:risk*
  - 小程序/Web分类包含TAX_RECORD、INVOICE_RECORD、BANK_STATEMENT、CREDIT_REPORT等，但后端注释和数据库枚举说明仍仅列ID_CARD/BUSINESS_LICENSE/FINANCIAL_STATEMENT/CONTRACT/DUE_DILIGENCE/OTHER；varchar可保存但缺统一白名单与分类专属规则。
  - 关联：复核事实进入内部匹配链路

### 材料复核后才能精准初筛
  - *域:client-lifecycle · 类型:bug*
  - 存在 PENDING_REVIEW 材料时服务端禁止执行初筛；通过后 OCR 事实进入最新客户提交数据。
  - 关联：材料客户全链路绑定、精准初筛事实优先级、loan-main · 企融通后端、loan-main 避坑

### 渠道仍可见客户报告缺口
  - *类型:bug*
  - 现有/api/channel/report及Web渠道报告页仍返回评级、银行数、产品数，与非员工不得查看客户报告的目标冲突，应下线并返回403。
  - 关联：员工产品渠道可见性、loan-main · 企融通后端、loan-main 避坑

### 渠道公海服务层硬拒绝
  - *域:client-lifecycle · 类型:bug*
  - 渠道账号访问公海必须在服务层直接拒绝，不能仅依赖前端菜单隐藏或网关规则。
  - 关联：loan-main · 企融通后端、loan-main 避坑

### 用户渠道分享金融匹配页面风险
  - *域:compliance · 类型:bug*
  - 客户与渠道可分享/复制‘企业资金智能匹配’链接且含推荐有礼；第16条要求公众号、直播、短视频营销在金融机构合法账号且人员为金融机构从业人员，当前分享机制缺乏主体限制与内容审批。
  - 关联：loan小程序与H5构成网络营销高概率、loan-main · 企融通后端、loan-main 避坑

### 短信验证码场景隔离
  - *域:sms · 类型:security-control*
  - LOGIN与RESET_PASSWORD使用独立Redis键和60秒频控，验证码成功后一次性删除。
  - 关联：全角色双模式登录、短信记录按模板ID落库

### 系统异常安全提示
  - *类型:bug*
  - 系统异常仅返回统一内部错误提示，详细堆栈写入服务端错误日志。
  - 关联：全局异常响应契约、loan-main · 企融通后端、loan-main 避坑

### 线下顾问使用需另行评价
  - *域:compliance · 类型:bug*
  - 员工在线下或电话中使用内部结果推荐产品，不一定构成本办法的网络营销，但不因此当然合法；需依据其他适用规则另行判断，本次仅依本办法不作结论。
  - 关联：纯电话邀约不当然属于网络营销、loan-main · 企融通后端、loan-main 避坑

### 经营指标采集缺口
  - *域:operations-report · 类型:bug*
  - 现有 last_followed_at 会在分配时刷新，无法可靠计算首次跟进时长；公海流水缺少显式入池批次配对，也不能准确计算平均停留时长，页面显示待补采集。
  - 关联：客户运营统计接口、首次跟进自然小时、公海停留自然小时口径与实现、loan-main · 企融通后端、loan-main 避坑

### 诊断数据来源状态
  - *域:loan-consult · 类型:bug*
  - 所有诊断字段区分系统已核验、OCR提取、待补采集；缺失不按0分处理，预测值必须明确标记。
  - 关联：单企业经营诊断报告原型V1、loan-main · 企融通后端、loan-main 避坑

### 贷款提供方披露不足
  - *域:compliance · 类型:bug*
  - 客户页只展示银行数量和评级并引导咨询顾问，未展示受托金融机构基本信息、官网和客服；与第9条披露要求、贷款产品由金融机构自身名义发布的第24条存在缺口。
  - 关联：贷款网络营销合规路径、loan-main · 企融通后端、loan-main 避坑

### 资金快速周转中性表述
  - *域:operations-report · 类型:bug*
  - 同日大额流入流出只标记为建议核对日期，模块命名为资金快速周转观察；不得自动认定虚假流水、刷流水或违规。
  - 关联：无委托时应移除的对客数据、loan-main · 企融通后端、loan-main 避坑

### 金融机构直接委托证据缺口
  - *域:compliance · 类型:bug*
  - 仓库未见金融机构直接书面委托、委托范围、责任边界及产品审核材料；平台允许我司或渠道录入银行产品并由我司终审，无法满足第5、22、25条的可证明要求。
  - 关联：贷款网络营销合规路径、数据授权必要但非营销授权替代、loan-main · 企融通后端、loan-main 避坑

### 金融营销数据授权缺口
  - *域:compliance · 类型:bug*
  - 登录协议默认勾选且仅为简短弹窗；认证授权只称供平台匹配，未见向具体金融机构提供数据的对象、范围和授权记录链路。若向机构传输，难以满足第27条。
  - 关联：loan现有营销合规控制、loan-main · 企融通后端、loan-main 避坑

### 链路日志敏感边界
  - *类型:bug*
  - 网关访问日志只记录链路元数据，不记录 Token、请求体等敏感内容。
  - 关联：网关链路访问日志、loan-main · 企融通后端、loan-main 避坑

### 隐藏产品不当然排除网络营销
  - *域:compliance · 类型:bug*
  - 客户虽看不到具体产品，但多银行匹配、可进件、产品数、银行覆盖数仍传达金融产品适配结论；第3条定义不以展示具体产品名称为唯一条件。
  - 关联：无委托时应移除的对客数据、聚合产品结果仍是对客透出、loan-main · 企融通后端、loan-main 避坑

### 顾问可切换公司客户风险
  - *域:client-lifecycle · 类型:bug*
  - ClientProfile 对顾问展示公司客户标签，page-lite 的 scope=ALL 不按顾问本人强制收口，与角色矩阵顾问仅本人客户冲突；需前端隐藏并由后端按角色硬隔离。
  - 关联：客户档案三类归属视图、loan-main · 企融通后端、loan-main 避坑

### 预约五分钟截止线服务端规则
  - *域:service-operations · 类型:validation*
  - 恰好开始前5分钟仍允许客户或员工自行改期取消；进入最后5分钟后仅允许员工异常处理并留痕，规则由服务端状态机校验。
  - 关联：按服务方式区分预约状态机


## 项目：loan-mini

### TabBar 深色主题作用域：问题与修复
  - *类型:lesson*
  - 根因是 TabBar 根节点未绑定 data-theme，导致组件内暗色变量不生效。已绑定 :data-theme="themeMode"，并通过 stylelint 与 H5 构建验证；新增主题组件时需验证变量作用域。
  - 关联：一键双主题切换、TabBar 当前图标几何、loan-mini · 小程序/H5、loan-mini 避坑

### Web 公司公海枚举不一致
  - *域:client-lifecycle · 类型:bug*
  - Web 管理端 COMPANY_SEA 被 ClientController 转成 seaLevel=COMPANY，但数据库迁移及小程序统一使用 ENTERPRISE，导致公司公海查询可能为空。应统一为 ENTERPRISE。
  - 关联：客户档案三类归属视图、loan-mini · 小程序/H5、loan-mini 避坑

### loan-mini 避坑
  - *类型:risk*
  - loan-mini 项目已知问题与反模式索引
  - 关联：避坑记录、首页候选原型待确认、构建残留待清理、登录协议强制同意、手机号登录协议缺口、移动端无效快捷入口、消息中心示例数据风险、reLaunch 状态丢失

### loan小程序与H5构成网络营销高概率
  - *域:compliance · 类型:bug*
  - 公开入口宣传多银行产品匹配，在线收集经营/征信/材料并生成可进件、评级、产品数量；符合通过互联网展示金融产品信息并促成购买咨询的高概率特征。
  - 关联：金融产品网络营销主体资格、用户渠道分享金融匹配页面风险、loan-mini · 小程序/H5、loan-mini 避坑

### reLaunch 状态丢失
  - *类型:bug*
  - 自绘 TabBar 全量 reLaunch 会丢失列表筛选、滚动与表单状态，需要跨端导航适配器
  - 关联：三端页面重构待处理、loan-mini · 小程序/H5、loan-mini 避坑

### 三端页面重构待处理
  - *类型:bug*
  - Web、H5、小程序页面重构已登记为待处理，后续按角色、权限、视觉和跨端验收分阶段完成
  - 关联：手机号登录协议缺口、移动端无效快捷入口、消息中心示例数据风险、reLaunch 状态丢失、移动端视觉令牌分散、loan-mini · 小程序/H5、loan-mini 避坑

### 合并页组件化踩坑
  - *类型:bug*
  - ①组件内 onLoad/onShow/onReachBottom/onPullDownRefresh 失效→改 onMounted+watch(active)并由容器页转发；②pages/xxx/(两级)抽到 components/(一级)须把 ../../ 降为 ../；③抽组件时删掉的入口守卫必须在容器页用 v-if 补回，否则会请求无权接口
  - 关联：TabBar 合并页（线索录入/我的客户）、loan-mini · 小程序/H5、loan-mini 避坑

### 客户报告隐藏银行覆盖
  - *类型:compliance-control*
  - 客户报告不得出现银行覆盖数量或0家占位。report/detail.vue 已将该行限制为员工可见，交互原型同步移除；客户视角只保留经营与风险分析。
  - 关联：内部分析工具隔离条件、loan-mini · 小程序/H5、loan-mini 避坑

### 小程序不提供密码找回
  - *域:authentication · 类型:boundary*
  - 小程序端不调用密码登录、RSA公钥或reset-password接口；员工和渠道Web密码流程保持独立。
  - 关联：小程序仅支持验证码备用登录

### 手机号登录协议缺口
  - *类型:bug*
  - phone-login 仅展示默认同意文案，没有可勾选状态与发送/登录拦截
  - 关联：三端页面重构待处理、loan-mini · 小程序/H5、loan-mini 避坑

### 构建残留待清理
  - *类型:bug*
  - loan-web/dist_old/ 与 loan-mini/dist/build/h5_old/ 因沙箱批量删除守卫未清理，待分批删或手动 rm -rf
  - 关联：沙箱批量删除守卫、小程序端 loan-mini、Web 管理端 loan-web、loan-mini · 小程序/H5、loan-mini 避坑

### 消息中心示例数据风险
  - *类型:bug*
  - 首页和我的页面硬编码消息与红点，应接入真实消息接口并抽取公共组件
  - 关联：三端页面重构待处理、loan-mini · 小程序/H5、loan-mini 避坑

### 登录协议强制同意
  - *类型:bug*
  - 用户协议和隐私政策默认勾选；用户取消后不可进入小程序、不可微信登录、不可发送验证码或手机号登录；协议文本可点击查看。
  - 关联：微信一键登录主入口、loan-mini · 小程序/H5、loan-mini 避坑

### 登录协议改为显式勾选
  - *类型:bug*
  - H5和小程序登录协议默认不勾选，用户主动勾选后才能提交登录，降低同意有效性风险。
  - 关联：数据授权必要但非营销授权替代、loan-mini · 小程序/H5、loan-mini 避坑

### 移动端无效快捷入口
  - *类型:bug*
  - 多个角色快捷入口 route 为空，点击才提示 Web；无移动能力应直接隐藏
  - 关联：三端页面重构待处理、loan-mini · 小程序/H5、loan-mini 避坑

### 聚合产品结果仍是对客透出
  - *域:compliance · 类型:bug*
  - 当前小程序客户页仍展示多银行匹配、可进件、可匹配产品数、银行覆盖数等由产品规则生成的聚合结论；虽隐藏具体产品名，仍削弱‘完全内部使用’主张。
  - 关联：隐藏产品不当然排除网络营销、loan-mini · 小程序/H5、loan-mini 避坑

### 首页候选原型待确认
  - *类型:bug*
  - 本轮仅输出独立可视化原型，未修改业务代码或docs原型；用户确认后再更新原型图与实现。
  - 关联：首页原型采用Hero叠层结构、loan-main · 企融通后端、loan-mini · 小程序/H5、loan-mini 避坑


## 项目：market-cdp

### CDP 避坑
  - *类型:risk*
  - market-cdp 项目已知问题与反模式索引
  - 关联：避坑记录、禁止 users 加 one_id 列、禁止 cust_no 作 identity、禁止热路径写 staging、UNMATCHED 禁止写 events

### UNMATCHED 禁止写 events
  - *类型:bug*
  - 未匹配身份不得 INSERT events
  - 关联：CDP 避坑、market · CDP

### 禁止 cust_no 作 identity
  - *类型:bug*
  - cust_no 仅 properties/宽表；identity 用 user_no+设备/手机
  - 关联：CDP 避坑、market · CDP

### 禁止 users 加 one_id 列
  - *类型:bug*
  - OneID=users.id；ADD user_id/one_id 会偏离神策对齐
  - 关联：CDP 避坑、market · CDP

### 禁止热路径写 staging
  - *类型:bug*
  - staging 仅 UNMATCHED/FAILED/CONFLICT
  - 关联：CDP 避坑、market · CDP


## 项目：mds

### MDS 未完成项
  - *类型:bug*
  - V2未切主路径(A1); 回传R1-R10(荣耀/WifiKey缺Handler、放款ignore未对齐),目标2026.10全量切新
  - 关联：MDS 营销渠道中台、mds · 营销中台、mds 避坑

### mds 避坑
  - *类型:risk*
  - mds 项目已知问题与反模式索引
  - 关联：避坑记录、MDS 未完成项


## 项目：platform

### 8777旧服务阻塞连接
  - *类型:pitfall*
  - 旧kg_server进程与浏览器失效连接占用8777，导致新页面请求超时；需先杀死监听进程再启动单一实例。
  - 关联：知识图服务线程化修复、浏览器旧错误标签页

### 公共风险与经验
  - *域:engineering-lessons · 类型:hub*
  - 跨项目可复用的根因、修复和验证模式；项目特有事故仍留在所属项目。
  - 关联：公共知识库、缓存故障降级、探索成功vs公共能力、沙箱批量删除守卫、P0 分页边界、探索打包为Skill、参数日志安全控制、公共性能能力

### 参数日志安全控制
  - *类型:bug*
  - 请求响应日志限制 4KB，跳过二进制和大载荷，并复用敏感字段脱敏策略。
  - 关联：请求响应审计日志、loan-main · 企融通后端、loan-main 避坑、公共风险与经验

### 开发参考非联动避坑泄漏
  - *类型:bug*
  - viewer虽按真实links展示关联节点，但仍渲染同项目未连线避坑，buildDevPrompt也注入同项目避坑，可能误导为联动红线。
  - 关联：开发参考工作流

### 沙箱批量删除守卫
  - *类型:bug*
  - 单操作删除≥50文件被拦截；分批<50或授权绕过
  - 关联：构建残留待清理、mp-weixin 真机构建、loan-main · 企融通后端、loan-main 避坑、公共风险与经验

### 浏览器旧错误标签页
  - *类型:pitfall*
  - 服务恢复后浏览器仍可能停留在ERR_CONNECTION_REFUSED旧页；需关闭旧标签并重新打开http://127.0.0.1:8777/knowledge-graph-viewer.html。
  - 关联：8777旧服务阻塞连接

### 知识发布治理门禁
  - *类型:constraint*
  - 知识必须经过接入、清洗、分类、归纳、可靠性核验、Skill审计回归后才能进入可复用正式库。
  - 关联：开放式全行业知识图书馆

### 知识图统计元数据漂移
  - *类型:bug*
  - knowledge.json实际501节点/909连线，meta.stats仍登记422/824，蒸馏时间仍为2026-09-20，需生成式校验而非手填。
  - 关联：本地大脑审计结论

### 知识图页面超时是浏览器渲染卡死
  - *类型:pitfall*
  - 服务端页面/JSON请求均快速HTTP 200；浏览器Renderer CPU 98%–194%，根因是默认全量514节点脑图布局与重绘导致主线程卡死，不是8777端口问题。
  - 关联：知识图服务线程化修复、全馆目录默认懒加载、本地大脑运行诊断证据

### 缓存故障降级
  - *类型:bug*
  - Redis 不可用时名称查询自动回退数据库并保留本地缓存，不影响业务请求。
  - 关联：业务名称批量缓存、loan-main · 企融通后端、loan-main 避坑、公共风险与经验

### 规范节点真源绑定缺口
  - *类型:bug*
  - 8个spec节点中仅前端节点有资源且路径不存在；后端/UI/UX/架构/数据库/测试/运维规范未绑定可验证SKILL.md真源。
  - 关联：开发参考

### 避坑记录
  - *类型:risk*
  - 历史 bug / 反模式 / 已踩坑；开发前检索避免重复
  - 关联：开发参考、loan-main 避坑、loan-mini 避坑、abs 避坑、合规避坑、mds 避坑、CDP 避坑

### 项目基线可验证性缺口
  - *类型:bug*
  - loan-platform文档检索Skill声明output/方案评审定稿纪要.html和.workbuddy/memory为基线，但当前仓库未发现output或.workbuddy目录，无法证明文档已与基线比对。
  - 关联：本地大脑审计结论


## 项目：（未归类）

### AES历史明文兼容
  - *类型:risk*
  - 远端历史手机号/短信记录存在明文，AesUtils 对合法手机号读取时原样兼容；新写入仍强制 AES。

### AES密钥缺失即失败
  - *类型:risk*
  - AesUtils移除可运行开发默认密钥，缺少aes.key直接启动失败，密钥必须来自Nacos。
  - 关联：Loan系统反方综合审计

### Gateway Mono Void 空流二次拒绝陷阱
  - *类型:risk*
  - chain.filter 返回 Mono<Void> 正常完成不发元素，flatMap 后使用 switchIfEmpty 会把成功转发误判为空并再次写 403；必须在规则流阶段 defaultIfEmpty 后再 flatMap。
  - 关联：手机号超限审批真实验收

### GitHub推送需要认证
  - *类型:risk*
  - HTTPS远程origin缺少可用凭据，git push会因无法读取Username失败。
  - 关联：远程落后本地一提交

### H5字母数字验证码输入
  - *类型:risk*
  - H5/小程序验证码输入框使用text和字母输入模式，避免number类型拒绝随机字母验证码。
  - 关联：真实联动受登录会话阻塞

### H5运行端阻塞
  - *类型:risk*
  - H5构建成功但本轮9174未监听，真实三端联动尚未闭环。
  - 关联：全量回归测试基线

### Loan 业务服务直连风险
  - *类型:risk*
  - 9080 可绕过 9088 直接处理 JWT 请求；网关只能作为第一层，9080 需网络隔离，业务层仍须认证、接口和对象级 fail-closed。
  - 关联：Loan 网关权限矩阵待复测

### Loan 严格接口与菜单矩阵
  - *类型:risk*
  - 接口权限默认 fail-closed；叶子菜单自动补祖先；顾问仅我的申请，经理部门审批，老板/超管全司审批，渠道排除内部服务审批。
  - 关联：Loan prd 菜单迁移已验收、Loan 客户服务工作区闭环

### Loan 后端启动初始化偏慢
  - *类型:risk*
  - 远端数据库首次 SELECT x 约 9.2 秒，随后 Spring AOP/MyBatis Mapper 与 Bean 初始化持续较久；不是 Nacos、Redis 或 COS 连接失败，需单独做启动性能治理。
  - 关联：Loan prd 基础设施配置真值、Loan 服务冷启动 602 秒

### Loan 审批部门范围缺口
  - *类型:risk*
  - 统一待审聚合的下载、材料、短信模板和报告模板分支没有传当前用户或部门范围；部门经理可能看到跨部门待审数据。

### Loan 客户对象级权限缺口
  - *类型:risk*
  - 2026-09-28 已修复：统一 ClientAccessGuard 覆盖客户详情、历史、编辑；顾问本人/公海只读、经理本部门、管理角色全司，编辑在事务内 FOR UPDATE 防止归属并发变化；loan-service 446 项测试通过，待 9088 真实角色验收。

### Loan 当前手机号解锁规则
  - *类型:risk*
  - 列表仅脱敏；首次解锁按员工、客户、日期计数；顾问超限由经理审批，经理超限由老板审批，老板和超管可直接查看并留审计。
  - 关联：Loan 当前文档单一真值

### Loan 服务冷启动 602 秒
  - *类型:risk*
  - 本次 Started LoanApplication 日志为 602.274 秒；线程栈显示 AOP、MyBatis Mapper、Bean 初始化持续占用主线程，数据库首次 SELECT x 约 9.2 秒只是其中一部分。
  - 关联：Loan 后端启动初始化偏慢、Loan 解压 classpath 启动优化

### Loan 管理角色接口过度授权
  - *类型:risk*
  - BOSS、OPERATOR、SUPER_ADMIN、SUPER 被列入 FULL_ACCESS_ROLES；OPERATOR/BOSS 的菜单和逐条 t_role_api 不能约束新增 admin API，应改为最小 allow-list。

### Loan 菜单页面接口权限漂移
  - *类型:risk*
  - 存在菜单可见但接口拒绝（非经理 TEAM）、菜单不可见但路由/API 放行（报告兜底、superRoles）、渠道快捷入口无真实叶子菜单等漂移。
  - 关联：Loan 网关权限矩阵待复测

### Loan系统反方综合审计
  - *类型:risk*
  - 从业务、权限、合规、性能、Web、小程序和H5审查；结论为P0未清零前不满足安全上线条件。
  - 关联：loan-main · 企融通后端、业务服务纵深鉴权缺口、外出无需审批与实现冲突、预约确认语义冲突、报告授权声明证据缺口、H5登录主操作冲突、工作台聚合与性能缺口、外出审批与照片硬门槛已确认

### Nacos数据源红线
  - *类型:risk*
  - 数据库连接必须从显式Nacos namespace/group=loan读取，禁止本地默认MySQL。
  - 关联：远程测试外出审批链路

### OCR/VLM 费用边界
  - *类型:risk*
  - MockOcrExtractor 不调用外部服务；VlmOcrExtractor 仅在配置 base-url 与 api-key 后向 OpenAI 兼容 chat/completions 发送材料内容，是否免费取决于供应商或自托管服务。
  - 关联：开发环境第三方接口 Mock 状态

### P2客户服务菜单角色边界
  - *类型:risk*
  - 客户服务菜单只授予ADVISER、DEPT_MANAGER、BOSS、OPERATOR、SUPER_ADMIN、SUPER，显式清除CHANNEL；菜单迁移对旧本地库补齐必要兼容列。
  - 关联：服务运营P2 Web统一工作区

### loan_db 与 erp_db 业务表边界
  - *类型:risk*
  - 两库位于同一远端 MySQL 且账号一致，但 erp_db 缺少 loan 核心表；loan 服务必须保留 loan_db，不能因复用 tse 配置而切换库名。
  - 关联：Loan prd 基础设施配置真值

### mds AB关闭级联清理修复
  - *类型:risk*
  - mds 最新提交 cd50ec41 要求缺少 admissionScene 明确失败，生效绑定必须走统一 disable 级联清理，避免生产脏数据。
  - 关联：策略计划级联删除审查

### prd基础设施配置来源
  - *类型:risk*
  - 生产联调数据库、Redis、OSS等配置必须从prd Nacos读取；禁止回退本地服务或默认凭据。
  - 关联：远程角色登录账号种子

### prd微信凭据缺失
  - *类型:risk*
  - prd Nacos application.properties未配置wechat.appid和wechat.secret，当前后端会按代码返回未配置真实微信appid/secret。
  - 关联：小程序wx.login登录链路

### 上传目录由 Nacos 配置
  - *类型:risk*
  - loan-service 启动依赖 loan.upload.base-dir；开发 prd Nacos 已配置 /app/data/oss-storage，loan.oss.mode=local 以创建上传/OCR OSS Bean。

### 业务ID展示边界
  - *类型:risk*
  - 业务编码仅用于请求参数、路由和row-key；客户侧页面展示姓名、企业名、手机号脱敏和日期。
  - 关联：手机号列表统一脱敏

### 业务服务纵深鉴权缺口
  - *类型:risk*
  - loan-service最终anyRequest permitAll且接口权限默认非严格，绕过网关直连时存在漏守卫风险。
  - 关联：Loan系统反方综合审计

### 共享文件仍有未提交差异
  - *类型:risk*
  - ApiPermissionSyncService.java、Layout.vue、router/index.js同时出现在提交和工作区；提交内是服务运营接入，工作区差异仍未提交。
  - 关联：21b6e2f范围已核验

### 内置浏览器不提供定位能力
  - *类型:risk*
  - Codex 内置浏览器当前页面运行环境的 navigator.geolocation 不可用；点击获取当前位置后仍显示尚未获取定位。不能用伪造坐标或直接改库替代真实单点定位。
  - 关联：外出打卡必须真实位置

### 内部令牌改用请求头
  - *类型:risk*
  - 网关拉取接口权限规则使用X-Internal-Token，不再通过URL查询参数传递，避免进入访问日志。
  - 关联：Loan系统反方综合审计

### 内部角色业务入口权限
  - *类型:risk*
  - 新增幂等迁移补齐顾问及内部管理角色的线索、客户检索、公海、材料、产品入口，不授予渠道角色。
  - 关联：审批关联业务跳转

### 员工外出审核迁移状态
  - *类型:risk*
  - 已从 prd Nacos 数据源执行并核验：默认状态为 PENDING_REVIEW，CHECK 包含 PENDING_REVIEW/REJECTED，新增审核字段和待审索引已生效。
  - 关联：迁移必须读取所选 Nacos 数据源、普通外出审批与双凭证打卡

### 员工密码列迁移必需
  - *类型:risk*
  - 旧本地库t_staff缺少password会导致密码登录SQLSyntaxError，需执行migrate-auth-local-compat迁移。
  - 关联：Web登录模式文案隔离

### 员工报告子资源统一数据范围
  - *类型:risk*
  - 小程序员工报告详情、产品明细、经营诊断均先执行 STAFF 身份及本人/部门/全公司范围校验，堵住 reportNo 旁路越权。
  - 关联：三端构建验证 2026-09-24

### 基础设施仅Nacos红线
  - *类型:risk*
  - 本机不启动或依赖MySQL、Redis、Nacos、OSS/COS模拟服务或Docker；运行时基础设施配置必须来自指定Nacos namespace/group loan。
  - 关联：网关Nacos启动预加载、本地OSS仅显式模式

### 外出审批通过待本人打卡
  - *类型:risk*
  - 外出记录经部门经理审批后进入 READY；actual_departed_at/actual_returned_at及照片仍为空，必须由申请人真实会话完成双打卡，禁止代打卡或直接改库。
  - 关联：审批待办与已审批分离

### 外出审核状态迁移未完成
  - *类型:risk*
  - t_staff_outing当前status默认DRAFT且CHECK仍为旧五态，虽有审核字段和idx_outing_status_time，说明审核状态SQL未完整执行。

### 外出打卡必须真实位置
  - *类型:risk*
  - 出发和返回打卡都必须同时提交现场照片与单点定位；定位不可用时不上传照片、不提交半条记录，需改用支持定位的浏览器或设备完成。
  - 关联：内置浏览器不提供定位能力、打卡定位时间按本地格式提交

### 外出无需审批与实现冲突
  - *类型:risk*
  - 用户要求无需审批、必须打卡、保存单点位置；实现仍强制PENDING_REVIEW和主管审批。
  - 关联：Loan系统反方综合审计

### 外出申请未进入我的申请
  - *类型:risk*
  - 统一审批 myApplications 目前未聚合 t_staff_outing，员工提交外出后在审批中心不可追踪。
  - 关联：员工外出审核链路

### 子菜单授权兼容
  - *类型:risk*
  - 新叶子上线时routeAccess兼容旧聚合菜单授权，迁移完成后以后端独立叶子为准。
  - 关联：Web独立子菜单页

### 审批禁止自审
  - *类型:risk*
  - 外出审批无论角色级别均排除申请人本人；列表和写接口双重限制。
  - 关联：审批按数据可见范围

### 客户回放接口页面权限前缀修复
  - *类型:risk*
  - /api/admin/client/** 同时承载客户档案和客户回放，宽泛前缀会误校验 page:client；活动回放与画像接口改按 page:service-replay 校验。
  - 关联：客户子菜单权限迁移

### 客户子菜单权限迁移
  - *类型:risk*
  - 发现历史角色授权仍绑定已停用 /client 父菜单，新的客户子菜单缺少角色授权，导致预约客户搜索和回放接口触发 page:client 403；已执行幂等迁移补齐角色与新子菜单授权。
  - 关联：客户回放接口页面权限前缀修复

### 客户手机号内部原值列
  - *类型:risk*
  - t_client_profile 新增 phone_plain；phone 仍 AES 加密，实体字段 select=false，普通查询和对外 DTO 不返回。
  - 关联：客户手机号继续加密落库

### 客户手机号原值仅内部受控读取
  - *类型:risk*
  - t_client_profile.phone 保持 AES 密文；phone_plain 为内部敏感原值，实体 select=false + JsonIgnore，仅专用 Mapper 与受控查看响应使用，禁止普通报表/小程序/H5/日志读取。
  - 关联：客户手机号仅由 TypeHandler 单次加密

### 客户手机号继续加密落库
  - *类型:risk*
  - 客户表 phone 使用 AES 类型处理器落库，phone_hash 用于等值查询；页面只返回脱敏值。
  - 关联：客户手机号受控查看、客户手机号内部原值列

### 客户服务菜单角色边界
  - *类型:risk*
  - /service-operations授权给ADVISER、DEPT_MANAGER、BOSS、OPERATOR、SUPER_ADMIN、SUPER；CHANNEL必须排除。
  - 关联：远程角色登录账号种子

### 小程序手机号一键登录缺失
  - *类型:risk*
  - 当前没有getPhoneNumber组件、phonenumber.getPhoneNumber服务调用或手机号绑定；现有“微信一键登录”是openid账号登录，不是手机号快速验证。

### 工作台命中缓存仍有秒级延迟
  - *类型:risk*
  - 真实请求中 refresh 回源约 4.0-6.1 秒，随后缓存读取仍约 1.6-5.1 秒；缓存键设计正确，剩余慢点可能在认证会话、全局切面或运行时资源竞争，需独立剖析。
  - 关联：工作台权限范围缓存验收

### 工单手机号脱敏出参
  - *类型:risk*
  - 服务工单后端批量关联客户档案并返回 contactPhoneMasked，避免前端列表缺少联系方式。
  - 关联：客户页面联系方式列

### 开发环境第三方接口 Mock 状态
  - *类型:risk*
  - Nacos dev 配置中 wechat.mock-enabled=true、loan.oss.mode=local、loan.oss.cos.enabled=false、OCR provider 默认 mock 且云密钥为空；当前不会调用真实微信、云 OSS 或 AI 识别。
  - 关联：项目免费 API 盘点、微信接口费用边界、OCR/VLM 费用边界、短信验证码当前模拟通道

### 微信接口费用边界
  - *类型:risk*
  - code2session、getuserphonenumber、公众号 token/ticket 均为微信官方接口，通常无单次调用费但需注册应用并受平台额度/规则限制；mock 开关开启时仅本地模拟，不外发用户数据。
  - 关联：开发环境第三方接口 Mock 状态

### 手机号先解密后脱敏
  - *类型:risk*
  - 所有客户、线索、报告和审计 DTO 必须先 AES 解密，再输出前三位+四星+后四位；禁止对 AES 密文本身切片。
  - 关联：联系方式申请查看

### 手机号列表统一脱敏
  - *类型:risk*
  - 客户、线索、预约等列表仅返回前三位加四星号加后四位；明文仅通过受控敏感查看服务读取。
  - 关联：业务ID展示边界、客户页面联系方式列

### 手机号额度员工行锁
  - *类型:risk*
  - SensitiveViewService 在客户和额度读取前按 staff_code 获取 SELECT FOR UPDATE 员工行锁，事务内串行化首次客户解锁的计数与留痕，防止并发超过30次。
  - 关联：手机号额度并发绕过风险

### 手机号额度并发绕过风险
  - *类型:risk*
  - 客户手机号查看以先计数后插入实现，日志表缺少用户+日期+客户唯一约束；多个不同客户并发请求可同时越过30次阈值。
  - 关联：2026-09-28 构建测试基线、手机号额度员工行锁

### 手机号额度按当日不同客户去重计数
  - *类型:risk*
  - 额度统计使用 COUNT(DISTINCT client_code)，并在是否已查看判断中带 view_date；禁止使用永久授权记录判断当天是否应扣次，否则历史授权会导致剩余次数长期不变。
  - 关联：手机号额度按每日首次客户解锁统计

### 报告授权声明证据缺口
  - *类型:risk*
  - 数据来源声明当前为固定文案，返回前未核验报告对应的授权记录、范围和材料归属。
  - 关联：Loan系统反方综合审计

### 接口权限默认fail-closed
  - *类型:risk*
  - loan.apiperm.strict默认改为true，未登记接口或角色未授权不再自动放行；测试放宽需显式隔离配置。
  - 关联：Loan系统反方综合审计

### 敏感手机号查看留痕与日限额
  - *类型:risk*
  - t_sensitive_view_log 记录员工、客户、日期和时间；每日上限30次，已授权客户再次查看不重复消耗额度。
  - 关联：客户手机号受控查看、敏感查看超限通知、手机号查看额度外审批

### 敏感查看只约束待审批唯一性
  - *类型:risk*
  - 超限审批唯一约束必须只覆盖 PENDING；使用生成列 pending_unique_key，使历史 APPROVED/REJECTED 不阻止同客户同员工同日再次发起。
  - 关联：手机号超限分级审批

### 数据库无物理外键
  - *类型:risk*
  - information_schema.KEY_COLUMN_USAGE未发现REFERENCED_TABLE_NAME记录，当前表间关系全部是应用层逻辑关联。
  - 关联：服务运营业务编码关联、旧自增ID逻辑绑定仍存在

### 文档门禁与本地大脑冲突
  - *类型:risk*
  - docs/local-brain 纳入版本控制后被 check-kb-consistency 全量扫描，历史编号和跨项目链接导致文档检查稳定失败。
  - 关联：2026-09-28 构建测试基线、文档门禁隔离本地大脑快照

### 旧自增ID逻辑绑定仍存在
  - *类型:risk*
  - 客户授权/个人认证、邀请码、匹配链路、规则计划、产品准入、渠道、材料日志、OCR、会员等表仍保留*_id自增主键关联，业务ID迁移尚未完成。
  - 关联：数据库无物理外键

### 暗色表格固定列禁止背景过渡
  - *类型:risk*
  - Element Plus 固定列的 background-color 250ms 过渡会在主题切换时短暂保留浅色背景并产生叠影；固定左右列应 transition:none 且使用不透明主题背景。
  - 关联：Web 子菜单以 route.name 划分页面实例

### 服务台角色数据范围
  - *类型:risk*
  - 顾问仅本人，部门经理仅本部门，老板运营超管全公司；预约、外出、待回访、工单四类名单复用同一范围策略。
  - 关联：待回访业务定义

### 服务直连JWT拒绝
  - *类型:risk*
  - 非公开接口在loan-service过滤器无有效会话直接返回401，防止绕过网关直连业务端口。
  - 关联：Loan系统反方综合审计

### 未提交改动需保留
  - *类型:risk*
  - 工作区存在大量与本次服务运营提交无关的未提交修改，上传时不得自动纳入。
  - 关联：服务运营链路提交

### 未登录接口边界验证
  - *类型:risk*
  - 4个公开接口返回200，112个受保护Web接口无token统一返回401，写接口未触发业务写入。
  - 关联：菜单接口契约检查

### 本地OSS仅显式模式
  - *类型:risk*
  - LocalOss仅在Nacos明确loan.oss.mode=local时装配，缺失或非法配置不再静默写入./uploads。
  - 关联：基础设施仅Nacos红线

### 本地基础设施红线遗留
  - *类型:risk*
  - docker-compose.yml 仍提供本地 MySQL/Redis/Nacos 和可用默认凭证，与本机仅运行应用、基础设施从 Nacos 读取的项目红线冲突。
  - 关联：2026-09-28 构建测试基线

### 画像维度隐藏内部编号
  - *类型:risk*
  - 画像维度摘要不展示 reportNo、snapshotNo、submissionNo、clientCode、staffCode 等内部编号；仅明确列入中文字段白名单的业务维度可呈现。
  - 关联：业务ID禁止作为页面展示值

### 登录提交幂等锁
  - *类型:risk*
  - 登录表单统一由 submit 触发，按钮与回车不能重复绑定；submitting 锁阻止并发请求和重复成功提示。

### 真实联动受登录会话阻塞
  - *类型:risk*
  - H5已恢复并返回200，但真实角色登录需有效验证码会话；不绕过验证码、不读取Redis、不伪造会话。
  - 关联：工作台跳转保留查询条件、H5字母数字验证码输入

### 短信表模板编码幂等迁移
  - *类型:risk*
  - 通过当前 Nacos 数据源执行 db/migrate-sms-record-template-code-2026-09-23.sql，已完成 1/1；未启动本地数据库或 Docker。
  - 关联：短信记录使用模板业务编码

### 策略计划级联删除审查
  - *类型:risk*
  - loan 策略删除在事务内删除计划、模块、步骤；仍需防止直接计划删除绕过策略引用检查，建议补充引用校验和集成测试。
  - 关联：mds AB关闭级联清理修复

### 线索写操作归属边界
  - *类型:risk*
  - 线索档案和跟进必须校验 owner_staff_code 等于当前员工；UPDATE 同时带归属条件防止并发转移后的越权写入。
  - 关联：我的线索操作闭环

### 经营分析 ECharts 数据提供器异常
  - *类型:risk*
  - 经营概览在 ECharts 初始化时报 Invalid data provider；接口请求成功，需统一依赖实例并批量验证图表路由。
  - 关联：经营概览图表重绘

### 经营分析提示文案
  - *类型:risk*
  - 匹配任务提示改为补充经营事实并生成经营分析，明确不展示或承诺银行准入结果。

### 网关上游 9080
  - *类型:risk*
  - 网关转发 /loan/api/* 到 http://127.0.0.1:9080；后端未监听时会产生 Connection refused 和 500。
  - 关联：loan 服务端口 9xxx 统一、后端接口验证

### 网关权限拒绝延迟执行
  - *类型:risk*
  - switchIfEmpty 必须使用 Mono.defer 包裹 reject；否则合法请求组装链时会提前写入403日志，造成大量权限认证接口失败假象。
  - 关联：Web 页面异常壳

### 网关重复写响应风险
  - *类型:risk*
  - 历史日志出现响应已提交后跳过二次写响应，需运行态复现并保证过滤器单次写响应。
  - 关联：全量回归测试基线

### 网关鉴权规则内部接口兜底
  - *类型:risk*
  - Redis 读取鉴权规则失败时改由 loan-service /loan/internal/api-perm/rules 拉取，不再立即返回空规则造成全量 403；成功结果进入进程内缓存。
  - 关联：网关拒绝响应提交保护、网关启动前注入Nacos参数

### 网关链路响应头只写一次
  - *类型:risk*
  - ApiAuthGlobalFilter 不在 beforeCommit 阶段重复修改响应头，避免响应提交后 ReadOnlyHttpHeaders.set 触发 UnsupportedOperationException。
  - 关联：Web菜单二级三级层级区分

### 联系方式申请查看
  - *类型:risk*
  - 客户和线索联系方式默认脱敏；申请查看通过受控接口、额度和审批链临时展示原值。
  - 关联：手机号先解密后脱敏

### 自动化回归结果
  - *类型:risk*
  - 后端428通过1跳过；Web29通过；小程序25通过；Web/H5/微信小程序构建成功。
  - 关联：全量回归测试基线

### 菜单父级自动补齐
  - *类型:risk*
  - OrgService 构建角色菜单树时自动补齐叶子菜单祖先，避免角色只授权叶子导致父分组缺失、页面不显示。
  - 关联：Web 菜单产品视角重构

### 角色联动测试必须使用真实会话
  - *类型:risk*
  - 无会话只能验证公开接口和401边界；禁止注入token、伪造JWT或关闭鉴权代替真实角色登录。

### 跟进统计慢查询风险
  - *类型:risk*
  - 历史日志显示t_client_follow_record部门范围COUNT约1.269秒，需只读EXPLAIN与索引验证。
  - 关联：全量回归测试基线

### 迁移必须读取所选 Nacos 数据源
  - *类型:risk*
  - apply-sql-nacos.py 强制读取 NACOS_SERVER_ADDR/NACOS_NAMESPACE 与 --confirm-remote；本轮按用户明确确认使用 Nacos 中的测试密码完成迁移。
  - 关联：员工外出审核迁移状态、合作产品业务编码、业务编码迁移已落库

### 迁移执行追踪缺口
  - *类型:risk*
  - 当前数据库未发现统一迁移历史表，SQL是否执行需按information_schema表结构、列、索引和菜单权限结果核验；不能仅依据文件存在判断。
  - 关联：服务工单业务编码表结构

### 远程库需执行password迁移
  - *类型:risk*
  - 本机loan_db已有t_staff.password，但运行进程连接远程prd库；远程数据库管理员必须在110.42.219.5:9306/loan_db执行t_staff.password迁移后重启服务。
  - 关联：prd Nacos远程数据源

### 远端链路验证边界
  - *类型:risk*
  - 远端测试记录 outing_general_test_20260924 保持 PENDING_REVIEW；未启动本机服务，不绕过服务层伪造审批/打卡。
  - 关联：员工外出审核链路

### 顾问预约客户范围实测
  - *类型:risk*
  - 真实页面验证顾问创建预约的客户下拉只返回owner_staff_code等于本人工号的客户；未分配客户不会出现在顾问下拉。
  - 关联：首条真实客户预约

### 预约确认语义冲突
  - *类型:risk*
  - 客户主动预约同时处于REQUESTED和客户已确认状态，三端对下一责任人表达不一致。
  - 关联：Loan系统反方综合审计、员工代客预约创建即确认 · 

