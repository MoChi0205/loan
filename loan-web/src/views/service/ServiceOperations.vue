<template>
  <div class="service-operations">
    <div class="loan-page-header service-header">
      <div>
        <h2 class="loan-page-title">{{ servicePageTitle }}</h2>
        <p class="loan-page-subtitle">{{ servicePageSubtitle }}</p>
      </div>
      <div class="header-actions">
        <el-select v-if="scopeOptions.length > 1" v-model="selectedScope" class="scope-select" aria-label="数据范围" @change="onScopeChange">
          <el-option v-for="item in scopeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-date-picker v-model="selectedDate" type="date" value-format="YYYY-MM-DD" :clearable="false" aria-label="业务日期" />
        <el-button :loading="refreshing" @click="refreshCurrent">刷新</el-button>
        <el-button type="primary" @click="openCreateAppointment">创建预约</el-button>
        <el-button v-if="activeTab === 'outings'" @click="openCreateGeneralOuting">普通外出</el-button>
      </div>
    </div>

    <div v-if="activeTab === 'daily'" class="metric-grid" v-loading="workbenchLoading">
      <button v-for="item in metrics" :key="item.key" class="metric-card loan-card" type="button" @click="goMetric(item)">
        <span class="metric-label">{{ item.label }}</span>
        <strong class="metric-value">{{ item.value }}</strong>
        <span class="metric-hint">{{ item.hint }}</span>
      </button>
    </div>

    <div class="loan-card main-card">
        <div v-if="activeTab === 'daily'" class="scope-notice">
          <AppIcon name="info" :size="14" />
          <span>{{ scopeNotice }}</span>
        </div>
        <div v-if="activeTab === 'daily'">
          <div class="list-grid" v-loading="workbenchLoading">
            <section class="list-panel" :class="{ 'list-panel--focused': focus === 'companyVisits' }">
              <div class="panel-head"><h3>{{ serviceLabels.visits }}</h3><span>{{ totalOf(workbench.companyVisits) }} 人</span></div>
              <button v-for="row in recordsOf(workbench.companyVisits)" :key="row.appointmentNo" class="record-item" type="button" @click="openClientReplay(row.clientCode)">
                <span><strong>{{ row.customerName || '未命名客户' }}</strong><small>{{ timeOnly(row.scheduledStart) }} · {{ row.hostStaffName || '顾问姓名待补充' }}</small></span>
                <el-tag size="small" :type="appointmentTag(row.status)">{{ appointmentStatusText[row.status] || row.status }}</el-tag>
              </button>
              <el-empty v-if="!recordsOf(workbench.companyVisits).length" description="当日暂无到公司服务" :image-size="52" />
            </section>
            <section class="list-panel" :class="{ 'list-panel--focused': focus === 'staffOutings' }">
              <div class="panel-head"><h3>{{ serviceLabels.outings }}</h3><span>{{ totalOf(workbench.staffOutings) }} 人</span></div>
              <button v-for="row in recordsOf(workbench.staffOutings)" :key="row.outingNo" class="record-item" type="button" @click="row.clientCode && openClientReplay(row.clientCode)">
                <span><strong>{{ row.staffName || '员工姓名待补充' }}</strong><small>{{ timeOnly(row.plannedStart) }} · {{ row.customerName || (row.clientCode ? '客户名称待补充' : '普通外出') }}</small></span>
                <el-tag size="small" :type="outingTag(row.status)">{{ outingStatusText[row.status] || row.status }}</el-tag>
              </button>
              <el-empty v-if="!recordsOf(workbench.staffOutings).length" description="当日暂无员工外出" :image-size="52" />
            </section>
            <section class="list-panel" :class="{ 'list-panel--focused': focus === 'pendingFollows' }">
              <div class="panel-head"><h3>{{ serviceLabels.follows }}</h3><span>{{ totalOf(workbench.pendingFollows) }} 项</span></div>
              <button v-for="row in recordsOf(workbench.pendingFollows)" :key="row.followNo" class="record-item" type="button" @click="openClientReplay(row.clientCode)">
                <span><strong>{{ row.customerName || '未命名客户' }}</strong><small>{{ formatDateTime(row.nextFollowAt) }}</small></span>
                <span class="record-note">{{ row.nextAction || '待跟进' }}</span>
              </button>
              <el-empty v-if="!recordsOf(workbench.pendingFollows).length" description="当日暂无待回访" :image-size="52" />
            </section>
            <section class="list-panel" :class="{ 'list-panel--focused': focus === 'activeOrders' }">
              <div class="panel-head"><h3>{{ serviceLabels.orders }}</h3><span>{{ totalOf(workbench.activeOrders) }} 单</span></div>
              <button v-for="row in recordsOf(workbench.activeOrders)" :key="row.orderNo" class="record-item" type="button" @click="openClientReplay(row.clientCode)">
                <span><strong>{{ row.customerName || '未命名客户' }}</strong><small>{{ formatDateTime(row.updatedAt) }}</small></span>
                <el-tag size="small" type="info">{{ orderStatusText[row.status] || row.status || '待处理' }}</el-tag>
              </button>
              <el-empty v-if="!recordsOf(workbench.activeOrders).length" description="暂无活跃工单" :image-size="52" />
            </section>
          </div>
        </div>

        <div v-else-if="activeTab === 'appointments'">
          <div class="filter-row">
            <el-select v-model="appointmentQuery.serviceMethod" clearable placeholder="全部服务方式" @change="loadAppointments">
              <el-option v-for="(label, code) in methodText" :key="code" :label="label" :value="code" />
            </el-select>
            <el-select v-model="appointmentQuery.status" clearable placeholder="全部状态" @change="loadAppointments">
              <el-option v-for="(label, code) in appointmentStatusText" :key="code" :label="label" :value="code" />
            </el-select>
          </div>
          <el-table v-loading="appointmentLoading" :data="appointments" stripe row-key="appointmentNo">
            <el-table-column label="客户" min-width="170">
              <template #default="{ row }"><button class="text-link" @click="openClientReplay(row.clientCode)">{{ row.customerName || '未命名客户' }}</button><div class="cell-sub">{{ row.contactName || '—' }} {{ row.contactPhoneMasked || '' }}</div></template>
            </el-table-column>
            <el-table-column label="联系方式" width="150"><template #default="{ row }">{{ row.contactPhoneMasked || '—' }}</template></el-table-column>
            <el-table-column label="服务安排" min-width="210"><template #default="{ row }"><div>{{ methodText[row.serviceMethod] || row.serviceMethod }}</div><div class="cell-sub">{{ formatDateTime(row.scheduledStart) }} 至 {{ timeOnly(row.scheduledEnd) }}</div></template></el-table-column>
            <el-table-column label="顾问/地点" min-width="160"><template #default="{ row }"><div>{{ row.hostStaffName || '顾问姓名待补充' }}</div><div class="cell-sub">{{ row.locationName || '线上服务' }}</div></template></el-table-column>
            <el-table-column label="状态" width="130"><template #default="{ row }"><el-tag :type="appointmentTag(row.status)" size="small">{{ appointmentStatusText[row.status] || row.status }}</el-tag><div class="cell-sub">{{ row.createdByType === 'STAFF' ? '公司已确认安排' : (row.customerConfirmStatus === 'CONFIRMED' ? '客户已提交确认' : '待客户确认') }}</div></template></el-table-column>
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <div class="action-row">
                  <el-button v-if="canConfirm(row)" link type="primary" @click="runAppointmentAction('confirm', row)">确认预约</el-button>
                  <el-button v-if="canArrive(row)" link type="primary" @click="runAppointmentAction('arrive', row)">登记到访</el-button>
                  <el-button v-if="canStart(row)" link type="primary" @click="runAppointmentAction('start', row)">开始</el-button>
                  <el-button v-if="canComplete(row)" link type="success" @click="runAppointmentAction('complete', row)">完成</el-button>
                  <el-button v-if="canNoShow(row)" link type="warning" @click="runAppointmentAction('noShow', row)">爽约</el-button>
                  <el-button v-if="canCreateOuting(row)" link type="primary" @click="openCreateOuting(row)">创建外出</el-button>
                  <el-dropdown v-if="canChange(row)" @command="(command) => onAppointmentMore(command, row)">
                    <el-button link>更多</el-button>
                    <template #dropdown><el-dropdown-menu><el-dropdown-item command="reschedule">改期</el-dropdown-item><el-dropdown-item command="cancel">取消</el-dropdown-item><el-dropdown-item v-if="isWithinFiveMinutes(row)" command="exceptionReschedule">异常改期</el-dropdown-item><el-dropdown-item v-if="isWithinFiveMinutes(row)" command="exceptionCancel">异常取消</el-dropdown-item></el-dropdown-menu></template>
                  </el-dropdown>
                  <span v-if="!canOperate(row)" class="cell-sub">仅可查看</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <AppPagination v-model:page="appointmentQuery.page" v-model:size="appointmentQuery.size" :total="appointmentTotal" @change="loadAppointments" />
        </div>

        <div v-else-if="activeTab === 'outings'">
          <div class="filter-row">
            <el-select v-model="outingQuery.status" clearable placeholder="全部状态" @change="loadOutings">
              <el-option v-for="(label, code) in outingStatusText" :key="code" :label="label" :value="code" />
            </el-select>
          </div>
          <el-table v-loading="outingLoading" :data="outings" stripe row-key="outingNo">
            <el-table-column label="员工" min-width="130"><template #default="{ row }"><div>{{ row.staffName || '员工姓名待补充' }}</div><div class="cell-sub">{{ row.deptName || '所属部门待补充' }}</div></template></el-table-column>
            <el-table-column label="客户" min-width="150"><template #default="{ row }"><button v-if="row.clientCode" class="text-link" @click="openClientReplay(row.clientCode)">{{ row.customerName || '客户名称待补充' }}</button><span v-else>无关联客户</span></template></el-table-column>
            <el-table-column label="计划时间" min-width="190"><template #default="{ row }">{{ formatDateTime(row.plannedStart) }}<div class="cell-sub">至 {{ timeOnly(row.plannedEnd) }}</div></template></el-table-column>
            <el-table-column label="目的地/目的" min-width="190"><template #default="{ row }"><div>{{ row.destination }}</div><div class="cell-sub">{{ row.purpose }}</div></template></el-table-column>
            <el-table-column label="打卡" min-width="210"><template #default="{ row }"><div>出发：{{ row.actualDepartedAt ? formatDateTime(row.actualDepartedAt) : '未打卡' }}<button v-if="row.departedPhotoKey" class="text-link" type="button" @click="viewPhoto(row, 'DEPART', '出发打卡照片')">照片</button></div><div class="cell-sub">返回：{{ row.actualReturnedAt ? formatDateTime(row.actualReturnedAt) : '未打卡' }}<button v-if="row.returnedPhotoKey" class="text-link" type="button" @click="viewPhoto(row, 'RETURN', '返回打卡照片')">照片</button></div></template></el-table-column>
            <el-table-column label="审核" min-width="170"><template #default="{ row }"><div>{{ row.reviewerName || (row.status === 'PENDING_REVIEW' ? '待审核' : '—') }}</div><div class="cell-sub" :title="row.reviewRemark || ''">{{ row.reviewedAt ? formatDateTime(row.reviewedAt) : '' }}<span v-if="row.reviewRemark"> · {{ row.reviewRemark }}</span></div></template></el-table-column>
            <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="outingTag(row.status)" size="small">{{ outingStatusText[row.status] || row.status }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="170" fixed="right"><template #default="{ row }"><template v-if="isOwnOuting(row)"><el-button v-if="row.status === 'READY'" link type="primary" @click="checkIn(row, 'depart')">出发打卡</el-button><el-button v-if="row.status === 'IN_PROGRESS'" link type="success" @click="checkIn(row, 'return')">返回打卡</el-button><el-button v-if="row.status === 'REJECTED'" link type="warning" @click="openResubmit(row)">重新提交</el-button><span v-if="row.status === 'PENDING_REVIEW'" class="cell-sub">待主管审核</span></template><template v-else-if="canReviewOuting(row)"><el-button v-if="row.status === 'PENDING_REVIEW'" link type="success" @click="onApprove(row)">通过</el-button><el-button v-if="row.status === 'PENDING_REVIEW'" link type="danger" @click="openReject(row)">驳回</el-button><span v-if="row.status !== 'PENDING_REVIEW'" class="cell-sub">仅可查看</span></template><span v-else class="cell-sub">仅可查看</span></template></el-table-column>
          </el-table>
          <AppPagination v-model:page="outingQuery.page" v-model:size="outingQuery.size" :total="outingTotal" @change="loadOutings" />
        </div>

        <div v-else>
          <div class="replay-layout">
            <aside class="client-picker">
              <el-input v-model="clientKeyword" clearable placeholder="姓名、企业名或手机号" @keyup.enter="searchClients"><template #append><el-button :loading="clientLoading" @click="searchClients">查询</el-button></template></el-input>
              <div class="client-results">
                <button v-for="row in clientOptions" :key="row.clientCode" type="button" class="client-option" :class="{ active: selectedClient?.clientCode === row.clientCode }" @click="selectClient(row)">
                  <strong>{{ row.enterpriseName || row.contactName || row.name || '未命名客户' }}</strong><span>{{ row.contactName || row.name || '—' }} · {{ row.phone || '未绑定手机号' }}</span><small>{{ row.ownerStaffName || '暂未分配顾问' }}</small>
                </button>
                <el-empty v-if="clientSearched && !clientOptions.length" description="未找到可见客户" :image-size="48" />
              </div>
            </aside>
            <section class="timeline-panel">
              <div class="panel-head replay-head"><div><h3>{{ selectedClientName }}</h3><span v-if="selectedClient">客户跟进回放</span></div><el-button v-if="canAddFollow" type="primary" @click="openFollow">新增跟进</el-button></div>

              <div v-if="selectedClient" v-loading="insightLoading" class="insight-panel">
                <div class="insight-head">
                  <div>
                    <h4>客户画像</h4>
                    <span v-if="insight">第 {{ insight.snapshotVersion }} 版 · {{ generatedByText[insight.generatedBy] || '系统生成' }} · {{ formatDateTime(insight.generatedAt) }}</span>
                    <span v-else>尚未生成画像快照</span>
                  </div>
                  <div class="insight-actions">
                    <el-tag v-if="insight" :type="insightStatusTag[insight.status]" size="small">{{ insightStatusText[insight.status] || '状态待确认' }}</el-tag>
                    <el-button v-if="canGenerateInsight" link type="primary" @click="onGenerateInsight">生成新版本</el-button>
                    <template v-if="canReviewInsight">
                      <el-button link type="success" @click="onReviewInsight('APPROVE')">复核通过</el-button>
                      <el-button link type="danger" @click="onReviewInsight('REJECT')">驳回</el-button>
                    </template>
                  </div>
                </div>
                <template v-if="insight">
                  <div class="insight-grid">
                    <div class="insight-block">
                      <strong>维度摘要</strong>
                      <ul v-if="dimensionPairs(insight.dimension).length"><li v-for="item in dimensionPairs(insight.dimension)" :key="item.key">{{ item.key }}：{{ item.value }}</li></ul>
                      <p v-else class="cell-sub">暂无维度数据</p>
                    </div>
                    <div class="insight-block">
                      <strong>风险提示</strong>
                      <ul v-if="insight.riskFlags?.length" class="insight-risk"><li v-for="(item, idx) in insight.riskFlags" :key="idx">{{ insightItemText(item, '风险事项') }}</li></ul>
                      <p v-else class="cell-sub">暂无风险提示</p>
                    </div>
                    <div class="insight-block">
                      <strong>经营建议</strong>
                      <ul v-if="insight.advice?.length"><li v-for="(item, idx) in insight.advice" :key="idx">{{ insightItemText(item, '经营建议') }}</li></ul>
                      <p v-else class="cell-sub">暂无建议</p>
                    </div>
                    <div class="insight-block">
                      <strong>来源与核验</strong>
                      <p class="cell-sub">{{ insight.sourceSummary?.dataSourceNotice || '暂无来源说明' }}</p>
                      <p class="cell-sub">材料版本：{{ insight.sourceSummary?.materialVersion || '—' }} · 数据期间：{{ insight.sourceSummary?.dataPeriod || '—' }}</p>
                      <p class="cell-sub">核验状态：{{ insight.sourceSummary?.materialReview?.verified ? '资料已核验' : '资料待核验' }}（待核验 {{ insight.sourceSummary?.materialReview?.pending || 0 }} · 需补充 {{ insight.sourceSummary?.materialReview?.rejected || 0 }}）</p>
                      <p v-if="insight.reviewRemark" class="cell-sub">复核意见：{{ insight.reviewRemark }}（{{ insight.reviewedBy || '—' }}）</p>
                    </div>
                  </div>
                  <div v-if="insightHistory.length" class="insight-versions">
                    <strong>版本链</strong>
                    <span v-for="item in insightHistory" :key="item.snapshotNo" class="version-chip" :class="{ active: item.current }">第 {{ item.snapshotVersion }} 版 · {{ insightStatusText[item.status] || '状态待确认' }}</span>
                  </div>
                </template>
                <p v-else class="cell-sub">尚未生成画像快照；生成后由主管复核生效，客户端只看到脱敏摘要。</p>
              </div>

              <el-timeline v-if="timeline.length" v-loading="timelineLoading">
                <el-timeline-item v-for="item in timeline" :key="item.eventNo" :timestamp="formatDateTime(item.happenedAt)" placement="top">
                  <div class="timeline-card"><strong>{{ eventTypeText(item.eventType) }}</strong><p>{{ item.summary || '—' }}</p><span>{{ actorTypeText(item.actorType) }} · {{ visibilityText(item.visibility) }}</span></div>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else :description="selectedClient ? '暂无活动记录' : '请先查询并选择客户'" />
              <AppPagination v-if="timelineTotal" v-model:page="timelineQuery.page" v-model:size="timelineQuery.size" :total="timelineTotal" @change="loadTimeline" />
            </section>
          </div>
        </div>
    </div>

    <AppDialog v-model:visible="createVisible" title="创建客户预约" width="680px" :loading="saving" @confirm="submitAppointment">
      <el-alert title="员工代客创建后立即确认；预约开始前 5 分钟内的变更需走异常处理并留痕。" type="info" :closable="false" show-icon />
      <el-form ref="appointmentFormRef" :model="appointmentForm" :rules="appointmentRules" label-width="100px" class="dialog-form">
        <el-form-item label="客户" prop="clientCode"><el-select v-model="appointmentForm.clientCode" filterable remote :remote-method="loadClientOptions" :loading="clientSelectLoading" placeholder="搜索姓名、企业名或手机号" style="width:100%" @visible-change="(v) => v && loadClientOptions('')"><el-option v-for="row in appointmentClientOptions" :key="row.clientCode" :label="`${row.enterpriseName || row.contactName || '未命名客户'} · ${row.contactName || '联系人待补充'}`" :value="row.clientCode" /></el-select></el-form-item>
        <el-form-item label="服务顾问" prop="hostStaffCode"><el-input v-if="isAdviser" :model-value="userStore.displayName || '当前顾问'" disabled /><el-select v-else v-model="appointmentForm.hostStaffCode" filterable remote :remote-method="loadStaffOptions" :loading="staffLoading" placeholder="搜索顾问姓名" style="width:100%" @visible-change="(v) => v && loadStaffOptions('')"><el-option v-for="row in staffOptions" :key="row.staffCode" :label="row.staffName || '顾问姓名待补充'" :value="row.staffCode" /></el-select></el-form-item>
        <el-form-item label="服务方式" prop="serviceMethod"><el-radio-group v-model="appointmentForm.serviceMethod"><el-radio-button v-for="(label, code) in methodText" :key="code" :label="code">{{ label }}</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="服务时间" prop="timeRange"><ServiceDateTimeRange v-model="appointmentForm.timeRange" :default-time="defaultAppointmentTime" /></el-form-item>
        <el-form-item v-if="needsLocation" label="地点名称" prop="locationName"><el-input v-model="appointmentForm.locationName" placeholder="公司或拜访地点名称" /></el-form-item>
        <el-form-item v-if="needsLocation" label="详细地址"><el-input v-model="appointmentForm.locationDetail" placeholder="详细地址（客户可见）" /></el-form-item>
        <el-form-item label="客户提示"><el-input v-model="appointmentForm.customerVisibleNote" type="textarea" :rows="2" placeholder="客户可见的下一步安排" /></el-form-item>
        <el-form-item label="内部备注"><el-input v-model="appointmentForm.internalNote" type="textarea" :rows="2" placeholder="仅公司员工可见" /></el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog v-model:visible="changeVisible" :title="changeMode.includes('reschedule') ? '预约改期' : '取消预约'" width="560px" :loading="saving" @confirm="submitChange">
      <el-form label-width="90px">
        <el-form-item v-if="changeMode.includes('reschedule')" label="新时间" required><ServiceDateTimeRange v-model="changeForm.timeRange" :default-time="defaultAppointmentTime" /></el-form-item>
        <el-form-item v-if="changeMode.includes('reschedule')" label="地点名称"><el-input v-model="changeForm.locationName" /></el-form-item>
        <el-form-item label="原因" :required="changeMode.startsWith('exception')"><el-input v-model="changeForm.reason" type="textarea" :rows="3" :placeholder="changeMode.startsWith('exception') ? '异常处理原因必填' : '请填写变更原因'" /></el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog v-model:visible="checkInVisible" :title="checkInMode === 'depart' ? '出发打卡' : '返回打卡'" width="540px" :loading="saving" @confirm="submitCheckIn">
      <el-alert title="打卡必须同时提交现场照片与单点定位；只保存本次位置，不采集连续轨迹。" type="info" :closable="false" show-icon />
      <div class="checkin-box"><el-button :loading="locating" @click="locate">获取当前位置</el-button><span>{{ checkInForm.locationText || '尚未获取定位' }}</span><small v-if="checkInForm.accuracyMeters">定位精度约 {{ Math.round(checkInForm.accuracyMeters) }} 米</small></div>
      <el-form label-width="90px" class="dialog-form">
        <el-form-item label="现场照片" required>
          <el-upload :auto-upload="false" :limit="1" accept="image/*" :file-list="checkInPhotoFiles" :on-change="onPickCheckInPhoto" :on-remove="resetCheckInPhotos">
            <el-button :loading="photoUploading">选择照片</el-button>
          </el-upload>
          <span class="cell-sub">{{ checkInForm.photoFileKey ? '照片已上传，可提交打卡' : '支持 jpg / jpeg / png / webp，不超过 10MB' }}</span>
        </el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog v-model:visible="rejectVisible" title="驳回外出申请" width="520px" :loading="saving" @confirm="submitReject">
      <el-alert title="驳回后申请人可修改并重新提交，原因会记录在审核留痕与客户活动回放中。" type="warning" :closable="false" show-icon />
      <el-form label-width="90px" class="dialog-form">
        <el-form-item label="申请人"><span>{{ rejectTarget?.staffName || '员工姓名待补充' }}</span></el-form-item>
        <el-form-item label="驳回原因" required><el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="必填：说明需要补充或修正的内容" /></el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog v-model:visible="resubmitVisible" title="重新提交外出申请" width="540px" :loading="saving" @confirm="submitResubmit">
      <el-alert :title="`上次驳回原因：${resubmitTarget?.reviewRemark || '—'}`" type="warning" :closable="false" show-icon />
      <el-form label-width="90px" class="dialog-form">
        <el-form-item label="目的地" required><el-input v-model="resubmitForm.destination" /></el-form-item>
        <el-form-item label="拜访目的" required><el-input v-model="resubmitForm.purpose" /></el-form-item>
        <el-form-item label="内部备注"><el-input v-model="resubmitForm.internalNote" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog v-model:visible="photoVisible" :title="photoTitle" width="640px">
      <img v-if="photoUrl" :src="photoUrl" alt="打卡照片" class="checkin-photo" />
      <template #footer><el-button @click="photoVisible = false">关闭</el-button></template>
    </AppDialog>

    <AppDialog v-model:visible="outingCreateVisible" :title="outingCreateTarget ? '提交上门外出申请' : '提交普通外出申请'" width="540px" :loading="saving" @confirm="submitOuting">
      <el-alert title="提交后需主管审核通过，才能出发与返回打卡；照片和单点位置均为必填。" type="warning" :closable="false" show-icon />
      <el-form label-width="90px" class="dialog-form">
        <el-form-item v-if="outingCreateTarget" label="客户"><span>{{ outingCreateTarget?.customerName || '客户名称待补充' }}</span></el-form-item>
        <el-form-item v-else label="计划时间" required><ServiceDateTimeRange v-model="outingCreateForm.timeRange" :default-time="defaultAppointmentTime" /></el-form-item>
        <el-form-item label="目的地" required><el-input v-model="outingCreateForm.destination" placeholder="上门服务地点" /></el-form-item>
        <el-form-item label="拜访目的" required><el-input v-model="outingCreateForm.purpose" placeholder="例如：经营资料梳理" /></el-form-item>
        <el-form-item label="内部备注"><el-input v-model="outingCreateForm.internalNote" type="textarea" :rows="2" placeholder="仅公司员工可见" /></el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog v-model:visible="followVisible" title="新增客户跟进" width="620px" :loading="saving" @confirm="submitFollow">
      <el-form label-width="100px">
        <el-form-item label="跟进渠道" required><el-select v-model="followForm.channelType" style="width:100%"><el-option v-for="(label, code) in followChannelText" :key="code" :label="label" :value="code" /></el-select></el-form-item>
        <el-form-item label="跟进结果" required><el-input v-model="followForm.resultCode" placeholder="例如：已沟通、待补充材料" /></el-form-item>
        <el-form-item label="跟进内容" required><el-input v-model="followForm.content" type="textarea" :rows="3" placeholder="仅员工内部查看的完整记录" /></el-form-item>
        <el-form-item label="下步安排"><el-input v-model="followForm.nextAction" placeholder="下一次要做什么" /></el-form-item>
        <el-form-item label="下次跟进"><el-date-picker v-model="followForm.nextFollowAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item>
        <el-form-item label="客户可见"><el-switch v-model="followCustomerVisible" active-text="显示摘要" inactive-text="仅员工可见" /></el-form-item>
        <el-form-item v-if="followCustomerVisible" label="客户摘要" required><el-input v-model="followForm.customerVisibleSummary" type="textarea" :rows="2" placeholder="仅填写适合客户查看的服务进展，不含内部判断" /></el-form-item>
      </el-form>
    </AppDialog>
  </div>
</template>

<script setup>
defineOptions({ name: '_service_operations' });
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import AppDialog from '@/components/AppDialog.vue';
import AppPagination from '@/components/AppPagination.vue';
import AppIcon from '@/components/AppIcon.vue';
import ServiceDateTimeRange from '@/components/ServiceDateTimeRange.vue';
import { pageClients, getClientDetail } from '@/api/client';
import { staffPage } from '@/api/org';
import {
  getDailyServiceLists, pageAppointments, createAppointment, confirmAppointment,
  arriveAppointment, startAppointment, completeAppointment, noShowAppointment,
  cancelAppointment, rescheduleAppointment, pageOutings, departOuting, returnOuting,
  createOuting, createFollowRecord, getClientActivityTimeline,
  approveOuting, rejectOuting, resubmitOuting, uploadOutingPhoto, fetchOutingPhoto,
  getClientInsight, getClientInsightHistory, generateClientInsight, reviewClientInsight,
} from '@/api/serviceOperations';
import { useUserStore } from '@/store/user';
import { formatDateTime } from '@/utils/format';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const userNo = computed(() => userStore.user?.userNo || userStore.user?.staffCode || '');
const isAdviser = computed(() => userStore.roleCode === 'ADVISER');
const today = () => {
  const d = new Date();
  const pad = (v) => String(v).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};
const selectedDate = ref(today());
/**
 * 页面类型直接以路由元数据为唯一真值。
 * 四个子菜单复用同一组件，不能再维护一份可能滞后的本地 activeTab。
 */
const activeTab = computed(() => {
  const view = String(route.meta.serviceView || route.query.tab || 'daily');
  return ['daily', 'appointments', 'outings', 'replay'].includes(view) ? view : 'daily';
});
const roleMaximumScope = computed(() => ({ ADVISER: 'SELF', DEPT_MANAGER: 'DEPARTMENT', BOSS: 'COMPANY', OPERATOR: 'COMPANY', SUPER_ADMIN: 'COMPANY', SUPER: 'COMPANY' }[userStore.roleCode] || 'NONE'));
const scopeOptions = computed(() => {
  const max = roleMaximumScope.value;
  if (max === 'SELF') return [{ value: 'SELF', label: '我的数据' }];
  if (max === 'DEPARTMENT') return [{ value: 'SELF', label: '我的数据' }, { value: 'DEPARTMENT', label: '团队数据' }];
  if (max === 'COMPANY') return [{ value: 'SELF', label: '我的数据' }, { value: 'DEPARTMENT', label: '团队数据' }, { value: 'COMPANY', label: '全公司数据' }];
  return [];
});
const selectedScope = ref(roleMaximumScope.value === 'NONE' ? 'SELF' : roleMaximumScope.value);
const clientScope = computed(() => ({ SELF: 'MY', DEPARTMENT: 'TEAM', COMPANY: 'ALL' }[selectedScope.value] || 'MY'));
const focus = ref('');
const refreshing = ref(false);
const saving = ref(false);

const methodText = Object.freeze({ COMPANY_ON_SITE: '客户到访我司', HOME_VISIT: '员工上门拜访客户', VIDEO_MEETING: '视频会议', PHONE_CONSULT: '电话咨询' });
const appointmentStatusText = Object.freeze({ REQUESTED: '待顾问确认', CONFIRMED: '已预约', ARRIVED: '已到店', SERVING: '服务中', COMPLETED: '已完成', CANCELLED: '已取消', NO_SHOW: '未到场', RESCHEDULED: '已改期' });
const outingStatusText = Object.freeze({ DRAFT: '草稿', PENDING_REVIEW: '待审核', REJECTED: '已驳回', READY: '待出发', IN_PROGRESS: '外出中', COMPLETED: '已返回', CANCELLED: '已取消' });
const orderStatusText = Object.freeze({ PENDING: '待处理', PROCESSING: '处理中', IN_PROGRESS: '服务中', COMPLETED: '已完成', CANCELLED: '已取消', CLOSED: '已关闭', REJECTED: '已驳回' });
const followChannelText = Object.freeze({ PHONE: '电话咨询', COMPANY_ON_SITE: '客户到访我司', HOME_VISIT: '员工上门拜访客户', VIDEO_MEETING: '视频会议', WECOM: '企业微信', OTHER: '其他' });
const serviceLabels = computed(() => {
  const prefix = ({ SELF: '我的', DEPARTMENT: '团队', COMPANY: '全公司' })[selectedScope.value] || '';
  return {
    visits: prefix ? `${prefix}来访` : '今日来访',
    outings: prefix ? `${prefix}外出` : '外出服务',
    follows: prefix ? `${prefix}待回访` : '待回访',
    orders: prefix ? `${prefix}活跃工单` : '活跃工单',
  };
});
const servicePageTitle = computed(() => {
  const title = { daily: `${serviceLabels.value.visits.replace('来访', '') || '今日'}服务台`, appointments: `${serviceLabels.value.visits.replace('来访', '') || ''}客户预约`, outings: serviceLabels.value.outings, replay: `${serviceLabels.value.visits.replace('来访', '') || ''}客户回放` };
  return title[activeTab.value] || '服务台';
});
const servicePageSubtitle = computed(() => ({
  daily: `${scopeLabel.value} · ${serviceLabels.value.visits}、${serviceLabels.value.outings}、${serviceLabels.value.follows}与${serviceLabels.value.orders}`,
  appointments: `${scopeLabel.value}客户到访我司、员工上门拜访客户、视频会议与电话咨询预约`,
  outings: `${serviceLabels.value.outings}的计划、单点位置打卡与结果记录`,
  replay: `按${scopeLabel.value}客户查看画像、预约、外出、工单和跟进时间线`,
}[activeTab.value] || '服务过程协同'));
const scopeLabel = computed(() => ({
  SELF: '本人', DEPARTMENT: '本团队', COMPANY: '全公司',
}[selectedScope.value] || '当前范围'));
const scopeNotice = computed(() => `${scopeLabel.value}数据 · “${serviceLabels.value.follows}”是顾问为客户设置下一次跟进时间后，在所选日期需要处理的跟进事项`);
const eventText = Object.freeze({ APPOINTMENT_CREATED: '创建预约', CUSTOMER_CONFIRMED: '客户确认', APPOINTMENT_CONFIRMED: '预约确认', CUSTOMER_ARRIVED: '客户到店', CUSTOMER_CHECKED_IN: '客户到店签到', SERVICE_STARTED: '开始服务', SERVICE_COMPLETED: '服务完成', APPOINTMENT_CANCELLED: '取消预约', APPOINTMENT_NO_SHOW: '客户未到场', APPOINTMENT_RESCHEDULED: '预约改期', OUTING_SUBMITTED: '提交外出申请', OUTING_APPROVED: '外出审核通过', OUTING_REJECTED: '外出申请驳回', OUTING_DEPARTED: '出发打卡', OUTING_RETURNED: '返回打卡', FOLLOW_RECORDED: '客户跟进', FOLLOW_UP: '客户跟进', INSIGHT_GENERATED: '生成客户画像', INSIGHT_REVIEWED: '复核客户画像' });
const actorText = Object.freeze({ STAFF: '员工', CUSTOMER: '客户', SYSTEM: '系统' });
const visibilityLabels = Object.freeze({ CUSTOMER: '客户可见', STAFF_ONLY: '仅员工可见', INTERNAL: '仅员工可见' });
function eventTypeText(value) { return eventText[value] || '客户服务记录'; }
function actorTypeText(value) { return actorText[value] || '系统记录'; }
function visibilityText(value) { return visibilityLabels[value] || '仅员工可见'; }
function recordsOf(page) { return Array.isArray(page?.records) ? page.records : []; }
function totalOf(page) { return Number(page?.total || 0); }
function timeOnly(value) { const text = formatDateTime(value); return text === '-' ? text : text.slice(11, 16); }
function appointmentTag(status) { return ({ COMPLETED: 'success', CANCELLED: 'info', NO_SHOW: 'danger', SERVING: 'warning', ARRIVED: 'warning' })[status] || 'info'; }
function outingTag(status) { return ({ COMPLETED: 'success', CANCELLED: 'info', IN_PROGRESS: 'warning', PENDING_REVIEW: 'warning', REJECTED: 'danger' })[status] || 'info'; }

const workbench = ref({});
const workbenchLoading = ref(false);
const metrics = computed(() => [
  { key: 'visits', label: serviceLabels.value.visits, value: totalOf(workbench.value.companyVisits), hint: '客户到访我司', tab: 'appointments', method: 'COMPANY_ON_SITE' },
  { key: 'outings', label: serviceLabels.value.outings, value: totalOf(workbench.value.staffOutings), hint: '上门拜访或普通外出', tab: 'outings' },
  { key: 'follows', label: serviceLabels.value.follows, value: totalOf(workbench.value.pendingFollows), hint: '按计划处理跟进事项', tab: 'daily' },
  { key: 'orders', label: serviceLabels.value.orders, value: totalOf(workbench.value.activeOrders), hint: '进行中的服务工单', tab: 'daily' },
]);
async function loadWorkbench(refresh = false) {
  workbenchLoading.value = true;
  try { workbench.value = (await getDailyServiceLists({ date: selectedDate.value, scope: selectedScope.value, page: 1, size: 20, refresh })).data || {}; }
  finally { workbenchLoading.value = false; }
}
/**
 * 工作台指标跳转必须携带当前筛选上下文。
 * 之前只传服务方式，导致用户在指定日期查看统计后跳转到列表又回到今天。
 */
function goMetric(item) {
  const query = { date: selectedDate.value };
  if (item.method) query.serviceMethod = item.method;
  if (item.tab === 'daily' && item.key) query.focus = item.key === 'follows' ? 'pendingFollows' : item.key === 'orders' ? 'activeOrders' : item.key;
  router.push({ path: `/service-operations/${item.tab}`, query });
}

const appointments = ref([]);
const appointmentTotal = ref(0);
const appointmentLoading = ref(false);
const appointmentQuery = reactive({ page: 1, size: 20, serviceMethod: '', status: '' });
async function loadAppointments() {
  appointmentLoading.value = true;
  try { const page = (await pageAppointments({ ...appointmentQuery, date: selectedDate.value, scope: selectedScope.value })).data || {}; appointments.value = recordsOf(page); appointmentTotal.value = totalOf(page); }
  finally { appointmentLoading.value = false; }
}
function canOperate(row) { return !!userNo.value && row.hostStaffCode === userNo.value; }
function canConfirm() { return false; }
function canArrive(row) { return canOperate(row) && row.status === 'CONFIRMED' && row.serviceMethod === 'COMPANY_ON_SITE'; }
function canStart(row) { return canOperate(row) && ((row.status === 'ARRIVED' && row.serviceMethod === 'COMPANY_ON_SITE') || (row.status === 'CONFIRMED' && ['VIDEO_MEETING', 'PHONE_CONSULT'].includes(row.serviceMethod))); }
function canComplete(row) { return canOperate(row) && row.status === 'SERVING'; }
function canNoShow(row) { return canOperate(row) && row.status === 'CONFIRMED' && new Date(row.scheduledStart).getTime() <= Date.now(); }
function canCreateOuting(row) { return canOperate(row) && row.status === 'CONFIRMED' && row.serviceMethod === 'HOME_VISIT'; }
function canChange(row) { return canOperate(row) && ['REQUESTED', 'CONFIRMED', 'ARRIVED', 'SERVING'].includes(row.status); }
function isWithinFiveMinutes(row) { return new Date(row.scheduledStart).getTime() - Date.now() < 5 * 60 * 1000; }
const appointmentActionMap = { confirm: confirmAppointment, arrive: arriveAppointment, start: startAppointment, complete: completeAppointment, noShow: noShowAppointment };
async function runAppointmentAction(action, row) {
  const label = ({ confirm: '确认预约', arrive: '登记客户到店', start: '开始服务', complete: '完成服务', noShow: '标记客户未到场' })[action];
  await ElMessageBox.confirm(`确认${label}？`, '操作确认');
  await appointmentActionMap[action](row.appointmentNo);
  ElMessage.success(`${label}成功`);
  await Promise.all([loadAppointments(), loadWorkbench(true)]);
}

const outings = ref([]);
const outingTotal = ref(0);
const outingLoading = ref(false);
const outingQuery = reactive({ page: 1, size: 20, status: '' });
async function loadOutings() {
  outingLoading.value = true;
  try { const page = (await pageOutings({ ...outingQuery, date: selectedDate.value, scope: selectedScope.value })).data || {}; outings.value = recordsOf(page); outingTotal.value = totalOf(page); }
  finally { outingLoading.value = false; }
}
function isOwnOuting(row) { return !!userNo.value && row.staffCode === userNo.value; }

function onTabChange(tab) {
  if (tab === 'daily') return loadWorkbench();
  if (tab === 'appointments') return loadAppointments();
  if (tab === 'outings') return loadOutings();
  if (tab === 'replay' && selectedClient.value) return loadTimeline();
  return Promise.resolve();
}
async function refreshCurrent() { refreshing.value = true; try { if (activeTab.value === 'daily') await loadWorkbench(true); else await onTabChange(activeTab.value); } finally { refreshing.value = false; } }
watch(selectedDate, () => { appointmentQuery.page = 1; outingQuery.page = 1; if (activeTab.value === 'daily') loadWorkbench(); else Promise.all([loadWorkbench(), onTabChange(activeTab.value)]); });

const createVisible = ref(false);
const appointmentFormRef = ref();
const appointmentForm = reactive({ clientCode: '', hostStaffCode: '', serviceMethod: 'COMPANY_ON_SITE', timeRange: [], locationName: '', locationDetail: '', customerVisibleNote: '', internalNote: '' });
const defaultAppointmentTime = [new Date(2000, 0, 1, 9, 0, 0), new Date(2000, 0, 1, 10, 0, 0)];
const appointmentRules = { clientCode: [{ required: true, message: '请选择客户', trigger: 'change' }], hostStaffCode: [{ required: true, message: '请选择服务顾问', trigger: 'change' }], serviceMethod: [{ required: true, message: '请选择服务方式', trigger: 'change' }], timeRange: [{ type: 'array', required: true, min: 2, message: '请选择服务起止时间', trigger: 'change' }], locationName: [{ validator: (_rule, value, callback) => needsLocation.value && !value ? callback(new Error('请填写服务地点')) : callback(), trigger: 'blur' }] };
const needsLocation = computed(() => ['COMPANY_ON_SITE', 'HOME_VISIT'].includes(appointmentForm.serviceMethod));
const appointmentClientOptions = ref([]);
const clientSelectLoading = ref(false);
const staffOptions = ref([]);
const staffLoading = ref(false);
function openCreateAppointment() { Object.assign(appointmentForm, { clientCode: '', hostStaffCode: userNo.value, serviceMethod: 'COMPANY_ON_SITE', timeRange: [], locationName: '', locationDetail: '', customerVisibleNote: '', internalNote: '' }); createVisible.value = true; loadClientOptions(''); if (!isAdviser.value) loadStaffOptions(''); }
let clientOptionTimer;
let clientOptionSequence = 0;
function loadClientOptions(keyword) {
  clearTimeout(clientOptionTimer);
  const seq = ++clientOptionSequence;
  clientOptionTimer = setTimeout(async () => {
    clientSelectLoading.value = true;
    try {
      const page = (await pageClients({ keyword: keyword || '', page: 1, size: 20, scope: clientScope.value })).data || {};
      if (seq === clientOptionSequence) appointmentClientOptions.value = recordsOf(page);
    } finally { if (seq === clientOptionSequence) clientSelectLoading.value = false; }
  }, 220);
}
async function loadStaffOptions(keyword) { staffLoading.value = true; try { const params = { keyword, page: 1, size: 50 }; if (userStore.roleCode === 'DEPT_MANAGER') params.deptCode = userStore.user?.deptCode; const page = (await staffPage(params)).data || {}; staffOptions.value = recordsOf(page).filter((row) => !row.status || row.status === 'ACTIVE'); } finally { staffLoading.value = false; } }
async function submitAppointment() {
  const valid = await appointmentFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (!appointmentForm.timeRange?.[0] || !appointmentForm.timeRange?.[1]) return ElMessage.warning('请选择完整的开始和结束日期时间');
  if (new Date(appointmentForm.timeRange[1]).getTime() <= new Date(appointmentForm.timeRange[0]).getTime()) return ElMessage.warning('结束时间必须晚于开始时间');
  saving.value = true;
  try {
    const data = { ...appointmentForm, scheduledStart: appointmentForm.timeRange[0], scheduledEnd: appointmentForm.timeRange[1] };
    delete data.timeRange;
    await createAppointment(data);
    createVisible.value = false;
    ElMessage.success('预约已创建并确认');
    await router.push('/service-operations/appointments');
  } catch (e) {
    const message = e?.message || '';
    if (/已存在外出记录/.test(message)) {
      ElMessage.warning('该预约已经提交过外出申请，请在“我的外出”中查看审批进度');
    }
  } finally { saving.value = false; }
}

const changeVisible = ref(false);
const changeMode = ref('cancel');
const changeTarget = ref(null);
const changeForm = reactive({ timeRange: [], locationName: '', locationDetail: '', reason: '' });
function onAppointmentMore(command, row) { changeMode.value = command; changeTarget.value = row; Object.assign(changeForm, { timeRange: [], locationName: row.locationName || '', locationDetail: row.locationDetail || '', reason: '' }); changeVisible.value = true; }
async function submitChange() {
  const exceptionFlow = changeMode.value.startsWith('exception');
  if (exceptionFlow && !changeForm.reason.trim()) return ElMessage.warning('异常处理原因必填');
  saving.value = true;
  try {
    if (changeMode.value.includes('reschedule')) {
      if (!changeForm.timeRange?.[0] || !changeForm.timeRange?.[1]) return ElMessage.warning('请选择新的服务时间');
      await rescheduleAppointment(changeTarget.value.appointmentNo, { scheduledStart: changeForm.timeRange[0], scheduledEnd: changeForm.timeRange[1], locationName: changeForm.locationName, locationDetail: changeForm.locationDetail, reason: changeForm.reason }, exceptionFlow);
    } else await cancelAppointment(changeTarget.value.appointmentNo, changeForm.reason, exceptionFlow);
    changeVisible.value = false; ElMessage.success(changeMode.value.includes('reschedule') ? '改期成功' : '取消成功'); await Promise.all([loadAppointments(), loadWorkbench(true)]);
  } finally { saving.value = false; }
}

const userRole = computed(() => userStore.roleCode || '');
const reviewerRoles = ['BOSS', 'OPERATOR', 'SUPER_ADMIN', 'SUPER'];
/** 前端只做按钮可见性判断，最终以服务端策略为准（禁止自审 + 部门经理限本部门）。 */
function canReviewOuting(row) {
  if (!userNo.value || row.staffCode === userNo.value) return false;
  if (reviewerRoles.includes(userRole.value)) return true;
  if (userRole.value === 'DEPT_MANAGER') return !!row.deptCode && row.deptCode === userStore.user?.deptCode;
  return false;
}

const checkInVisible = ref(false);
const checkInMode = ref('depart');
const checkInTarget = ref(null);
const locating = ref(false);
const photoUploading = ref(false);
const checkInPhotoFiles = ref([]);
const checkInForm = reactive({ latitude: null, longitude: null, accuracyMeters: null, locationText: '', collectedAt: '', photoFileKey: '' });
function checkIn(row, mode) {
  checkInTarget.value = row;
  checkInMode.value = mode;
  Object.assign(checkInForm, { latitude: null, longitude: null, accuracyMeters: null, locationText: '', collectedAt: '', photoFileKey: '' });
  resetCheckInPhotos();
  checkInVisible.value = true;
  locate();
}
/** 选照片即上传：先拿 fileKey，避免提交打卡时才发现照片没传成功。 */
async function onPickCheckInPhoto(uploadFile) {
  const raw = uploadFile?.raw;
  if (!raw) return;
  if (!raw.type || !raw.type.startsWith('image/')) return ElMessage.warning('只能上传图片文件');
  if (raw.size > 10 * 1024 * 1024) return ElMessage.warning('打卡照片不能超过 10MB');
  photoUploading.value = true;
  try {
    const res = await uploadOutingPhoto(checkInTarget.value.outingNo, raw);
    const fileKey = res?.data?.fileKey || '';
    if (!fileKey) {
      resetCheckInPhotos();
      return ElMessage.warning('照片上传未返回标识，请重新选择');
    }
    checkInForm.photoFileKey = fileKey;
    checkInPhotoFiles.value = [{ name: raw.name, uid: uploadFile.uid || Date.now(), url: URL.createObjectURL(raw) }];
  } catch (e) {
    resetCheckInPhotos();
  } finally {
    photoUploading.value = false;
  }
}
function resetCheckInPhotos() {
  (checkInPhotoFiles.value || []).forEach((item) => { if (item.url) URL.revokeObjectURL(item.url); });
  checkInPhotoFiles.value = [];
  checkInForm.photoFileKey = '';
}
function localDateTime(timestamp) {
  const d = new Date(timestamp || Date.now());
  const pad = (v) => String(v).padStart(2, '0');
  // 后端字段是 LocalDateTime，不能传 toISOString() 的 UTC 时间；否则中国时区会相差 8 小时。
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}
function locate() {
  if (!navigator.geolocation) return ElMessage.error('当前浏览器不支持定位');
  locating.value = true;
  navigator.geolocation.getCurrentPosition((position) => { Object.assign(checkInForm, { latitude: position.coords.latitude, longitude: position.coords.longitude, accuracyMeters: position.coords.accuracy, locationText: `经度 ${position.coords.longitude.toFixed(6)}，纬度 ${position.coords.latitude.toFixed(6)}`, collectedAt: localDateTime(position.timestamp) }); locating.value = false; }, (error) => { locating.value = false; ElMessage.error(error.code === 1 ? '定位权限未开启，请允许浏览器访问位置' : '定位失败，请重试'); }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 });
}
async function submitCheckIn() {
  if (checkInForm.latitude == null) return ElMessage.warning('请先获取当前位置');
  if (!checkInForm.photoFileKey) return ElMessage.warning('请先上传现场照片');
  saving.value = true;
  try { const fn = checkInMode.value === 'depart' ? departOuting : returnOuting; await fn(checkInTarget.value.outingNo, { ...checkInForm }); checkInVisible.value = false; ElMessage.success(`${checkInMode.value === 'depart' ? '出发' : '返回'}打卡成功`); await Promise.all([loadOutings(), loadWorkbench(true)]); }
  finally { saving.value = false; }
}

