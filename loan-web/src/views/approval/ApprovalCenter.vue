<template>
  <div class="approval-page">
    <div class="loan-page-header">
      <div>
        <h2 class="loan-page-title">{{ approvalPageCopy.title }}</h2>
        <p class="loan-page-subtitle">{{ approvalPageCopy.subtitle }}</p>
      </div>
      <div class="header-actions">
        <el-button v-if="canCreateAnyApplication && activeTab === 'mine'" type="primary" @click="openApplicationChooser"><AppIcon name="plus" :size="15" /> 新增申请</el-button>
      </div>
    </div>

    <AppDialog v-model:visible="applicationChooserVisible" title="选择申请类型" width="520px" :show-footer="false">
      <div class="application-chooser">
        <button v-if="canApplyDownload" type="button" class="application-choice" @click="openDownloadApplication"><AppIcon name="download" :size="22" /><span><strong>资料下载申请</strong><small>申请无水印资料下载并查看审批进度</small></span><span>→</span></button>
        <button v-if="canCreateAppointment" type="button" class="application-choice" @click="goCreateAppointment"><AppIcon name="clock" :size="22" /><span><strong>客户预约</strong><small>进入预约页面创建客户服务安排</small></span><span>→</span></button>
        <button v-if="canCreateOuting" type="button" class="application-choice" @click="goCreateOuting"><AppIcon name="lead" :size="22" /><span><strong>员工外出申请</strong><small>进入外出页面填写计划并提交审批</small></span><span>→</span></button>
        <button type="button" class="application-choice" @click="goCreateProduct"><AppIcon name="product" :size="22" /><span><strong>产品提交审核</strong><small>新增产品并提交入全量库审批</small></span><span>→</span></button>
        <button type="button" class="application-choice" @click="goClientSea"><AppIcon name="org" :size="22" /><span><strong>客户认领 / 转移</strong><small>进入客户公海认领客户并发起归属流转</small></span><span>→</span></button>
      </div>
    </AppDialog>

    <div v-show="activeTab === 'mine'" class="loan-card">
      <div class="mine-type-tabs" role="tablist" aria-label="申请类型">
        <button v-for="t in mineTypeTabs" :key="t.value" type="button" class="mine-type-tab" :class="{ active: mineQuery.type === t.value }" role="tab" :aria-selected="mineQuery.type === t.value ? 'true' : 'false'" @click="selectMineType(t.value)">{{ t.label }}</button>
      </div>
      <AppSearchBar :loading="mineLoading" @search="searchMine" @reset="resetMine">
        <el-select v-model="mineQuery.status" placeholder="申请状态" clearable style="width: 130px">
          <el-option v-for="(label, value) in statusText" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-if="mineQuery.type === 'SENSITIVE_VIEW'" v-model="mineQuery.approvalStage" placeholder="审批层级" clearable style="width: 150px">
          <el-option label="部门经理审批" value="MANAGER_REVIEW" />
          <el-option label="老板/超管审批" value="BOSS_REVIEW" />
        </el-select>
        <AppDateRange v-model="mineQuery.dateRange" start-placeholder="提交开始日期" end-placeholder="提交结束日期" width="260px" />
        <el-input v-model="mineQuery.keyword" placeholder="申请事项 / 审批意见" clearable style="width: 220px" @keyup.enter="searchMine" />
      </AppSearchBar>
      <el-table :data="mineRows" v-loading="mineLoading" stripe row-key="approvalNo">
        <template #empty><AppEmpty title="暂无审批申请" desc="认领转移、产品新增或附件下载申请会显示在这里" /></template>
        <el-table-column label="审批类型" width="130"><template #default="{ row }">{{ typeText[row.type] || '其他申请' }}</template></el-table-column>
        <el-table-column label="申请事项" min-width="220"><template #default="{ row }">{{ row.subject || row.clientName || row.enterpriseName || '审批申请' }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><span class="loan-tag" :class="statusTag(row.approveStatus)">{{ statusText[row.approveStatus] || '状态待确认' }}</span></template></el-table-column>
        <el-table-column label="审批进度" width="150"><template #default="{ row }">{{ row.approvalStage === 'BOSS_REVIEW' ? '等待老板/超管终审' : (row.approveStatus === 'PENDING' ? '审批中' : '已完成') }}</template></el-table-column>
        <el-table-column label="审批意见" min-width="180"><template #default="{ row }">{{ row.opinion || '—' }}</template></el-table-column>
        <el-table-column label="提交时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="关联业务" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.targetPath" link type="primary" @click="openRelated(row)">查看详情</el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-show="activeTab === 'outing'" class="loan-card">
      <AppSearchBar :loading="outingLoading" @search="searchOutings" @reset="resetOutings">
        <el-input v-model="outingQuery.keyword" placeholder="申请人 / 客户名称" clearable style="width: 220px" @keyup.enter="searchOutings" />
        <AppDateRange v-model="outingDateRange" start-placeholder="外出开始日期" end-placeholder="外出结束日期" width="260px" />
      </AppSearchBar>
      <el-table :data="outingRows" v-loading="outingLoading" stripe row-key="outingNo">
        <template #empty><AppEmpty title="暂无待审批外出" desc="当前权限范围内没有待处理的外出申请" /></template>
        <el-table-column label="申请人" width="140"><template #default="{ row }">{{ row.staffName || '姓名待补充' }}</template></el-table-column>
        <el-table-column label="部门" width="150"><template #default="{ row }">{{ row.deptName || '未分部门' }}</template></el-table-column>
        <el-table-column label="客户" min-width="170"><template #default="{ row }">{{ row.customerName || '未关联客户' }}</template></el-table-column>
        <el-table-column label="外出时间" width="300"><template #default="{ row }">{{ formatDateTime(row.plannedStart) }} 至 {{ formatDateTime(row.plannedEnd) }}</template></el-table-column>
        <el-table-column prop="destination" label="目的地" min-width="180" show-overflow-tooltip />
        <el-table-column prop="purpose" label="事由" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right"><template #default="{ row }">
          <el-button link type="success" @click="onApproveOuting(row)">通过</el-button>
          <el-button link type="danger" @click="openOutingReject(row)">驳回</el-button>
        </template></el-table-column>
      </el-table>
      <AppPagination v-model:page="outingQuery.page" v-model:size="outingQuery.size" :total="outingTotal" @change="loadOutings" />
    </div>

    <div v-show="activeTab === 'sensitivePhone'" class="loan-card">
      <el-table :data="sensitiveRows" v-loading="sensitiveLoading" stripe row-key="approvalNo">
        <template #empty><AppEmpty title="暂无手机号查看审批" desc="超过查看额度的申请会显示在这里" /></template>
        <el-table-column label="申请人" width="130"><template #default="{ row }">{{ row.applicantName || '姓名待补充' }}</template></el-table-column>
        <el-table-column label="客户" min-width="180"><template #default="{ row }"><el-button link type="primary" @click="router.push({ path: '/client/my', query: { clientCode: row.clientCode } })">{{ row.clientName || '客户名称待补充' }}</el-button></template></el-table-column>
        <el-table-column label="审批级别" width="130"><template #default="{ row }">{{ row.approvalStage === 'BOSS_REVIEW' ? '老板审批' : '部门经理审批' }}</template></el-table-column>
        <el-table-column label="触发日期" prop="viewDate" width="130" />
        <el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button link type="success" @click="auditSensitive(row, true)">通过</el-button><el-button link type="danger" @click="auditSensitive(row, false)">驳回</el-button></template></el-table-column>
      </el-table>
    </div>

    <div v-for="kind in ['smsTemplate','reportTemplate']" :key="kind" v-show="activeTab === kind" class="loan-card">
      <el-table :data="contentRows[kind]" v-loading="contentLoading[kind]" stripe row-key="approvalNo">
        <template #empty><AppEmpty title="暂无待审核模板" desc="运营提交后将在此等待审核" /></template>
        <el-table-column label="审核事项" min-width="180"><template #default>模板发布审核</template></el-table-column>
        <el-table-column label="模板名称" prop="templateName" min-width="180" />
        <el-table-column label="版本" prop="versionNo" width="90" />
        <el-table-column label="提交人" prop="applicantName" width="140" />
        <el-table-column label="提交时间" prop="createdAt" width="180" sortable />
        <el-table-column label="操作" width="100"><template #default="{row}"><el-button v-permission="ACTION_PERMISSION.CHANNEL_CONTENT_AUDIT" type="success" link @click="openAudit(kind === 'smsTemplate' ? 'SMS_TEMPLATE' : 'REPORT_TEMPLATE', row)">审核</el-button></template></el-table-column>
      </el-table>
    </div>

    <!-- ============ 产品审核 ============ -->
    <div v-if="canAuditChannelContent" v-show="activeTab === 'product'" class="loan-card">
      <AppSearchBar :loading="loadingP" @search="searchP" @reset="resetP">
        <el-select v-model="queryP.status" placeholder="审核状态" clearable style="width: 140px">
          <el-option v-for="(t, k) in statusText" :key="k" :label="t" :value="k" />
        </el-select>
        <el-input v-model="queryP.keyword" placeholder="产品名称 / 工单号" style="width: 200px" clearable @keyup.enter="searchP" />
        <el-select v-model="queryP.submitDeptCode" placeholder="提交人团队" clearable filterable style="width: 160px">
          <el-option v-for="d in deptOptionsP" :key="d.deptCode" :label="d.deptName" :value="d.deptCode" />
        </el-select>
        <el-select v-model="queryP.submitStaffCode" placeholder="提交人" clearable filterable style="width: 150px">
          <el-option v-for="st in staffOptionsP" :key="st.staffCode" :label="st.staffName" :value="st.staffCode" />
        </el-select>
        <AppDateRange v-model="dateRangeP" start-placeholder="提交开始日期" end-placeholder="提交结束日期" width="260px" />
      </AppSearchBar>

      <AppTableState :error="errorP" @retry="loadP">
      <el-table :data="dataP" v-loading="loadingP" stripe row-key="approvalNo" @sort-change="handleSortChange">
        <template #empty>
          <AppEmpty title="暂无产品审核" desc="新产品申请入全量库后将在此等待审核" />
        </template>
        <el-table-column label="审核事项" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ approvalMatter(row, 'product') }}</template></el-table-column>
        <el-table-column label="产品" min-width="170">
          <template #default="{ row }">
            <div class="cell-main">{{ row.bankProductName || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="申请类型" width="100">
          <template #default="{ row }">
            <span class="loan-tag loan-tag-info">{{ row.applyType === 'CREATE' ? '新建' : row.applyType === 'DELETE' ? '下线删除' : '变更' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="申请理由" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.applyReason || '—' }}</template>
        </el-table-column>
        <el-table-column label="提交人" min-width="120">
          <template #default="{ row }">
            <div class="cell-main">{{ row.submitterStaffName || row.submitterStaffCode || '—' }}</div>
            <div class="cell-sub">{{ row.submitterStaffCode || '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="重复标记" width="90">
          <template #default="{ row }">
            <span v-if="row.duplicateFlag" class="loan-tag loan-tag-danger">疑似重复</span>
            <span v-else class="loan-tag loan-tag-muted">正常</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="loan-tag" :class="statusTag(row.approveStatus)">{{ enumLabel(statusText, row.approveStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="approverStaffName" label="审核人" width="120">
          <template #default="{ row }">{{ row.approverStaffName || (row.approveStatus === 'PENDING' ? '待审核' : '姓名待补充') }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" sortable>
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="productActions(row)" />
          </template>
        </el-table-column>
      </el-table>
      </AppTableState>
      <AppPagination v-if="!errorP" v-model:page="queryP.page" v-model:size="queryP.size" :total="totalP" @change="loadP" />
    </div>

    <!-- ============ 渠道新增线索终审（老板/超级管理员单级终审） ============ -->
    <div v-show="activeTab === 'channelLead'" class="loan-card">
      <AppSearchBar :loading="loadingCL" @search="searchCL" @reset="resetCL">
        <el-input v-model="queryCL.keyword" placeholder="联系人 / 手机号 / 渠道名称" style="width: 260px" clearable @keyup.enter="searchCL" />
        <el-select v-model="queryCL.leadType" placeholder="客群" clearable style="width: 120px"><el-option label="个人" value="PERSONAL" /><el-option label="企业" value="ENTERPRISE" /></el-select>
        <AppDateTime v-model="queryCL.dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="提交开始" end-placeholder="提交结束" width="260px" @update:model-value="syncChannelDateRange" />
      </AppSearchBar>
      <AppTableState :error="errorCL" @retry="loadCL">
        <el-table :data="dataCL" v-loading="loadingCL" stripe row-key="leadNo" style="height: calc(100vh - 320px); min-height: 360px">
          <template #empty><AppEmpty title="暂无渠道线索审核" desc="渠道录入新线索后将在此等待终审" /></template>
          <el-table-column prop="contactName" label="联系人" width="130" />
          <el-table-column label="手机号" width="140">
            <template #default="{ row }">{{ desensitizePhone(row.phone) }}</template>
          </el-table-column>
          <el-table-column prop="createdBy" label="创建人" min-width="150" />
          <el-table-column prop="leadType" label="客群" width="100"><template #default="{ row }">{{ row.leadType === 'PERSONAL' ? '个人' : '企业' }}</template></el-table-column>
          <el-table-column prop="createdAt" label="提交时间" width="170" sortable><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <AppTableActions :actions="[{ key: 'audit', label: '审核', type: 'success', onClick: () => openAudit('channelLead', { ...row, approvalNo: row.leadNo }) }]" />
            </template>
          </el-table-column>
        </el-table>
      </AppTableState>
      <AppPagination v-if="!errorCL" v-model:page="queryCL.page" v-model:size="queryCL.size" :total="totalCL" @change="loadCL" />
    </div>

    <!-- ============ 附件下载审核 ============ -->
    <div v-show="activeTab === 'download'" class="loan-card">
      <AppSearchBar :loading="loadingD" @search="searchD" @reset="resetD">
        <el-select v-model="queryD.status" placeholder="审核状态" clearable style="width: 140px">
          <el-option v-for="(t, k) in statusText" :key="k" :label="t" :value="k" />
        </el-select>
        <el-input v-model="queryD.keyword" placeholder="申请人姓名 / 用途说明" style="width: 220px" clearable @keyup.enter="searchD" />
        <template #append>
          <el-button v-permission="ACTION_PERMISSION.DOWNLOAD_APPLY" type="primary" plain @click="openApply">
            <AppIcon name="add" :size="14" />
            发起申请
          </el-button>
        </template>
      </AppSearchBar>

      <AppTableState :error="errorD" @retry="loadD">
      <el-table :data="dataD" v-loading="loadingD" stripe row-key="approvalNo" @sort-change="handleSortChangeD" style="height: calc(100vh - 320px); min-height: 360px">
        <template #empty>
          <AppEmpty title="暂无下载审核" desc="员工发起无水印下载申请后将在此审核" />
        </template>
        <el-table-column label="审核事项" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ approvalMatter(row, 'download') }}</template></el-table-column>
        <el-table-column prop="applicantStaffName" label="申请人" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.applicantStaffName || '姓名待补充' }}</template>
        </el-table-column>
        <el-table-column prop="purpose" label="用途说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="客户与资料" min-width="260">
          <template #default="{ row }">
            <div v-for="item in (row.attachmentDetails || [])" :key="item.id" class="attachment-detail">
              <strong>{{ item.clientName || '客户信息缺失' }}</strong> · {{ item.fileName || '未命名附件' }}
            </div>
            <span v-if="!row.attachmentDetails?.length">{{ attachmentCount(row.attachmentIds) }} 份资料（历史数据未绑定明细）</span>
          </template>
        </el-table-column>
        <el-table-column label="期望期限" width="90">
          <template #default="{ row }">{{ row.expectDays ? row.expectDays + ' 天' : '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="loan-tag" :class="statusTag(row.approveStatus)">{{ enumLabel(statusText, row.approveStatus) }}</span>
            <span v-if="row.voidFlag" class="loan-tag loan-tag-muted" style="margin-left: 4px">已作废</span>
          </template>
        </el-table-column>
        <el-table-column label="限时链接" min-width="180">
          <template #default="{ row }">
            <span v-if="row.approveStatus === 'APPROVED' && !row.voidFlag" class="mono link-token">
              {{ row.linkExpireAt ? '24h 内有效 · 截止 ' + formatDateTime(row.linkExpireAt) : row.linkToken }}
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="downloadActions(row)" />
          </template>
        </el-table-column>
      </el-table>
      </AppTableState>
      <AppPagination v-if="!errorD" v-model:page="queryD.page" v-model:size="queryD.size" :total="totalD" @change="loadD" />
    </div>

    <!-- ============ 客户分配审核（无归宿客户归属流转，仅审核分配管理员可见） ============ -->
    <div v-show="activeTab === 'allocation'" class="loan-card">
      <AppSearchBar :loading="loadingA" @search="searchA" @reset="resetA">
        <el-input v-model="queryA.keyword" placeholder="企业名称 / 客户姓名 / 申请人" style="width: 240px" clearable @keyup.enter="searchA" />
        <el-button :disabled="!allocationSelection.length || batchAuditing" @click="onBatchAuditAllocation(true)">一键通过（{{ allocationSelection.length }}）</el-button>
        <el-button type="danger" :disabled="!allocationSelection.length || batchAuditing" @click="onBatchAuditAllocation(false)">一键驳回（{{ allocationSelection.length }}）</el-button>
      </AppSearchBar>

      <AppTableState :error="errorA" @retry="loadA">
      <el-table :data="dataA" v-loading="loadingA" stripe row-key="approvalNo" @selection-change="onAllocationSelectionChange" @sort-change="handleSortChangeA">
        <template #empty>
          <AppEmpty title="暂无分配审核" desc="客户申请归属流转、无归宿客户分配将在此等待审核" />
        </template>
        <el-table-column type="selection" width="44" :selectable="(row) => row.approveStatus === 'PENDING'" />
        <el-table-column label="审核事项" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ approvalMatter(row, 'allocation') }}</template></el-table-column>
        <el-table-column label="客户" min-width="180">
          <template #default="{ row }">
            <div class="cell-main">{{ row.entName || row.contactName || '未命名客户' }}</div>
            <div class="cell-sub">{{ row.customerGroup === 'PERSONAL' ? '个人客户' : '企业客户' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="申请人" min-width="140">
          <template #default="{ row }">
            <div class="cell-main">{{ row.applicantName || '姓名待补充' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="contactName" label="联系人" width="120" show-overflow-tooltip />
        <el-table-column label="联系电话" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ desensitizePhone(row.contactPhone) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" sortable>
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <AppTableActions :actions="allocationActions(row)" />
          </template>
        </el-table-column>
      </el-table>
      </AppTableState>
      <AppPagination v-if="!errorA" v-model:page="queryA.page" v-model:size="queryA.size" :total="totalA" @change="loadA" />
    </div>

    <!-- 通用审核弹窗（产品 / 下载 / 分配共用） -->
    <AppDialog v-model:visible="auditVisible" title="审核" :loading="auditing" @confirm="onAudit">
      <el-form ref="auditFormRef" :model="auditForm" :rules="auditRules" label-width="100px" label-position="right">
        <el-form-item label="审核事项">
          <span>{{ auditForm.matter }}</span>
        </el-form-item>
        <el-form-item label="审核结果" prop="approve">
          <el-radio-group v-model="auditForm.approve">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="意见" prop="opinion">
          <el-input v-model="auditForm.opinion" type="textarea" :rows="2" :placeholder="auditForm.approve ? '通过选填' : '驳回意见必填'" />
        </el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog v-model:visible="outingRejectVisible" title="驳回外出申请" width="480px" :loading="auditing" @confirm="submitOutingReject">
      <el-form label-width="80px"><el-form-item label="驳回原因" required>
        <el-input v-model="outingRejectReason" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请填写明确的驳回原因" />
      </el-form-item></el-form>
    </AppDialog>

    <!-- 下载申请弹窗 -->
    <AppDialog v-model:visible="applyVisible" title="发起无水印下载申请" width="640px" modal-class="loan-app-dialog download-apply-dialog" :loading="applying" @confirm="onApply">
      <el-form ref="applyFormRef" :model="applyForm" :rules="applyRules" label-width="110px" label-position="right">
        <el-form-item label="资料清单" prop="attachmentIds">
          <el-select v-model="applyForm.attachmentIds" multiple filterable remote :remote-method="searchAttachments" :loading="attachmentLoading" placeholder="输入文件名、资料类型或客户名称搜索" style="width: 100%" placement="bottom-start" :teleported="false" popper-class="attachment-select-popper" @visible-change="onAttachmentVisible">
            <el-option v-for="item in attachmentOptions" :key="item.value" :label="item.label" :value="item.value" />
            <div v-if="!attachmentFinished && attachmentOptions.length" class="remote-more" @mousedown.prevent @click="loadMoreAttachments">{{ attachmentLoading ? '加载中…' : '加载更多' }}</div>
          </el-select>
        </el-form-item>
        <el-form-item label="用途说明" prop="purpose">
          <el-input v-model="applyForm.purpose" type="textarea" :rows="2" placeholder="如 报送银行 / 纸质留存" />
        </el-form-item>
        <el-form-item label="期望期限" prop="expectDays">
          <el-input-number v-model="applyForm.expectDays" :min="1" :max="365" :controls="false" placeholder="天" style="width: 140px" />
        </el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
import { enumLabel } from '@/utils/enumDisplay';
defineOptions({ name: '_approval' });
import { ref, reactive, computed, watch , onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import AppSearchBar from '@/components/AppSearchBar.vue';
import AppDateRange from '@/components/AppDateRange.vue';
import AppDateTime from '@/components/AppDateTime.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppEmpty from '@/components/AppEmpty.vue';
import AppTableActions from '@/components/AppTableActions.vue';
import AppIcon from '@/components/AppIcon.vue';
import AppTableState from '@/components/AppTableState.vue';
import AppDialog from '@/components/AppDialog.vue';
import { useTable } from '@/composables/useTable';
import { useRemoteOptions } from '@/composables/useRemoteOptions';
import { formatDateTime, desensitizePhone } from '@/utils/format';
import { approvalMatter } from '@/utils/display';
import { useUserStore } from '@/store/user';
import { ACTION_PERMISSION, approvalActionState } from '@/utils/access';
import {
  pageProductApprovals, auditProductApproval,
  pageDownloadApprovals, auditDownloadApproval, voidDownloadApproval, applyDownload,
  pageAllocationApprovals, auditAllocationApproval, batchAuditAllocationApprovals,
  pageChannelLeadApprovals, auditChannelLeadApproval,
  pageContentApprovals, auditContentApproval,
  myApprovalApplications,
} from '@/api/approval';
import { appConfirm } from '@/utils/confirm';
import { pageAttachments } from '@/api/attachment';
import { pagePendingOutings, approveOuting, rejectOuting } from '@/api/serviceOperations';
import { pendingSensitiveViewApprovals, auditSensitiveViewApproval } from '@/api/sensitive';

const userStore = useUserStore();
const route = useRoute();
const router = useRouter();
/** D39/C24：DM 可审本团队分配，跨团队由后端拒绝并上收 BOSS。 */
const canAuditAllocation = computed(() => userStore.hasPerm(ACTION_PERMISSION.ALLOCATION_AUDIT));
const canAuditChannelContent = computed(() => userStore.hasPerm(ACTION_PERMISSION.CONTENT_AUDIT));
const canApplyDownload = computed(() => userStore.hasPerm(ACTION_PERMISSION.DOWNLOAD_APPLY));
const canCreateAppointment = computed(() => userStore.hasPerm(ACTION_PERMISSION.ORDER_CREATE));
const canCreateOuting = computed(() => userStore.hasPerm(ACTION_PERMISSION.ORDER_CREATE));
const canCreateAnyApplication = computed(() => canApplyDownload.value || canCreateAppointment.value || canCreateOuting.value);
const applicationChooserVisible = ref(false);
function openApplicationChooser() {
  // 在具体审批子页面直接进入对应表单；“我的申请”仍保留类型选择器。
  if (activeTab.value === 'download' && canApplyDownload.value) return openDownloadApplication();
  if (activeTab.value === 'outing' && canCreateOuting.value) return goCreateOuting();
  applicationChooserVisible.value = true;
}
function openDownloadApplication() { applicationChooserVisible.value = false; activeTab.value = 'download'; openApply(); }
function goCreateAppointment() { applicationChooserVisible.value = false; router.push({ path: '/service-operations/appointments', query: { create: '1' } }); }
function goCreateOuting() { applicationChooserVisible.value = false; router.push({ path: '/service-operations/outings', query: { create: '1' } }); }
function goCreateProduct() { applicationChooserVisible.value = false; router.push({ path: '/product/all', query: { create: '1' } }); }
function goClientSea() { applicationChooserVisible.value = false; router.push({ path: '/client/company-sea' }); }

const approvalView = String(route.path.split('/').pop() || 'mine');
const approvalViewMap = Object.freeze({ 'channel-lead': 'channelLead', 'sms-template': 'smsTemplate', 'report-template': 'reportTemplate', 'sensitive-phone': 'sensitivePhone' });
const requestedTab = approvalViewMap[approvalView] || String(approvalView || route.query.tab || 'mine');
const activeTab = ref(['mine', 'download', 'allocation', 'outing', 'sensitivePhone', 'product', 'channelLead', 'smsTemplate', 'reportTemplate'].includes(requestedTab) ? requestedTab : 'mine');
const approvalPageCopy = computed(() => ({
  // 命名澄清（2026-10-19）：「我的申请」易与审批人视角混淆——
  // 「我发起的」= 我提交给他人审批；各审批 tab 副标题统一带「待我审批」语义。
  mine: { title: '我发起的', subtitle: '我提交给他人审批的单子与当前进度（此处不能审批，需到对应审批 tab 处理）' },
  product: { title: '产品审核', subtitle: '待我审批：产品新增、变更、下线申请与重复风险标记' },
  download: { title: '附件下载审核', subtitle: '待我审批：员工发起的无水印资料下载申请' },
  allocation: { title: '客户分配审核', subtitle: '待我审批：客户认领与归属流转申请，支持勾选后一键批量审批' },
  outing: { title: '外出审批', subtitle: '待我审批：当前数据范围内的员工外出计划' },
  sensitivePhone: { title: '手机号查看审批', subtitle: '待我审批：顾问超出查看额度后的手机号解锁申请' },
  channelLead: { title: '渠道线索审核', subtitle: '待我审批：合作渠道提交的新线索' },
  smsTemplate: { title: '短信模板审核', subtitle: '待我审批：短信模板发布与变更申请' },
  reportTemplate: { title: '报告模板审核', subtitle: '待我审批：报告模板发布与变更申请' },
}[activeTab.value] || { title: '审批中心', subtitle: '处理当前数据范围内的审批事项' }));
function tabFromRoute() {
  const view = String(route.path.split('/').pop() || 'mine');
  return approvalViewMap[view] || (['mine', 'download', 'allocation', 'outing', 'sensitivePhone', 'product', 'channelLead', 'smsTemplate', 'reportTemplate'].includes(view) ? view : 'mine');
}
watch(() => route.fullPath, () => {
  const next = tabFromRoute();
  if (next !== activeTab.value) activeTab.value = next;
});
const loadedTabs = reactive({ mine: false, product: false, download: false, allocation: false, outing: false, sensitivePhone: false, channelLead: false, smsTemplate: false, reportTemplate: false });
const mineRows = ref([]); const mineLoading = ref(false);
const typeText = { PRODUCT: '产品审批', DOWNLOAD: '附件下载', ALLOCATION: '客户认领/转移', OUTING: '外出申请', MATERIAL_REVIEW: '材料复核', SENSITIVE_VIEW: '手机号查看', SMS_TEMPLATE: '短信模板', REPORT_TEMPLATE: '报告模板' };
const mineQuery = reactive({ type: '', status: '', approvalStage: '', dateRange: null, keyword: '' });
const mineTypeOptions = computed(() => {
  const role = userStore.roleCode;
  const exempt = ['BOSS', 'SUPER_ADMIN', 'SUPER'].includes(role);
  const contentManager = ['OPERATOR', 'BOSS', 'SUPER_ADMIN', 'SUPER'].includes(role);
  const values = ['PRODUCT', 'DOWNLOAD', 'ALLOCATION', 'OUTING', 'MATERIAL_REVIEW'];
  if (!exempt) values.push('SENSITIVE_VIEW');
  if (contentManager) values.push('SMS_TEMPLATE', 'REPORT_TEMPLATE');
  return values.map((value) => ({ value, label: typeText[value] }));
});
/** 类型子 tab：前置「全部」，点击即切换并刷新，无需再从下拉逐次选择。 */
const mineTypeTabs = computed(() => [{ value: '', label: '全部' }, ...mineTypeOptions.value]);
function selectMineType(type) {
  if (mineQuery.type === type) return;
  mineQuery.type = type;
  if (type !== 'SENSITIVE_VIEW') mineQuery.approvalStage = '';
  loadMine();
}
function mineParams() {
  const params = {
    type: mineQuery.type || undefined,
    status: mineQuery.status || undefined,
    approvalStage: mineQuery.type === 'SENSITIVE_VIEW' ? (mineQuery.approvalStage || undefined) : undefined,
    keyword: mineQuery.keyword.trim() || undefined,
  };
  if (Array.isArray(mineQuery.dateRange) && mineQuery.dateRange.length === 2) {
    [params.startDate, params.endDate] = mineQuery.dateRange;
  }
  return params;
}
async function loadMine() { mineLoading.value = true; try { const res = await myApprovalApplications(mineParams()); mineRows.value = res.data || []; } finally { mineLoading.value = false; } }
function searchMine() { loadMine(); }
function resetMine() {
  Object.assign(mineQuery, { type: '', status: '', approvalStage: '', dateRange: null, keyword: '' });
  loadMine();
}
function openRelated(row) {
  if (!row?.targetPath) return;
  router.push({ path: row.targetPath, query: row.targetQuery || undefined });
}
watch(() => mineQuery.type, (type) => { if (type !== 'SENSITIVE_VIEW') mineQuery.approvalStage = ''; });
const contentRows = reactive({ smsTemplate: [], reportTemplate: [] });
const contentLoading = reactive({ smsTemplate: false, reportTemplate: false });
async function loadContent(kind) {
  contentLoading[kind] = true;
  try { const type = kind === 'smsTemplate' ? 'SMS_TEMPLATE' : 'REPORT_TEMPLATE'; const res = await pageContentApprovals(type, { page: 1, size: 100 }); contentRows[kind] = res?.records || res?.data?.records || []; }
  finally { contentLoading[kind] = false; }
}
const statusText = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' };
const statusTag = (s) => ({ PENDING: 'loan-tag-warning', APPROVED: 'loan-tag-success', REJECTED: 'loan-tag-danger' }[s] || 'loan-tag-muted');
function attachmentCount(value) {
  try { return Array.isArray(value) ? value.length : JSON.parse(value || '[]').length; } catch { return 0; }
}

/** 产品审核：提交人/团队下拉数据 + 提交时间范围（与 queryP.startTime/endTime 双向同步） */
const deptOptionsP = ref([]);
const staffOptionsP = ref([]);
const dateRangeP = computed({
  get: () => (queryP.startTime && queryP.endTime ? [queryP.startTime, queryP.endTime] : []),
  set: (v) => {
    queryP.startTime = v?.[0] || '';
    queryP.endTime = v?.[1] || '';
  },
});
onMounted(async () => {
  try {
    const flat = [];
    const walk = (nodes, prefix) => (nodes || []).forEach((n) => {
      const label = prefix ? `${prefix} / ${n.name}` : n.name;
      flat.push({ deptCode: n.code, deptName: label });
      walk(n.children, label);
    });
    walk((await departmentTree()).data || [], '');
    deptOptionsP.value = flat;
  } catch { deptOptionsP.value = []; }
  try {
    staffOptionsP.value = (await staffPage({ page: 1, size: 200, status: 'ACTIVE' })).data?.records || [];
  } catch { staffOptionsP.value = []; }
});

/** 产品审核表 */
const { loading: loadingP, error: errorP, data: dataP, total: totalP, query: queryP, load: loadP, onSearch: searchP, onReset: resetP, handleSortChange } =
  useTable(pageProductApprovals, { status: '', keyword: '', submitStaffCode: '', submitDeptCode: '', startTime: '', endTime: '' });

/** 下载审核表 */
const { loading: loadingD, error: errorD, data: dataD, total: totalD, query: queryD, load: loadD, onSearch: searchD, onReset: resetD, handleSortChange: handleSortChangeD } =
  useTable(pageDownloadApprovals, { status: '', keyword: '' });

/** 客户分配审核表 */
const { loading: loadingA, error: errorA, data: dataA, total: totalA, query: queryA, load: loadA, onSearch: searchA, onReset: resetA, handleSortChange: handleSortChangeA } =
  useTable(pageAllocationApprovals, { keyword: '' });

/** 一键审批（批量，2026-10-19）：勾选后批量通过/驳回，逐条结果汇总展示。 */
const allocationSelection = ref([]);
const batchAuditing = ref(false);
function onAllocationSelectionChange(rows) { allocationSelection.value = rows || []; }
async function onBatchAuditAllocation(approve) {
  const nos = allocationSelection.value.map((r) => r.approvalNo).filter(Boolean);
  if (!nos.length) return;
  let opinion = '';
  if (approve) {
    try {
      await appConfirm(`确认一键通过选中的 ${nos.length} 条分配审批？跨团队单通过部门经理审核后仍需老板终审。`, '确认批量通过？', { confirmButtonText: '确认通过' });
    } catch (e) { return; }
  } else {
    try {
      const { value } = await ElMessageBox.prompt(`即将驳回选中的 ${nos.length} 条分配审批`, '批量驳回意见（必填）', {
        confirmButtonText: '确认驳回', cancelButtonText: '取消',
        inputPlaceholder: '请填写统一的驳回原因', inputValidator: (v) => !!(v && v.trim()) || '驳回原因必填',
      });
      opinion = value.trim();
    } catch (e) { return; }
  }
  batchAuditing.value = true;
  try {
    const res = (await batchAuditAllocationApprovals({ approvalNos: nos, approve, opinion: opinion || null })).data || {};
    const okCount = res.successCount ?? 0;
    const failCount = res.failCount ?? 0;
    const bossCount = (res.results || []).filter((r) => r.success && /老板终审/.test(r.message || '')).length;
    if (failCount) {
      const firstFail = (res.results || []).find((r) => !r.success);
      ElMessage.warning(`批量处理完成：成功 ${okCount} 条，失败 ${failCount} 条（如：${firstFail?.message || '未知原因'}）`);
    } else if (approve && bossCount) {
      ElMessage.success(`已通过 ${okCount} 条，其中 ${bossCount} 条为跨团队单，待老板终审`);
    } else {
      ElMessage.success(approve ? `已批量通过 ${okCount} 条` : `已批量驳回 ${okCount} 条`);
    }
    allocationSelection.value = [];
    loadA();
  } catch (e) { /* 拦截器已提示 */ } finally {
    batchAuditing.value = false;
  }
}

const { loading: loadingCL, error: errorCL, data: dataCL, total: totalCL, query: queryCL, load: loadCL, onSearch: searchCL, onReset: resetCL } =
  useTable(pageChannelLeadApprovals, { keyword: '', leadType: '', dateRange: [] });
function syncChannelDateRange(range) {
  queryCL.startDate = range?.[0] || '';
  queryCL.endDate = range?.[1] || '';
}

const outingRows = ref([]); const outingTotal = ref(0); const outingLoading = ref(false);
const sensitiveRows = ref([]); const sensitiveLoading = ref(false);
async function loadSensitiveApprovals() { sensitiveLoading.value = true; try { sensitiveRows.value = (await pendingSensitiveViewApprovals()).data || []; } finally { sensitiveLoading.value = false; } }
async function auditSensitive(row, approve) {
  let opinion = null;
  if (!approve) {
    const answer = await ElMessageBox.prompt('请填写驳回原因', '手机号查看审批', { inputValidator: (value) => !!value?.trim() || '驳回原因必填', confirmButtonText: '确认驳回', cancelButtonText: '取消' }).catch(() => null);
    if (!answer) return;
    opinion = answer.value.trim();
  }
  await auditSensitiveViewApproval(row.approvalNo, { approve, opinion });
  ElMessage.success(approve ? '审批已通过' : '审批已驳回');
  await loadSensitiveApprovals();
}
const outingQuery = reactive({ page: 1, size: 20, keyword: '', dateFrom: '', dateTo: '' });
const outingDateRange = computed({
  get: () => (outingQuery.dateFrom && outingQuery.dateTo ? [outingQuery.dateFrom, outingQuery.dateTo] : []),
  set: (v) => {
    outingQuery.dateFrom = v?.[0] || '';
    outingQuery.dateTo = v?.[1] || '';
  },
});
async function loadOutings() {
  outingLoading.value = true;
  try {
    const params = { page: outingQuery.page, size: outingQuery.size };
    if (outingQuery.keyword.trim()) params.keyword = outingQuery.keyword.trim();
    if (outingQuery.dateFrom) params.dateFrom = outingQuery.dateFrom;
    if (outingQuery.dateTo) params.dateTo = outingQuery.dateTo;
    const res = await pagePendingOutings(params);
    outingRows.value = res.data?.records || [];
    outingTotal.value = Number(res.data?.total || 0);
  } finally { outingLoading.value = false; }
}
function searchOutings() { outingQuery.page = 1; loadOutings(); }
function resetOutings() {
  Object.assign(outingQuery, { page: 1, size: 20, keyword: '', dateFrom: '', dateTo: '' });
  loadOutings();
}
async function onApproveOuting(row) {
  await approveOuting(row.outingNo, '审批通过');
  ElMessage.success('外出申请已通过');
  await loadOutings();
}
const outingRejectVisible = ref(false); const outingRejectTarget = ref(null); const outingRejectReason = ref('');
function openOutingReject(row) { outingRejectTarget.value = row; outingRejectReason.value = ''; outingRejectVisible.value = true; }
async function submitOutingReject() {
  if (!outingRejectReason.value.trim()) return ElMessage.warning('驳回原因必填');
  auditing.value = true;
  try {
    await rejectOuting(outingRejectTarget.value.outingNo, outingRejectReason.value.trim());
    outingRejectVisible.value = false;
    ElMessage.success('外出申请已驳回');
    await loadOutings();
  } finally { auditing.value = false; }
}

function productActions(row) {
  const actions = [];
  if (approvalActionState('product', row, userStore.permissions).canAudit) {
    actions.push({ key: 'audit', label: '审核', type: 'success', onClick: () => openAudit('product', row) });
  }
  return actions;
}

function downloadActions(row) {
  const actions = [];
  const state = approvalActionState('download', row, userStore.permissions);
  if (state.canAudit) {
    actions.push({ key: 'audit', label: '审核', type: 'success', onClick: () => openAudit('download', row) });
  }
  if (state.canVoid) {
    actions.push({
      key: 'void',
      label: '作废',
      type: 'danger',
      confirm: `确认作废「${approvalMatter(row, 'download')}」？`,
      onClick: () => onVoid(row),
    });
  }
  return actions;
}

function allocationActions(row) {
  const actions = [];
  // 待审列表仅返回 PENDING 记录，故统一展示审核入口
  if (approvalActionState('allocation', row, userStore.permissions).canAudit) {
    actions.push({ key: 'audit', label: '审核', type: 'success', onClick: () => openAudit('allocation', row) });
  }
  return actions;
}

// ============================================================
// 审核
// ============================================================
const auditVisible = ref(false);
const auditing = ref(false);
const auditForm = reactive({ kind: 'product', approvalNo: '', matter: '', approve: true, opinion: '' });
const auditFormRef = ref();
const auditRules = {
  opinion: [
    {
      validator: (rule, value, callback) => {
        if (!auditForm.approve && !(value || '').trim()) {
          callback(new Error('驳回意见必填'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
};

function openAudit(kind, row) {
  auditForm.kind = kind;
  auditForm.approvalNo = row.approvalNo;
  auditForm.matter = approvalMatter(row, kind);
  auditForm.approve = true;
  auditForm.opinion = '';
  auditFormRef.value?.clearValidate();
  auditVisible.value = true;
}

async function onAudit() {
  try {
    await auditFormRef.value.validate();
  } catch (e) {
    return;
  }
  auditing.value = true;
  try {
    const payload = { approve: auditForm.approve, opinion: auditForm.opinion.trim() || null };
    if (auditForm.kind === 'product') {
      await auditProductApproval(auditForm.approvalNo, payload);
      ElMessage.success(auditForm.approve ? '已通过，产品已进入全量库' : '已驳回');
      loadP();
    } else if (auditForm.kind === 'allocation') {
      await auditAllocationApproval(auditForm.approvalNo, payload);
      ElMessage.success(auditForm.approve ? '已通过，客户归属流转完成' : '已驳回');
      loadA();
    } else if (auditForm.kind === 'channelLead') {
      await auditChannelLeadApproval(auditForm.approvalNo, payload);
      ElMessage.success(auditForm.approve ? '已通过，线索进入公海' : '已驳回');
      loadCL();
    } else if (auditForm.kind === 'SMS_TEMPLATE' || auditForm.kind === 'REPORT_TEMPLATE') {
      await auditContentApproval(auditForm.kind, auditForm.approvalNo, payload);
      ElMessage.success(auditForm.approve ? '模板已启用/发布' : '已驳回并保留意见');
      await loadContent(auditForm.kind === 'SMS_TEMPLATE' ? 'smsTemplate' : 'reportTemplate');
    } else {
      await auditDownloadApproval(auditForm.approvalNo, payload);
      ElMessage.success(auditForm.approve ? '已通过，24h 限时链接已生成' : '已驳回');
      loadD();
    }
    auditVisible.value = false;
  } catch (e) { /* 拦截器已提示 */ } finally {
    auditing.value = false;
  }
}

async function onVoid(row) {
  try {
    await voidDownloadApproval(row.approvalNo);
    ElMessage.success('已作废');
    loadD();
  } catch (e) { /* 拦截器已提示 */ }
}

// ============================================================
// 发起下载申请
// ============================================================
const applyVisible = ref(false);
const applying = ref(false);
const applyForm = reactive({ attachmentIds: [], purpose: '', expectDays: null });
const {
  items: attachmentOptions, loading: attachmentLoading, finished: attachmentFinished,
  search: searchAttachments, loadMore: loadMoreAttachments,
} = useRemoteOptions(pageAttachments, {
  debounce: 400,
  // 业务编码仅作为提交值，用户侧只展示可理解的资料、客户信息。
  normalize: (a) => ({ value: a.id, label: `${a.clientName || '客户未绑定'} · ${a.fileName || '未命名资料'} · ${a.attachmentTypeName || '其他资料'}` }),
});
function onAttachmentVisible(visible) {
  if (visible && !attachmentOptions.value.length && !attachmentLoading.value) searchAttachments('');
}
const applyFormRef = ref();
const applyRules = {
  attachmentIds: [
    { required: true, message: '资料清单必填', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (!Array.isArray(value) || value.length === 0) {
          callback(new Error('附件列表不能为空'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
  purpose: [
    { required: true, message: '用途说明必填', trigger: 'blur' },
    { min: 2, max: 200, message: '用途说明需 2-200 字', trigger: 'blur' },
  ],
  expectDays: [
    {
      validator: (rule, value, callback) => {
        if (value != null && (!Number.isInteger(value) || value < 1 || value > 365)) {
          callback(new Error('期望期限需为 1-365 天的整数'));
        } else {
          callback();
        }
      },
      trigger: 'change',
    },
  ],
};

function openApply() {
  applyForm.attachmentIds = [];
  applyForm.purpose = '';
  applyForm.expectDays = null;
  applyFormRef.value?.clearValidate();
  applyVisible.value = true;
}

async function onApply() {
  try {
    await applyFormRef.value.validate();
  } catch (e) {
    return;
  }
  applying.value = true;
  try {
    await applyDownload({ ...applyForm, attachmentIds: JSON.stringify(applyForm.attachmentIds) });
    ElMessage.success('申请已提交');
    applyVisible.value = false;
    loadD();
  } catch (e) { /* 拦截器已提示 */ } finally {
    applying.value = false;
  }
}

/** 页签首次激活时再取数，避免审核中心首屏并发三套分页。 */
watch(activeTab, async (tab) => {
  if (loadedTabs[tab]) return;
  if (tab === 'product' && !canAuditChannelContent.value) return;
  if (tab === 'allocation' && !canAuditAllocation.value) return;
  if (tab === 'channelLead' && !canAuditChannelContent.value) return;
  loadedTabs[tab] = true;
  try {
    if (tab === 'mine') await loadMine();
    else if (tab === 'product') await loadP();
    else if (tab === 'download') await loadD();
    else if (tab === 'allocation') await loadA();
    else if (tab === 'outing') await loadOutings();
    else if (tab === 'sensitivePhone') await loadSensitiveApprovals();
    else if (tab === 'channelLead') await loadCL();
    else await loadContent(tab);
  } catch {
    loadedTabs[tab] = false;
  }
}, { immediate: true });
</script>

<style scoped>
.cell-main { font-weight: 500; }
.cell-sub { font-size: 12px; color: var(--loan-text-secondary, var(--loan-text-muted)); }
.mono { font-family: "SF Mono", Menlo, Consolas, monospace; }
.link-token { font-size: 12px; color: var(--loan-primary); }
.muted { color: var(--loan-text-secondary, var(--loan-text-muted)); }
.attachment-detail { line-height: 20px; white-space: normal; }
.application-chooser { display: grid; gap: 10px; padding: 4px 0 8px; }
.application-choice {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 70px;
  padding: 13px 16px;
  border: 1px solid var(--loan-border);
  border-radius: var(--loan-radius-sm, 10px);
  background: var(--loan-surface);
  color: var(--loan-text);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--loan-transition), background var(--loan-transition), transform var(--loan-transition), box-shadow var(--loan-transition);
}
.application-choice:hover,
.application-choice:focus-visible {
  border-color: var(--loan-primary);
  background: var(--loan-primary-soft);
  box-shadow: 0 4px 14px color-mix(in srgb, var(--loan-primary) 12%, transparent);
  transform: translateY(-1px);
  outline: none;
}
.application-choice :deep(svg) { color: var(--loan-primary); }
.application-choice > span:nth-child(2) { min-width: 0; }
.application-choice strong,
.application-choice small { display: block; }
.application-choice strong { font-size: 14px; font-weight: 600; line-height: 20px; }
.application-choice small { margin-top: 3px; color: var(--loan-text-muted); font-size: 12px; line-height: 18px; }
.application-choice > span:last-child { color: var(--loan-primary); font-size: 18px; text-align: right; }
.remote-more { min-height: 36px; display: flex; align-items: center; justify-content: center; color: var(--loan-primary); cursor: pointer; font-size: 13px; }
.mine-type-tabs { display: flex; flex-wrap: wrap; gap: 8px; padding: 4px 0 14px; border-bottom: 1px solid var(--loan-border); margin-bottom: 14px; }
.mine-type-tab {
  appearance: none;
  border: 1px solid var(--loan-border);
  background: var(--loan-surface);
  color: var(--loan-text-secondary);
  border-radius: 999px;
  padding: 6px 14px;
  font-size: 13px;
  line-height: 1.4;
  cursor: pointer;
  transition: border-color var(--loan-transition), background var(--loan-transition), color var(--loan-transition);
}
.mine-type-tab:hover { border-color: var(--loan-primary); color: var(--loan-primary); }
.mine-type-tab.active { border-color: var(--loan-primary); background: var(--loan-primary-soft); color: var(--loan-primary); font-weight: 600; }
:global(.download-apply-dialog .el-dialog) { max-width: calc(100vw - 32px); overflow: visible; }
:global(.download-apply-dialog .el-dialog__body) { overflow: visible; }
:global(.download-apply-dialog .attachment-select-popper) { max-width: 490px; }
:global(.download-apply-dialog .attachment-select-popper .el-select-dropdown__wrap) { max-height: 220px; }
:global(.download-apply-dialog .attachment-select-popper .el-select-dropdown__item) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 640px) {
  .application-choice { grid-template-columns: 30px minmax(0, 1fr) 16px; padding: 11px 12px; }
}
</style>
