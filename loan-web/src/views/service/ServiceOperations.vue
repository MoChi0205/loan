<template>
  <div class="service-operations">
    <div class="loan-page-header service-header">
      <div>
        <h2 class="loan-page-title">客户服务</h2>
        <p class="loan-page-subtitle">来访、预约、外出与客户跟进统一协同</p>
      </div>
      <div class="header-actions">
        <el-date-picker v-model="selectedDate" type="date" value-format="YYYY-MM-DD" :clearable="false" aria-label="业务日期" />
        <el-button :loading="refreshing" @click="refreshCurrent">刷新</el-button>
        <el-button type="primary" @click="openCreateAppointment">创建预约</el-button>
      </div>
    </div>

    <div class="metric-grid" v-loading="workbenchLoading">
      <button v-for="item in metrics" :key="item.key" class="metric-card loan-card" type="button" @click="goMetric(item)">
        <span class="metric-label">{{ item.label }}</span>
        <strong class="metric-value">{{ item.value }}</strong>
        <span class="metric-hint">{{ item.hint }}</span>
      </button>
    </div>

    <div class="loan-card main-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="今日服务台" name="daily">
          <div class="list-grid" v-loading="workbenchLoading">
            <section class="list-panel">
              <div class="panel-head"><h3>今日来访</h3><span>{{ totalOf(workbench.companyVisits) }} 人</span></div>
              <button v-for="row in recordsOf(workbench.companyVisits)" :key="row.appointmentNo" class="record-item" type="button" @click="openClientReplay(row.clientCode)">
                <span><strong>{{ row.customerName || row.clientCode }}</strong><small>{{ timeOnly(row.scheduledStart) }} · {{ row.hostStaffName || row.hostStaffCode }}</small></span>
                <el-tag size="small" :type="appointmentTag(row.status)">{{ appointmentStatusText[row.status] || row.status }}</el-tag>
              </button>
              <el-empty v-if="!recordsOf(workbench.companyVisits).length" description="当日暂无到公司服务" :image-size="52" />
            </section>
            <section class="list-panel">
              <div class="panel-head"><h3>外出服务</h3><span>{{ totalOf(workbench.staffOutings) }} 人</span></div>
              <button v-for="row in recordsOf(workbench.staffOutings)" :key="row.outingNo" class="record-item" type="button" @click="openClientReplay(row.clientCode)">
                <span><strong>{{ row.staffName || row.staffCode }}</strong><small>{{ timeOnly(row.plannedStart) }} · {{ row.customerName || row.clientCode }}</small></span>
                <el-tag size="small" :type="outingTag(row.status)">{{ outingStatusText[row.status] || row.status }}</el-tag>
              </button>
              <el-empty v-if="!recordsOf(workbench.staffOutings).length" description="当日暂无上门外出" :image-size="52" />
            </section>
            <section class="list-panel">
              <div class="panel-head"><h3>待回访</h3><span>{{ totalOf(workbench.pendingFollows) }} 项</span></div>
              <button v-for="row in recordsOf(workbench.pendingFollows)" :key="row.followNo" class="record-item" type="button" @click="openClientReplay(row.clientCode)">
                <span><strong>{{ row.customerName || row.clientCode }}</strong><small>{{ formatDateTime(row.nextFollowAt) }}</small></span>
                <span class="record-note">{{ row.nextAction || '待跟进' }}</span>
              </button>
              <el-empty v-if="!recordsOf(workbench.pendingFollows).length" description="当日暂无待回访" :image-size="52" />
            </section>
            <section class="list-panel">
              <div class="panel-head"><h3>活跃工单</h3><span>{{ totalOf(workbench.activeOrders) }} 单</span></div>
              <button v-for="row in recordsOf(workbench.activeOrders)" :key="row.orderNo" class="record-item" type="button" @click="openClientReplay(row.clientCode)">
                <span><strong>{{ row.customerName || row.clientCode }}</strong><small>{{ row.orderNo }} · {{ formatDateTime(row.updatedAt) }}</small></span>
                <el-tag size="small" type="info">{{ row.status }}</el-tag>
              </button>
              <el-empty v-if="!recordsOf(workbench.activeOrders).length" description="暂无活跃工单" :image-size="52" />
            </section>
          </div>
        </el-tab-pane>

        <el-tab-pane label="预约来访" name="appointments">
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
              <template #default="{ row }"><button class="text-link" @click="openClientReplay(row.clientCode)">{{ row.customerName || row.clientCode }}</button><div class="cell-sub">{{ row.contactName || '—' }} {{ row.contactPhoneMasked || '' }}</div></template>
            </el-table-column>
            <el-table-column label="服务安排" min-width="210"><template #default="{ row }"><div>{{ methodText[row.serviceMethod] || row.serviceMethod }}</div><div class="cell-sub">{{ formatDateTime(row.scheduledStart) }} 至 {{ timeOnly(row.scheduledEnd) }}</div></template></el-table-column>
            <el-table-column label="顾问/地点" min-width="160"><template #default="{ row }"><div>{{ row.hostStaffName || row.hostStaffCode }}</div><div class="cell-sub">{{ row.locationName || '线上服务' }}</div></template></el-table-column>
            <el-table-column label="状态" width="130"><template #default="{ row }"><el-tag :type="appointmentTag(row.status)" size="small">{{ appointmentStatusText[row.status] || row.status }}</el-tag><div class="cell-sub">客户{{ row.customerConfirmStatus === 'CONFIRMED' ? '已确认' : '待确认' }}</div></template></el-table-column>
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <div class="action-row">
                  <el-button v-if="canConfirm(row)" link type="primary" @click="runAppointmentAction('confirm', row)">确认预约</el-button>
                  <el-button v-if="canArrive(row)" link type="primary" @click="runAppointmentAction('arrive', row)">到店</el-button>
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
        </el-tab-pane>

        <el-tab-pane label="员工外出" name="outings">
          <div class="filter-row">
            <el-select v-model="outingQuery.status" clearable placeholder="全部状态" @change="loadOutings">
              <el-option v-for="(label, code) in outingStatusText" :key="code" :label="label" :value="code" />
            </el-select>
          </div>
          <el-table v-loading="outingLoading" :data="outings" stripe row-key="outingNo">
            <el-table-column label="员工" min-width="130"><template #default="{ row }"><div>{{ row.staffName || row.staffCode }}</div><div class="cell-sub">{{ row.deptCode || '—' }}</div></template></el-table-column>
            <el-table-column label="客户" min-width="150"><template #default="{ row }"><button class="text-link" @click="openClientReplay(row.clientCode)">{{ row.customerName || row.clientCode }}</button></template></el-table-column>
            <el-table-column label="计划时间" min-width="190"><template #default="{ row }">{{ formatDateTime(row.plannedStart) }}<div class="cell-sub">至 {{ timeOnly(row.plannedEnd) }}</div></template></el-table-column>
            <el-table-column label="目的地/目的" min-width="190"><template #default="{ row }"><div>{{ row.destination }}</div><div class="cell-sub">{{ row.purpose }}</div></template></el-table-column>
            <el-table-column label="打卡" min-width="210"><template #default="{ row }"><div>出发：{{ row.actualDepartedAt ? formatDateTime(row.actualDepartedAt) : '未打卡' }}<button v-if="row.departedPhotoKey" class="text-link" type="button" @click="viewPhoto(row, 'DEPART', '出发打卡照片')">照片</button></div><div class="cell-sub">返回：{{ row.actualReturnedAt ? formatDateTime(row.actualReturnedAt) : '未打卡' }}<button v-if="row.returnedPhotoKey" class="text-link" type="button" @click="viewPhoto(row, 'RETURN', '返回打卡照片')">照片</button></div></template></el-table-column>
            <el-table-column label="审核" min-width="170"><template #default="{ row }"><div>{{ row.reviewerName || (row.status === 'PENDING_REVIEW' ? '待审核' : '—') }}</div><div class="cell-sub" :title="row.reviewRemark || ''">{{ row.reviewedAt ? formatDateTime(row.reviewedAt) : '' }}<span v-if="row.reviewRemark"> · {{ row.reviewRemark }}</span></div></template></el-table-column>
            <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="outingTag(row.status)" size="small">{{ outingStatusText[row.status] || row.status }}</el-tag></template></el-table-column>
            <el-table-column label="操作" width="170" fixed="right"><template #default="{ row }"><template v-if="isOwnOuting(row)"><el-button v-if="row.status === 'READY'" link type="primary" @click="checkIn(row, 'depart')">出发打卡</el-button><el-button v-if="row.status === 'IN_PROGRESS'" link type="success" @click="checkIn(row, 'return')">返回打卡</el-button><el-button v-if="row.status === 'REJECTED'" link type="warning" @click="openResubmit(row)">重新提交</el-button><span v-if="row.status === 'PENDING_REVIEW'" class="cell-sub">待主管审核</span></template><template v-else-if="canReviewOuting(row)"><el-button v-if="row.status === 'PENDING_REVIEW'" link type="success" @click="onApprove(row)">通过</el-button><el-button v-if="row.status === 'PENDING_REVIEW'" link type="danger" @click="openReject(row)">驳回</el-button><span v-if="row.status !== 'PENDING_REVIEW'" class="cell-sub">仅可查看</span></template><span v-else class="cell-sub">仅可查看</span></template></el-table-column>
          </el-table>
          <AppPagination v-model:page="outingQuery.page" v-model:size="outingQuery.size" :total="outingTotal" @change="loadOutings" />
        </el-tab-pane>

        <el-tab-pane label="客户回放" name="replay">
          <div class="replay-layout">
            <aside class="client-picker">
              <el-input v-model="clientKeyword" clearable placeholder="姓名、企业名或手机号" @keyup.enter="searchClients"><template #append><el-button :loading="clientLoading" @click="searchClients">查询</el-button></template></el-input>
              <div class="client-results">
                <button v-for="row in clientOptions" :key="row.clientCode" type="button" class="client-option" :class="{ active: selectedClient?.clientCode === row.clientCode }" @click="selectClient(row)">
                  <strong>{{ row.enterpriseName || row.contactName || row.clientCode }}</strong><span>{{ row.contactName || '—' }} · {{ row.phone || '未绑定手机号' }}</span><small>{{ row.ownerStaffName || '暂未分配顾问' }}</small>
                </button>
                <el-empty v-if="clientSearched && !clientOptions.length" description="未找到可见客户" :image-size="48" />
              </div>
            </aside>
            <section class="timeline-panel">
              <div class="panel-head replay-head"><div><h3>{{ selectedClientName }}</h3><span v-if="selectedClient">{{ selectedClient.clientCode }}</span></div><el-button v-if="canAddFollow" type="primary" @click="openFollow">新增跟进</el-button></div>

              <div v-if="selectedClient" v-loading="insightLoading" class="insight-panel">
                <div class="insight-head">
                  <div>
                    <h4>客户画像</h4>
                    <span v-if="insight">第 {{ insight.snapshotVersion }} 版 · {{ generatedByText[insight.generatedBy] || insight.generatedBy }} · {{ formatDateTime(insight.generatedAt) }}</span>
                    <span v-else>尚未生成画像快照</span>
                  </div>
                  <div class="insight-actions">
                    <el-tag v-if="insight" :type="insightStatusTag[insight.status]" size="small">{{ insightStatusText[insight.status] || insight.status }}</el-tag>
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
                      <ul v-if="insight.riskFlags?.length" class="insight-risk"><li v-for="(item, idx) in insight.riskFlags" :key="idx">{{ item }}</li></ul>
                      <p v-else class="cell-sub">暂无风险提示</p>
                    </div>
                    <div class="insight-block">
                      <strong>经营建议</strong>
                      <ul v-if="insight.advice?.length"><li v-for="(item, idx) in insight.advice" :key="idx">{{ item }}</li></ul>
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
                    <span v-for="item in insightHistory" :key="item.snapshotNo" class="version-chip" :class="{ active: item.current }">第 {{ item.snapshotVersion }} 版 · {{ insightStatusText[item.status] || item.status }}</span>
                  </div>
                </template>
                <p v-else class="cell-sub">尚未生成画像快照；生成后由主管复核生效，客户端只看到脱敏摘要。</p>
              </div>

              <el-timeline v-if="timeline.length" v-loading="timelineLoading">
                <el-timeline-item v-for="item in timeline" :key="item.eventNo" :timestamp="formatDateTime(item.happenedAt)" placement="top">
                  <div class="timeline-card"><strong>{{ eventText[item.eventType] || item.eventType }}</strong><p>{{ item.summary || '—' }}</p><span>{{ actorText[item.actorType] || item.actorType }} · {{ item.visibility === 'CUSTOMER' ? '客户可见' : '仅员工可见' }}</span></div>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else :description="selectedClient ? '暂无活动记录' : '请先查询并选择客户'" />
              <AppPagination v-if="timelineTotal" v-model:page="timelineQuery.page" v-model:size="timelineQuery.size" :total="timelineTotal" @change="loadTimeline" />
            </section>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <AppDialog v-model:visible="createVisible" title="创建客户预约" width="680px" :loading="saving" @confirm="submitAppointment">
      <el-alert title="员工代客创建后，客户需先确认；预约开始前 5 分钟内的变更需走异常处理并留痕。" type="info" :closable="false" show-icon />
      <el-form ref="appointmentFormRef" :model="appointmentForm" :rules="appointmentRules" label-width="100px" class="dialog-form">
        <el-form-item label="客户" prop="clientCode"><el-select v-model="appointmentForm.clientCode" filterable remote :remote-method="loadClientOptions" :loading="clientSelectLoading" placeholder="搜索姓名、企业名或手机号" style="width:100%" @visible-change="(v) => v && loadClientOptions('')"><el-option v-for="row in appointmentClientOptions" :key="row.clientCode" :label="`${row.enterpriseName || row.contactName || row.clientCode} · ${row.contactName || '—'}`" :value="row.clientCode" /></el-select></el-form-item>
        <el-form-item label="服务顾问" prop="hostStaffCode"><el-input v-if="isAdviser" :model-value="`${userStore.displayName} · ${userNo}`" disabled /><el-select v-else v-model="appointmentForm.hostStaffCode" filterable remote :remote-method="loadStaffOptions" :loading="staffLoading" placeholder="搜索姓名或工号" style="width:100%" @visible-change="(v) => v && loadStaffOptions('')"><el-option v-for="row in staffOptions" :key="row.staffCode" :label="`${row.staffName} · ${row.staffCode}`" :value="row.staffCode" /></el-select></el-form-item>
        <el-form-item label="服务方式" prop="serviceMethod"><el-radio-group v-model="appointmentForm.serviceMethod"><el-radio-button v-for="(label, code) in methodText" :key="code" :label="code">{{ label }}</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="服务时间" prop="timeRange"><el-date-picker v-model="appointmentForm.timeRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" style="width:100%" /></el-form-item>
        <el-form-item v-if="needsLocation" label="地点名称" prop="locationName"><el-input v-model="appointmentForm.locationName" placeholder="公司或拜访地点名称" /></el-form-item>
        <el-form-item v-if="needsLocation" label="详细地址"><el-input v-model="appointmentForm.locationDetail" placeholder="详细地址（客户可见）" /></el-form-item>
        <el-form-item label="客户提示"><el-input v-model="appointmentForm.customerVisibleNote" type="textarea" :rows="2" placeholder="客户可见的下一步安排" /></el-form-item>
        <el-form-item label="内部备注"><el-input v-model="appointmentForm.internalNote" type="textarea" :rows="2" placeholder="仅公司员工可见" /></el-form-item>
      </el-form>
    </AppDialog>

    <AppDialog v-model:visible="changeVisible" :title="changeMode.includes('reschedule') ? '预约改期' : '取消预约'" width="560px" :loading="saving" @confirm="submitChange">
      <el-form label-width="90px">
        <el-form-item v-if="changeMode.includes('reschedule')" label="新时间" required><el-date-picker v-model="changeForm.timeRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item>
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
        <el-form-item label="申请人"><span>{{ rejectTarget?.staffName || rejectTarget?.staffCode }}</span></el-form-item>
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

    <AppDialog v-model:visible="outingCreateVisible" title="提交上门外出申请" width="540px" :loading="saving" @confirm="submitOuting">
      <el-alert title="只能由预约的主服务顾问本人提交，不接受他人代录；提交后需主管审核通过，才能出发与返回打卡。" type="warning" :closable="false" show-icon />
      <el-form label-width="90px" class="dialog-form">
        <el-form-item label="客户"><span>{{ outingCreateTarget?.customerName || outingCreateTarget?.clientCode }}</span></el-form-item>
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
import { pageClients } from '@/api/client';
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
const activeTab = ref('daily');
const refreshing = ref(false);
const saving = ref(false);