const rejectVisible = ref(false);
const rejectTarget = ref(null);
const rejectReason = ref('');
function openReject(row) { rejectTarget.value = row; rejectReason.value = ''; rejectVisible.value = true; }
async function submitReject() {
  if (!rejectReason.value.trim()) return ElMessage.warning('驳回原因必填');
  saving.value = true;
  try {
    await rejectOuting(rejectTarget.value.outingNo, rejectReason.value.trim());
    rejectVisible.value = false;
    ElMessage.success('已驳回，申请人可修改后重新提交');
    await Promise.all([loadOutings(), loadWorkbench(true)]);
  } finally { saving.value = false; }
}
async function onApprove(row) {
  await ElMessageBox.confirm(`确认通过「${row.staffName || '该员工'}」的外出申请？通过后其可完成出发与返回打卡。`, '审核确认');
  await approveOuting(row.outingNo, '');
  ElMessage.success('已通过审核');
  await Promise.all([loadOutings(), loadWorkbench(true)]);
}

const resubmitVisible = ref(false);
const resubmitTarget = ref(null);
const resubmitForm = reactive({ destination: '', purpose: '', internalNote: '' });
function openResubmit(row) { resubmitTarget.value = row; Object.assign(resubmitForm, { destination: row.destination || '', purpose: row.purpose || '', internalNote: '' }); resubmitVisible.value = true; }
async function submitResubmit() {
  if (!resubmitForm.destination.trim() || !resubmitForm.purpose.trim()) return ElMessage.warning('目的地和拜访目的必填');
  saving.value = true;
  try {
    await resubmitOuting(resubmitTarget.value.outingNo, { ...resubmitForm });
    resubmitVisible.value = false;
    ElMessage.success('已重新提交，等待主管审核');
    await Promise.all([loadOutings(), loadWorkbench(true)]);
  } finally { saving.value = false; }
}

