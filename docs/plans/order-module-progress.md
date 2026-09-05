# 阶段二开发进度归档：① 服务工单（order）模块

> 2026-08-26 ｜ 完成内容：后端 order + 薄 ClientProfile + 前端 order 页面 + 路由菜单 ｜ 状态：✅ 编译/构建通过

## 一、本期完成范围

### 1. 补充数据库迁移（追加至 `db/migrate-bizid-fk.sql` 第 7/8 节，已执行）
- `t_service_order`：`client_profile_id/owner_staff_id/bank_product_id`（BIGINT）→ `client_profile_code/owner_staff_code/bank_product_code`（VARCHAR），索引同步重建
- `t_client_profile`：`owner_staff_id/lead_id`（BIGINT）→ `owner_staff_code/lead_no`（VARCHAR）
- 两表执行时均为 0 行，无数据风险；全部执行成功

### 2. 后端：服务工单（`com.loan.order`）
| 文件 | 说明 |
|------|------|
| `entity/ServiceOrder` | 实体 + 状态机常量（NEW→IN_SERVICE→DEAL/CANCEL，DEAL→REFUND）+ 来源常量 |
| `mapper/ServiceOrderMapper` | MyBatis-Plus BaseMapper |
| `dto/OrderCreateReq` | 建单请求（clientCode/customerGroup 必填，业务编码入参） |
| `dto/OrderStatusReq` | 状态流转请求（status + dealAmount/dealTime） |
| `service/OrderService` | create（谁建单归谁）/ page（老板/主管看全部，顾问看自己的）/ detail（含客户名/产品名）/ updateStatus（状态机校验，DEAL 必填成交金额，rewardSettledFlag=0 待奖励结算） |
| `controller/OrderController` | POST `/api/admin/order`、GET `/page`、GET `/{orderNo}`、PUT `/{orderNo}/status` |

### 3. 后端：薄 ClientProfile 支撑切片（`com.loan.client`）
- `entity/ClientProfile` / `mapper/ClientProfileMapper`（全字段实体，业务编码字段）
- `service/ClientService.pageLite`：关键字匹配 client_code/联系人/企业名/手机号（phone_hash 精确），手机号脱敏
- `controller/ClientController`：GET `/api/admin/client/page-lite`（建单客户下拉）

### 4. 前端：order 模块
- `src/api/order.js`：pageOrders/createOrder/orderDetail/updateOrderStatus/pageClientLite
- `src/views/order/OrderList.vue`：列表（状态/关键字/仅我的筛选）+ 新建工单弹窗（客户远程搜索下拉/产品下拉/客群/备注）+ 状态操作（开始服务/成交弹窗填金额/取消/退款冲正）+ 详情抽屉
- 路由 `/order`（`src/router/index.js`）+ 侧栏菜单「服务工单」（`Layout.vue`）

### 5. 前端 Wave 1 残留同步修复（联调断裂点）
| 文件 | 修复 |
|------|------|
| `src/api/lead.js` | claimLead(leadNo) / assignLead(leadNo, toStaffCode) |
| `src/api/product.js` / `src/api/rule.js` | deleteProduct(productCode) / deleteRule(ruleCode) |
| `ProductList.vue` | onDelete 传 productCode、onEdit 去 id |
| `RuleList.vue` | row-key/batch/delete 改 ruleCode |
| `LeadPool.vue` | 认领传 leadNo、指派选人用 staffCode |
| `OrgCenter.vue` | 员工筛选 deptId→deptCode、部门树点击用 data.code |

## 二、验证

- 后端：`mvn -pl loan-service -am compile -DskipTests`（JDK 1.8.0_202）✅
- 前端：`vite build` ✅（OrderList 12.2 kB 正常产出）
- 数据库：migrate-bizid-fk.sql 全部语句执行成功（8 节 28+9 条）

## 三、遗留 / 后续

- 奖励结算（reward）：DEAL 仅置 `rewardSettledFlag=0`，结算逻辑在阶段二 ② 资料审批/奖励 实现
- 凭证附件（voucherAttachmentId）：线下补录必填校验暂未启用（附件上传接口后续迭代）
- CRM_WRITEBACK 回写建单：外部接入时实现
- 列表「仅我的工单」依赖角色：BOSS/DEPT_MANAGER 默认看全部，可勾选仅我的