const methodText = Object.freeze({ COMPANY_ON_SITE: '到公司现场', HOME_VISIT: '上门拜访', VIDEO_MEETING: '视频会议', PHONE_CONSULT: '电话咨询' });
const appointmentStatusText = Object.freeze({ REQUESTED: '待确认', CONFIRMED: '已确认', ARRIVED: '已到店', SERVING: '服务中', COMPLETED: '已完成', CANCELLED: '已取消', NO_SHOW: '未到场', RESCHEDULED: '已改期' });
const outingStatusText = Object.freeze({ DRAFT: '草稿', PENDING_REVIEW: '待审核', REJECTED: '已驳回', READY: '待出发', IN_PROGRESS: '外出中', COMPLETED: '已返回', CANCELLED: '已取消' });
const followChannelText = Object.freeze({ PHONE: '电话咨询', COMPANY_ON_SITE: '到公司现场', HOME_VISIT: '上门拜访', VIDEO_MEETING: '视频会议', WECOM: '企业微信', OTHER: '其他' });
const eventText = Object.freeze({ APPOINTMENT_CREATED: '创建预约', CUSTOMER_CONFIRMED: '客户确认', APPOINTMENT_CONFIRMED: '预约确认', CUSTOMER_ARRIVED: '客户到店', SERVICE_STARTED: '开始服务', SERVICE_COMPLETED: '服务完成', APPOINTMENT_CANCELLED: '取消预约', APPOINTMENT_NO_SHOW: '客户未到场', APPOINTMENT_RESCHEDULED: '预约改期', OUTING_SUBMITTED: '提交外出申请', OUTING_APPROVED: '外出审核通过', OUTING_REJECTED: '外出申请驳回', OUTING_DEPARTED: '出发打卡', OUTING_RETURNED: '返回打卡', FOLLOW_RECORDED: '客户跟进' });
const actorText = Object.freeze({ STAFF: '员工', CUSTOMER: '客户', SYSTEM: '系统' });
function recordsOf(page) { return Array.isArray(page?.records) ? page.records : []; }
function totalOf(page) { return Number(page?.total || 0); }
function timeOnly(value) { const text = formatDateTime(value); return text === '-' ? text : text.slice(11, 16); }
function appointmentTag(status) { return ({ COMPLETED: 'success', CANCELLED: 'info', NO_SHOW: 'danger', SERVING: 'warning', ARRIVED: 'warning' })[status] || ''; }
function outingTag(status) { return ({ COMPLETED: 'success', CANCELLED: 'info', IN_PROGRESS: 'warning', PENDING_REVIEW: 'warning', REJECTED: 'danger' })[status] || ''; }