const photoVisible = ref(false);
const photoUrl = ref('');
const photoTitle = ref('打卡照片');
async function viewPhoto(row, phase, title) {
  photoTitle.value = title || '打卡照片';
  try {
    const blob = await fetchOutingPhoto(row.outingNo, phase);
    if (photoUrl.value) URL.revokeObjectURL(photoUrl.value);
    photoUrl.value = URL.createObjectURL(blob);
    photoVisible.value = true;
  } catch (e) {
    ElMessage.error('打卡照片获取失败或已失效');
  }
}

const outingCreateVisible = ref(false);
const outingCreateTarget = ref(null);
const outingCreateForm = reactive({ timeRange: [], destination: '', purpose: '', internalNote: '' });
function openCreateOuting(row) {
  outingCreateTarget.value = row;
  Object.assign(outingCreateForm, { timeRange: [], destination: row.locationDetail || row.locationName || '', purpose: '', internalNote: '' });
  outingCreateVisible.value = true;
}
function openCreateGeneralOuting() { outingCreateTarget.value = null; Object.assign(outingCreateForm, { timeRange: [], destination: '', purpose: '', internalNote: '' }); outingCreateVisible.value = true; }
async function submitOuting() {
  if (!outingCreateForm.destination.trim() || !outingCreateForm.purpose.trim()) return ElMessage.warning('目的地和拜访目的必填');
  if (!outingCreateTarget.value && (!outingCreateForm.timeRange?.[0] || !outingCreateForm.timeRange?.[1])) return ElMessage.warning('请选择普通外出计划时间');
  saving.value = true;
  try {
    const payload = { ...outingCreateForm, outingType: outingCreateTarget.value ? 'HOME_VISIT' : 'GENERAL' };
    if (outingCreateTarget.value) payload.appointmentNo = outingCreateTarget.value.appointmentNo;
    else { payload.plannedStart = payload.timeRange[0]; payload.plannedEnd = payload.timeRange[1]; }
    delete payload.timeRange;
    await createOuting(payload);
    outingCreateVisible.value = false;
    ElMessage.success('外出申请已提交，待主管审核通过后可打卡');
    await Promise.all([loadAppointments(), loadWorkbench(true)]);
  } catch (e) {
    const message = e?.message || '';
    if (message.includes('已经提交过外出申请') || message.includes('已有外出申请')) {
      ElMessage.warning('该预约已经提交过外出申请，请在“我的外出”中查看审批进度');
    }
  } finally { saving.value = false; }
}

