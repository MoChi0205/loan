# B 组数据模型缺口实施方案（2026-08-30）

> 承接《小程序H5落地清单-2026-08-30》剩余项 4：`t_screening_product` 明细表、诊断算法、统一审批中心通知、`staffName` 接 `t_staff`。
> 依据：结论 C4（报告命中产品可见性）、C5（经营诊断）、C2（归属流转）；方案评审定稿纪要（68 表 schema 基线）。

## 现状核查结论

| 缺口 | 实测现状 |
|------|---------|
| B1 命中产品明细 | 引擎 `MatchResultVO.products`（ProductMatchVO：productCode/productName/totalResult/modules）**仅在内存用于计数**，`t_client_screening` 只存 product_count/bank_count 汇总，**无明细表**；`MiniMatchService.reportProducts` 返回空列表（TODO） |
| B2 诊断算法 | `reportDiagnosis` 返回五块空骨架（TODO）；数据源可用：`t_client_submission.data_json`（facts 经营事实）+ `t_client_screening`（匹配结果）+ `t_bank_product`（无，对客脱敏） |
| B3 无归宿分配审批 | `MiniClientService.claim` 无归宿分支只写 `t_lead_allocation_record` 流水（CLAIM_APPLY），**无真实审批单**（TODO「接入统一审批中心」，但 approval 包仅产品/附件下载审批，无统一中心） |
| B4 staffName | `MiniMatchService.staffName()` / `MiniClientService.staffName()` 均直接返回工号（TODO）；`t_staff` 表 + `Staff` 实体 + `StaffMapper` 已存在 |

## B1：t_screening_product 明细表 + reportProducts 真实查询