const workbench = ref({});
const workbenchLoading = ref(false);
const metrics = computed(() => [
  { key: 'visits', label: '今日来访', value: totalOf(workbench.value.companyVisits), hint: '仅到公司现场', tab: 'appointments', method: 'COMPANY_ON_SITE' },
  { key: 'outings', label: '外出服务', value: totalOf(workbench.value.staffOutings), hint: '上门拜访名单', tab: 'outings' },
  { key: 'follows', label: '待回访', value: totalOf(workbench.value.pendingFollows), hint: '按计划跟进客户', tab: 'daily' },
  { key: 'orders', label: '活跃工单', value: totalOf(workbench.value.activeOrders), hint: '进行中的服务工单', tab: 'daily' },
]);
async function loadWorkbench() {
  workbenchLoading.value = true;
  try { workbench.value = (await getDailyServiceLists({ date: selectedDate.value, page: 1, size: 20 })).data || {}; }
  finally { workbenchLoading.value = false; }
}
function goMetric(item) { activeTab.value = item.tab; if (item.method) appointmentQuery.serviceMethod = item.method; onTabChange(item.tab); }

const appointments = ref([]);
const appointmentTotal = ref(0);
const appointmentLoading = ref(false);
const appointmentQuery = reactive({ page: 1, size: 20, serviceMethod: '', status: '' });
async function loadAppointments() {
  appointmentLoading.value = true;
  try { const page = (await pageAppointments({ ...appointmentQuery, date: selectedDate.value })).data || {}; appointments.value = recordsOf(page); appointmentTotal.value = totalOf(page); }
  finally { appointmentLoading.value = false; }
}
function canOperate(row) { return !!userNo.value && row.hostStaffCode === userNo.value; }
function canConfirm(row) { return canOperate(row) && row.status === 'REQUESTED' && row.customerConfirmStatus === 'CONFIRMED'; }
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
  await Promise.all([loadAppointments(), loadWorkbench()]);
}