const clientKeyword = ref('');
const clientOptions = ref([]);
const clientLoading = ref(false);
const clientSearched = ref(false);
const selectedClient = ref(null);
const selectedClientName = computed(() => selectedClient.value ? (selectedClient.value.enterpriseName || selectedClient.value.contactName || selectedClient.value.name || '未命名客户') : '客户活动回放');
const canAddFollow = computed(() => selectedClient.value?.ownerStaffCode === userNo.value);
let replaySearchTimer;
let replaySearchSequence = 0;
function searchClients() {
  clearTimeout(replaySearchTimer);
  const seq = ++replaySearchSequence;
  clientSearched.value = true;
  replaySearchTimer = setTimeout(async () => {
    clientLoading.value = true;
    try {
      const page = (await pageClients({ keyword: clientKeyword.value.trim(), page: 1, size: 20, scope: clientScope.value })).data || {};
      if (seq === replaySearchSequence) clientOptions.value = recordsOf(page);
    } finally { if (seq === replaySearchSequence) clientLoading.value = false; }
  }, 220);
}
function selectClient(row) { selectedClient.value = row; timelineQuery.page = 1; router.replace({ path: '/service-operations/replay', query: { clientCode: row.clientCode, scope: selectedScope.value } }); loadInsight(); loadTimeline(); }
function openClientReplay(clientCode) {
  if (activeTab.value !== 'replay') return router.push({ path: '/service-operations/replay', query: { clientCode, scope: selectedScope.value } });
  clientKeyword.value = '';
  return getClientDetail(clientCode).then((res) => {
    const row = res.data || { clientCode, contactName: '未命名客户' };
    selectClient({ ...row, clientCode });
  });
}
const timeline = ref([]);
const timelineTotal = ref(0);
const timelineLoading = ref(false);
const timelineQuery = reactive({ page: 1, size: 20 });
async function loadTimeline() { if (!selectedClient.value?.clientCode) return; timelineLoading.value = true; try { const page = (await getClientActivityTimeline(selectedClient.value.clientCode, timelineQuery)).data || {}; timeline.value = recordsOf(page); timelineTotal.value = totalOf(page); } finally { timelineLoading.value = false; } }