### 建表（追加到 loan-db-schema.sql）
```sql
DROP TABLE IF EXISTS `t_screening_product`;
CREATE TABLE `t_screening_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_no` varchar(64) NOT NULL COMMENT '报告编号(业务ID:report+32位随机)',
  `bank_product_id` bigint NOT NULL COMMENT '银行产品ID(t_bank_product)',
  `product_code` varchar(64) NOT NULL COMMENT '产品编码(内部代号化)',
  `hit_result` varchar(24) NOT NULL COMMENT '命中结果(PASS/CONDITION/REJECT)',
  `match_score` int NOT NULL DEFAULT '0' COMMENT '匹配度0-100(落库时按模块命中率计算)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_product` (`report_no`,`product_code`),
  KEY `idx_report` (`report_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报告命中产品明细(员工陪访可见,客户永不展示产品名)';
```

### 落库（ScreeningService.run，screening insert 之后、return 之前）
- 遍历 `vo.getProducts()`：reportNo + bankProductId(productId) + productCode + hitResult(totalResult) + matchScore。
- **matchScore 计算**：产品下所有 step 中 `stepResult=PASS` 占比 ×100（四舍五入）；无 step 时按 totalResult 兜底（PASS→90 / CONDITION→70 / REJECT→40）。

### 查询（MiniMatchService.reportProducts 替换 TODO）
- `t_screening_product sp` → `t_bank_product bp`（product_name/amount_min/max/rate_min/max/term_min/max）→ `t_bank_channel bc`（bank_name）。
- 返回字段对齐前端 report/detail 命中产品 tab：
  `productCode / productName / bankName / hitResult / matchScore / amountRange("100万-500万") / rate("3.5%-4.2%") / term("12-36个月")`。
- 金额单位：元 → 万（`amount/10000` 保留 0-2 位小数）；复用 `MiniProductService.rangeText` 同款格式化逻辑（本服务内实现，不跨服务依赖）。

## B2：经营诊断算法（reportDiagnosis 替换骨架）

数据源：`t_client_submission.data_json`（facts：annualTaxAmount 年纳税 / annualInvoiceAmount 年开票 / foundYears 成立年限 / industry 行业）+ `t_client_screening`（grade/pass_count 等）。

五块生成规则（**合规：话术规避承诺性表述**，对齐评审决策 08-28 与 advice_json 口径）：

1. **kpi**（4 项，`{label, value, color, desc}`）：
   - 年纳税额（元）、年开票额（元）、成立年限（年）、命中产品数（款）。
   - value 取 facts/报告的原始值；color 用语义色（纳税/开票高→success，低→warning）。
2. **suggestions**（2-4 条，`{type, tagType, content}`）：按条件触发模板文案
   - 成立年限 < 2 年 → 「经营时长建议」（info）；纳税额低 → 「纳税数据建议」；开票额 > 纳税额 ×8 → 「票据管理建议」；命中产品 ≥3 → 「多产品比价建议」；否则 → 通用经营规范建议（success）。
3. **risks**（1-3 条，`{level, content}`，level: 中/高）：
   - 纳税额 < 1 万 → 高「纳税规模风险」；成立年限 < 1 年 → 高「经营时长风险」；开票波动（无历年数据时以「当年开票/纳税比异常」提示）→ 中；否则 → 中「资料完备性提示」。
4. **yearData**：无历年数据源 → 返回**当年 1 行**（year=当前年，revenue=开票额、tax=纳税额、invoice=开票额、profit=空显「—」），并保留「仅当年度数据」语义（前端表格照常渲染）。
5. **dimensions**（5 项，`{name, value(0-100), industryAvg}`）：
   - 纳税强度 / 开票规模 / 经营时长 / 财务健康 / 综合匹配（value 由 facts 归一化：如纳税 ≥50 万→100、≥10 万→80、≥1 万→60、>0→40、0→20；industryAvg 预设行业均值占位）。
   - 说明：industryAvg 为预设行业基准（行业数据接入前用常量，文档标注）。

## B3：无归宿分配审批（C2 闭环）

### 建表
```sql
DROP TABLE IF EXISTS `t_client_allocation_approval`;
CREATE TABLE `t_client_allocation_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `approval_no` varchar(64) NOT NULL COMMENT '审批单号(业务ID:alloc+32位随机)',
  `client_code` varchar(64) NOT NULL COMMENT '客户编码',
  `applicant_staff_code` varchar(32) NOT NULL COMMENT '申请人(员工工号)',
  `approve_status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING待审/APPROVED通过/REJECTED驳回)',
  `approver_staff_code` varchar(32) DEFAULT NULL COMMENT '审批人(运营/超管)',
  `approve_opinion` varchar(500) DEFAULT NULL COMMENT '审批意见(驳回必填)',
  `approved_at` datetime DEFAULT NULL COMMENT '审批完成时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_approval_no` (`approval_no`),
  KEY `idx_client` (`client_code`),
  KEY `idx_status` (`approve_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户归属分配审批单(无归宿客户申请分配,运营/超管审批后归属流转)';
```

### 改造
- `MiniClientService.claim` 无归宿分支：幂等查已有 PENDING 审批单（有则复用），否则落审批单 + 返回**真实 approvalNo**（替换原 `claimResult("PENDING_APPROVAL", clientCode)` 的伪单号）。
- `claimStatus`：改读审批单状态（PENDING/APPROVED/REJECTED + rejectReason），APPROVED 且 owner 已流转 → APPROVED。
- **新增审批接口**（MiniClientController，`/api/mini/client/allocation-approvals`）：
  - `GET /pending`：运营/超管待审列表（`@CurrentUser` 校验 userType=STAFF 且 roleCode ∈ OPERATOR/SUPER_ADMIN/BOSS）。
  - `POST /{approvalNo}/approve`：通过 → 客户 owner 流转给申请人 + 记录 allocation record（AUTO_CLAIM）+ 单号更新 APPROVED。
  - `POST /{approvalNo}/reject`：驳回（opinion 必填）→ REJECTED。
- 幂等：同一客户只允许 1 条 PENDING（申请时查重，已有则返回现有单号）。

## B4：staffName 接 t_staff

- 注入 `StaffMapper`；`staffName(staffCode)`：按 staff_code 查 t_staff 取 staff_name；查不到返回工号兜底。
- **防 N+1**：`allReports` 批量场景——先收集行内所有 ownerStaffCode 一次 IN 查询构建 Map（方法内缓存）；`staffName` 单查接口保留单值查询。
- 同步修复 `MiniClientService.staffName`（search 返回归属人姓名）。

## 验证计划
1. `mvn -q -pl loan-service -am compile -DskipTests` 编译通过
2. 前端不动（report/detail 字段契约已对齐；match 查重返回 ownerStaffName 名称化）
3. 单元回归：`mvn -q -pl loan-service test -Dtest=Mini*`（如有既有测试）
4. 文档同步：落地清单 §二十二、结论沉淀 C19、MEMORY

## 影响面
- 新增 2 张表（t_screening_product / t_client_allocation_approval）+ 实体 + Mapper
- 修改：ScreeningService（落库）、MiniMatchService（reportProducts/reportDiagnosis/staffName）、MiniClientService（claim/claimStatus/staffName）、MiniClientController（审批 3 接口）
- 不触碰：引擎匹配核心、现有审批（产品/附件下载）不动