const outings = ref([]);
const outingTotal = ref(0);
const outingLoading = ref(false);
const outingQuery = reactive({ page: 1, size: 20, status: '' });
async function loadOutings() {
  outingLoading.value = true;
  try { const page = (await pageOutings({ ...outingQuery, date: selectedDate.value })).data || {}; outings.value = recordsOf(page); outingTotal.value = totalOf(page); }
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
async function refreshCurrent() { refreshing.value = true; try { await onTabChange(activeTab.value); } finally { refreshing.value = false; } }
watch(selectedDate, () => { appointmentQuery.page = 1; outingQuery.page = 1; if (activeTab.value === 'daily') loadWorkbench(); else Promise.all([loadWorkbench(), onTabChange(activeTab.value)]); });

const createVisible = ref(false);
const appointmentFormRef = ref();
const appointmentForm = reactive({ clientCode: '', hostStaffCode: '', serviceMethod: 'COMPANY_ON_SITE', timeRange: [], locationName: '', locationDetail: '', customerVisibleNote: '', internalNote: '' });
const appointmentRules = { clientCode: [{ required: true, message: '请选择客户', trigger: 'change' }], hostStaffCode: [{ required: true, message: '请选择服务顾问', trigger: 'change' }], serviceMethod: [{ required: true, message: '请选择服务方式', trigger: 'change' }], timeRange: [{ type: 'array', required: true, min: 2, message: '请选择服务起止时间', trigger: 'change' }], locationName: [{ validator: (_rule, value, callback) => needsLocation.value && !value ? callback(new Error('请填写服务地点')) : callback(), trigger: 'blur' }] };
const needsLocation = computed(() => ['COMPANY_ON_SITE', 'HOME_VISIT'].includes(appointmentForm.serviceMethod));
const appointmentClientOptions = ref([]);
const clientSelectLoading = ref(false);
const staffOptions = ref([]);
const staffLoading = ref(false);
function openCreateAppointment() { Object.assign(appointmentForm, { clientCode: '', hostStaffCode: userNo.value, serviceMethod: 'COMPANY_ON_SITE', timeRange: [], locationName: '', locationDetail: '', customerVisibleNote: '', internalNote: '' }); createVisible.value = true; loadClientOptions(''); if (!isAdviser.value) loadStaffOptions(''); }
async function loadClientOptions(keyword) { clientSelectLoading.value = true; try { const page = (await pageClients({ keyword, page: 1, size: 20, scope: userStore.roleCode === 'ADVISER' ? 'MY' : 'ALL' })).data || {}; appointmentClientOptions.value = recordsOf(page); } finally { clientSelectLoading.value = false; } }
async function loadStaffOptions(keyword) { staffLoading.value = true; try { const params = { keyword, page: 1, size: 50 }; if (userStore.roleCode === 'DEPT_MANAGER') params.deptCode = userStore.user?.deptCode; const page = (await staffPage(params)).data || {}; staffOptions.value = recordsOf(page).filter((row) => !row.status || row.status === 'ACTIVE'); } finally { staffLoading.value = false; } }
async function submitAppointment() {
  await appointmentFormRef.value?.validate();
  saving.value = true;
  try {
    const data = { ...appointmentForm, scheduledStart: appointmentForm.timeRange[0], scheduledEnd: appointmentForm.timeRange[1] };
    delete data.timeRange;
    await createAppointment(data);
    createVisible.value = false;
    ElMessage.success('预约已创建，等待客户确认');
    activeTab.value = 'appointments';
    await Promise.all([loadAppointments(), loadWorkbench()]);
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
    changeVisible.value = false; ElMessage.success(changeMode.value.includes('reschedule') ? '改期成功' : '取消成功'); await Promise.all([loadAppointments(), loadWorkbench()]);
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
function locate() {
  if (!navigator.geolocation) return ElMessage.error('当前浏览器不支持定位');
  locating.value = true;
  navigator.geolocation.getCurrentPosition((position) => { Object.assign(checkInForm, { latitude: position.coords.latitude, longitude: position.coords.longitude, accuracyMeters: position.coords.accuracy, locationText: `经度 ${position.coords.longitude.toFixed(6)}，纬度 ${position.coords.latitude.toFixed(6)}`, collectedAt: new Date(position.timestamp).toISOString().slice(0, 19) }); locating.value = false; }, (error) => { locating.value = false; ElMessage.error(error.code === 1 ? '定位权限未开启，请允许浏览器访问位置' : '定位失败，请重试'); }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 });
}
async function submitCheckIn() {
  if (checkInForm.latitude == null) return ElMessage.warning('请先获取当前位置');
  if (!checkInForm.photoFileKey) return ElMessage.warning('请先上传现场照片');
  saving.value = true;
  try { const fn = checkInMode.value === 'depart' ? departOuting : returnOuting; await fn(checkInTarget.value.outingNo, { ...checkInForm }); checkInVisible.value = false; ElMessage.success(`${checkInMode.value === 'depart' ? '出发' : '返回'}打卡成功`); await Promise.all([loadOutings(), loadWorkbench()]); }
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
    await Promise.all([loadOutings(), loadWorkbench()]);
  } finally { saving.value = false; }
}
async function onApprove(row) {
  await ElMessageBox.confirm(`确认通过「${row.staffName || row.staffCode}」的外出申请？通过后其可完成出发与返回打卡。`, '审核确认');
  await approveOuting(row.outingNo, '');
  ElMessage.success('已通过审核');
  await Promise.all([loadOutings(), loadWorkbench()]);
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
    await Promise.all([loadOutings(), loadWorkbench()]);
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
const outingCreateForm = reactive({ destination: '', purpose: '', internalNote: '' });
function openCreateOuting(row) {
  outingCreateTarget.value = row;
  Object.assign(outingCreateForm, { destination: row.locationDetail || row.locationName || '', purpose: '', internalNote: '' });
  outingCreateVisible.value = true;
}
async function submitOuting() {
  if (!outingCreateForm.destination.trim() || !outingCreateForm.purpose.trim()) return ElMessage.warning('目的地和拜访目的必填');
  saving.value = true;
  try {
    await createOuting({ appointmentNo: outingCreateTarget.value.appointmentNo, ...outingCreateForm });
    outingCreateVisible.value = false;
    ElMessage.success('外出申请已提交，待主管审核通过后可打卡');
    await Promise.all([loadAppointments(), loadWorkbench()]);
  } finally { saving.value = false; }
}

const clientKeyword = ref('');
const clientOptions = ref([]);
const clientLoading = ref(false);
const clientSearched = ref(false);
const selectedClient = ref(null);
const selectedClientName = computed(() => selectedClient.value ? (selectedClient.value.enterpriseName || selectedClient.value.contactName || selectedClient.value.clientCode) : '客户活动回放');
const canAddFollow = computed(() => selectedClient.value?.ownerStaffCode === userNo.value);
async function searchClients() { clientLoading.value = true; clientSearched.value = true; try { const page = (await pageClients({ keyword: clientKeyword.value, page: 1, size: 20, scope: userStore.roleCode === 'ADVISER' ? 'MY' : 'ALL' })).data || {}; clientOptions.value = recordsOf(page); } finally { clientLoading.value = false; } }
function selectClient(row) { selectedClient.value = row; timelineQuery.page = 1; router.replace({ query: { ...route.query, tab: 'replay', clientCode: row.clientCode } }); loadInsight(); loadTimeline(); }
function openClientReplay(clientCode) { activeTab.value = 'replay'; clientKeyword.value = clientCode; searchClients().then(() => { const row = clientOptions.value.find((item) => item.clientCode === clientCode) || { clientCode }; selectClient(row); }); }
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

/** 维度对象按「键：值」展开，未知结构不渲染，避免页面出现 [object Object]。 */
function dimensionPairs(source) {
  if (!source || typeof source !== 'object') return [];
  return Object.entries(source)
    .filter(([, value]) => value !== null && value !== undefined && typeof value !== 'object')
    .map(([key, value]) => ({ key: dimensionLabels[key] || key, value: String(value) }));
}
const dimensionLabels = {
  reportAvailable: '经营分析报告', customerGroup: '客群', source: '客户来源',
  appointmentTotal: '预约总数', arrivedCount: '实际到店', noShowCount: '未到场',
  outingTotal: '外出服务', outingCompleted: '已完成外出', followTotal: '跟进记录', pendingFollows: '待回访',
};

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
  try { await createFollowRecord(selectedClient.value.clientCode, { ...followForm, visibility: followCustomerVisible.value ? 'CUSTOMER' : 'STAFF_ONLY' }); followVisible.value = false; ElMessage.success('跟进记录已保存'); await Promise.all([loadTimeline(), loadWorkbench()]); }
  finally { saving.value = false; }
}

onMounted(async () => {
  const tab = String(route.query.tab || 'daily');
  if (['daily', 'appointments', 'outings', 'replay'].includes(tab)) activeTab.value = tab;
  await loadWorkbench();
  if (activeTab.value !== 'daily') onTabChange(activeTab.value);
  if (route.query.clientCode) openClientReplay(String(route.query.clientCode));
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
.list-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.list-panel { min-height: 230px; padding: 16px; border: 1px solid var(--loan-border); border-radius: var(--loan-radius); background: var(--loan-surface); }
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