const insight = ref(null);
const insightLoading = ref(false);
const insightHistory = ref([]);
const canGenerateInsight = computed(() => !!selectedClient.value && selectedClient.value.ownerStaffCode === userNo.value);
const canReviewInsight = computed(() => {
  if (!selectedClient.value || !insight.value || insight.value.current) return false;
  if (['BOSS', 'OPERATOR', 'SUPER_ADMIN', 'SUPER'].includes(userStore.roleCode)) return true;
  return userStore.roleCode === 'DEPT_MANAGER';
});
const insightStatusText = { DRAFT: '待复核', REVIEWED: '已复核', CURRENT: '当前生效', ARCHIVED: '已归档' };
const insightStatusTag = { DRAFT: 'warning', REVIEWED: 'info', CURRENT: 'success', ARCHIVED: 'info' };
const generatedByText = { AI: 'AI 生成', RULE: '规则聚合', STAFF: '员工复核' };

async function loadInsight() {
  if (!selectedClient.value?.clientCode) return;
  insightLoading.value = true;
  try {
    const code = selectedClient.value.clientCode;
    const current = (await getClientInsight(code)).data || null;
    const page = (await getClientInsightHistory(code, { page: 1, size: 10 })).data || {};
    insight.value = current;
    insightHistory.value = recordsOf(page);
  } finally { insightLoading.value = false; }
}

const dimensionLabels = {
  reportAvailable: '经营分析报告', customerGroup: '客群', source: '客户来源',
  appointmentTotal: '预约总数', arrivedCount: '实际到店', noShowCount: '未到场',
  outingTotal: '外出服务', outingCompleted: '已完成外出', followTotal: '跟进记录', pendingFollows: '待回访',
  serviceRecords: '服务记录', materialCompleteness: '材料完整度', business: '经营分析', kpi: '经营指标',
};
const hiddenDimensionKeys = new Set(['reportNo', 'snapshotNo', 'submissionNo', 'clientCode', 'staffCode', 'ownerStaffCode']);
const dimensionValueLabels = Object.freeze({
  ENTERPRISE: '企业客户', PERSONAL: '个人客户',
  OURS: '我司录入', CHANNEL: '渠道录入', MINI: '小程序录入', MINI_STAFF_CREATE: '员工移动端录入', MINI_WECHAT_PHONE: '微信手机号建档',
  LEAD: '线索转化', BOSS: '老板录入', ADVISER: '顾问录入', VIP: '客户自主录入', WEB: 'Web 端录入', H5: 'H5 端录入',
  COMPLETE: '资料完整', INCOMPLETE: '资料待补充', PENDING: '待处理', VERIFIED: '已核验',
  true: '已生成', false: '未生成',
});
const insightMetaLabels = Object.freeze({
  HIGH: '高风险', MIDDLE: '中风险', MEDIUM: '中风险', LOW: '低风险',
  high: '高风险', middle: '中风险', medium: '中风险', low: '低风险',
  高: '高风险', 中: '中风险', 低: '低风险',
});

function dimensionValueText(value) {
  const mapped = dimensionValueLabels[String(value)];
  if (mapped) return mapped;
  if (typeof value === 'boolean') return value ? '是' : '否';
  return String(value);
}

/**
 * 画像维度仅展示有明确中文业务含义的字段；内部编号及未知技术字段不得直出页面。
 * 对服务记录等嵌套对象递归展开，避免对象被渲染为 [object Object] 或英文键值。
 */
function dimensionPairs(source, parentLabel = '') {
  if (!source || typeof source !== 'object' || Array.isArray(source)) return [];
  return Object.entries(source).flatMap(([key, value]) => {
    if (hiddenDimensionKeys.has(key) || value === null || value === undefined || value === '') return [];
    const label = dimensionLabels[key];
    if (!label) return [];
    if (typeof value === 'object' && !Array.isArray(value)) return dimensionPairs(value, label);
    if (Array.isArray(value)) return [];
    return [{ key: parentLabel ? `${parentLabel}·${label}` : label, value: dimensionValueText(value) }];
  });
}

/** 兼容历史快照中的字符串及结构化风险/建议，不向员工展示 level/type/tagType 等内部字段。 */
function insightItemText(item, fallback) {
  if (typeof item === 'string' && item.trim()) {
    const text = item.trim();
    // 兼容历史 Java Map 被 String.valueOf 后保存成“{level=..., content=...}”的快照。
    if (text.startsWith('{') && text.endsWith('}')) {
      try { return insightItemText(JSON.parse(text), fallback); } catch (_error) { /* 继续兼容旧 Map 文本 */ }
      const contentAt = text.indexOf('content=');
      if (contentAt >= 0) {
        const content = text.slice(contentAt + 'content='.length, -1).trim();
        const meta = text.match(/(?:level|type)=([^,}]+)/)?.[1]?.trim();
        const prefix = insightMetaLabels[meta] || meta;
        return content ? `${prefix ? `${prefix}：` : ''}${content}` : fallback;
      }
      return fallback;
    }
    return text;
  }
  if (!item || typeof item !== 'object') return fallback;
  const content = item.content || item.message || item.summary || item.description;
  if (typeof content !== 'string' || !content.trim()) return fallback;
  const meta = item.level || item.type;
  const prefix = insightMetaLabels[meta] || meta;
  return `${prefix ? `${prefix}：` : ''}${content.trim()}`;
}

async function onGenerateInsight() {
  saving.value = true;
  try { await generateClientInsight(selectedClient.value.clientCode); ElMessage.success('画像快照已生成，待复核'); await Promise.all([loadInsight(), loadTimeline()]); }
  finally { saving.value = false; }
}
async function onReviewInsight(decision) {
  let remark = '';
  if (decision === 'REJECT') {
    const result = await ElMessageBox.prompt('请填写驳回原因', '驳回画像快照', { inputPlaceholder: '驳回原因必填', inputValidator: (v) => (v && v.trim() ? true : '驳回原因必填') });
    remark = result.value;
  } else {
    await ElMessageBox.confirm('通过后该版本立即成为当前生效画像，原版本自动归档。', '复核通过', { type: 'warning' });
  }
  saving.value = true;
  try { await reviewClientInsight(selectedClient.value.clientCode, insight.value.snapshotNo, { decision, remark }); ElMessage.success(decision === 'APPROVE' ? '画像已生效' : '画像已驳回归档'); await Promise.all([loadInsight(), loadTimeline()]); }
  finally { saving.value = false; }
}

const followVisible = ref(false);
const followCustomerVisible = ref(false);
const followForm = reactive({ channelType: 'PHONE', resultCode: '', content: '', nextAction: '', nextFollowAt: '', customerVisibleSummary: '' });
function openFollow() { Object.assign(followForm, { channelType: 'PHONE', resultCode: '', content: '', nextAction: '', nextFollowAt: '', customerVisibleSummary: '' }); followCustomerVisible.value = false; followVisible.value = true; }
async function submitFollow() {
  if (!followForm.channelType || !followForm.resultCode.trim() || !followForm.content.trim()) return ElMessage.warning('跟进渠道、结果和内容必填');
  if (followCustomerVisible.value && !followForm.customerVisibleSummary.trim()) return ElMessage.warning('请填写客户可见摘要');
  saving.value = true;
  try { await createFollowRecord(selectedClient.value.clientCode, { ...followForm, visibility: followCustomerVisible.value ? 'CUSTOMER' : 'STAFF_ONLY' }); followVisible.value = false; ElMessage.success('跟进记录已保存'); await Promise.all([loadTimeline(), loadWorkbench(true)]); }
  finally { saving.value = false; }
}

function applyRouteQuery() {
  if (route.query.date) selectedDate.value = String(route.query.date);
  if (route.query.scope && scopeOptions.value.some((item) => item.value === String(route.query.scope).toUpperCase())) selectedScope.value = String(route.query.scope).toUpperCase();
  if (route.query.focus) focus.value = String(route.query.focus);
  if (route.query.serviceMethod) appointmentQuery.serviceMethod = String(route.query.serviceMethod);
  if (route.query.status) {
    appointmentQuery.status = String(route.query.status);
    outingQuery.status = String(route.query.status);
  }
}

function onScopeChange(value) {
  router.replace({ path: route.path, query: { ...route.query, scope: value, date: selectedDate.value } });
}

watch(() => route.fullPath, async () => {
  applyRouteQuery();
  await onTabChange(activeTab.value);
});

onMounted(async () => {
  applyRouteQuery();
  await loadWorkbench();
  if (activeTab.value !== 'daily') onTabChange(activeTab.value);
  if (route.query.clientCode) openClientReplay(String(route.query.clientCode));
  if (route.query.create === '1') {
    if (activeTab.value === 'appointments') openCreateAppointment();
    if (activeTab.value === 'outings') openCreateGeneralOuting();
  }
});
</script>

<style scoped>
.service-operations { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.service-header { align-items: center; }
.header-actions, .filter-row, .action-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.header-actions :deep(.el-date-editor) { width: 150px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.metric-card { border: 1px solid var(--loan-border); padding: 18px; text-align: left; cursor: pointer; transition: transform var(--loan-transition), border-color var(--loan-transition); }
.metric-card:hover { transform: translateY(-2px); border-color: var(--loan-primary); }
.metric-label, .metric-hint { display: block; color: var(--loan-text-muted); font-size: 12px; }
.metric-value { display: block; margin: 7px 0 5px; color: var(--loan-text); font-size: 28px; line-height: 1; }
.main-card { padding: 8px 18px 18px; min-width: 0; }
.scope-notice { display: flex; align-items: flex-start; gap: 8px; padding: 10px 12px; margin: 0 0 12px; border: 1px solid color-mix(in srgb, var(--loan-primary) 22%, var(--loan-border)); border-radius: var(--loan-radius-sm); background: var(--loan-primary-soft); color: var(--loan-text-secondary); font-size: 12px; line-height: 18px; }
.scope-notice :deep(svg) { flex: 0 0 auto; margin-top: 2px; color: var(--loan-primary); }
.list-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.list-panel { min-height: 230px; padding: 16px; border: 1px solid var(--loan-border); border-radius: var(--loan-radius); background: var(--loan-surface); }
.list-panel--focused { border-color: var(--loan-primary); box-shadow: 0 0 0 2px color-mix(in srgb, var(--loan-primary) 12%, transparent); }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.panel-head h3 { margin: 0; color: var(--loan-text); font-size: 15px; }
.panel-head > span, .panel-head > div > span { color: var(--loan-text-muted); font-size: 12px; }
.record-item, .client-option { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 11px 6px; color: var(--loan-text); background: transparent; border: 0; border-bottom: 1px solid var(--loan-border); text-align: left; cursor: pointer; }
.record-item:hover, .client-option:hover, .client-option.active { background: var(--loan-primary-soft); }
.record-item span:first-child, .client-option { min-width: 0; }
.record-item strong, .record-item small, .client-option strong, .client-option span, .client-option small { display: block; }
.record-item small, .client-option span, .client-option small, .cell-sub { margin-top: 4px; color: var(--loan-text-muted); font-size: 12px; }
.record-note { max-width: 45%; color: var(--loan-text-secondary); font-size: 12px; text-align: right; }
.filter-row { margin-bottom: 14px; }
.filter-row :deep(.el-select) { width: 180px; }
.text-link { padding: 0; color: var(--loan-primary); background: transparent; border: 0; cursor: pointer; text-align: left; }
.replay-layout { display: grid; grid-template-columns: 290px minmax(0, 1fr); gap: 18px; min-height: 520px; }
.client-picker { padding-right: 16px; border-right: 1px solid var(--loan-border); }
.client-results { max-height: 460px; margin-top: 10px; overflow-y: auto; }
.client-results { scrollbar-gutter: stable; }
.client-option { display: block; padding: 12px; border-radius: var(--loan-radius-sm); }
.replay-head { min-height: 42px; }
.insight-panel { padding: 14px 16px; margin-bottom: 16px; border: 1px solid var(--loan-border); border-radius: var(--loan-radius); background: var(--loan-surface); }
.insight-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.insight-head h4 { margin: 0 0 4px; font-size: 15px; font-weight: 500; color: var(--loan-text); }
.insight-head span { font-size: 12px; color: var(--loan-text-muted); }
.insight-actions { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.insight-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; margin-top: 12px; }
.insight-block { padding: 12px; border: 1px solid var(--loan-border); border-radius: var(--loan-radius-sm); }
.insight-block strong, .insight-versions strong { display: block; margin-bottom: 6px; font-size: 13px; font-weight: 500; color: var(--loan-text); }
.insight-block ul { margin: 0; padding-left: 16px; }
.insight-block li { margin-bottom: 4px; font-size: 13px; line-height: 1.6; color: var(--loan-text-secondary); }
.insight-risk li { color: var(--loan-warning-text); }
.insight-block p { margin: 0 0 4px; }
.insight-versions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: 12px; font-size: 12px; color: var(--loan-text-muted); }
.insight-versions strong { margin-bottom: 0; }
.version-chip { padding: 2px 8px; border: 1px solid var(--loan-border); border-radius: 999px; }
.version-chip.active { border-color: var(--loan-primary); color: var(--loan-primary); }
.timeline-card { padding: 12px 14px; border: 1px solid var(--loan-border); border-radius: var(--loan-radius-sm); background: var(--loan-surface); }
.timeline-card p { margin: 7px 0; color: var(--loan-text-secondary); }
.timeline-card span { color: var(--loan-text-muted); font-size: 12px; }
.dialog-form { margin-top: 18px; }
.checkin-box { display: grid; grid-template-columns: auto 1fr; align-items: center; gap: 10px; margin-top: 18px; padding: 16px; border: 1px solid var(--loan-border); border-radius: var(--loan-radius); }
.checkin-box small { grid-column: 2; color: var(--loan-text-muted); }
/* 打卡照片预览：限制最大高度避免大图撑爆弹窗 */
.checkin-photo { display: block; width: 100%; max-height: 60vh; object-fit: contain; border-radius: var(--loan-radius); }
/* 表格内的「照片」按钮紧跟在打卡时间后面，留一点间距 */
.cell-sub .text-link, td .text-link { margin-left: 6px; }
@media (max-width: 1100px) { .metric-grid { grid-template-columns: repeat(2, 1fr); } .replay-layout { grid-template-columns: 240px minmax(0, 1fr); } }
@media (max-width: 760px) { .service-header { align-items: flex-start; } .header-actions { width: 100%; } .metric-grid, .list-grid, .replay-layout { grid-template-columns: 1fr; } .client-picker { padding-right: 0; padding-bottom: 14px; border-right: 0; border-bottom: 1px solid var(--loan-border); } }
</style>
