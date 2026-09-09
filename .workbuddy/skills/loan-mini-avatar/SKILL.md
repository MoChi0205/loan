---
name: loan-mini-avatar
description: 小程序微信头像与昵称填写能力封装（open-type=chooseAvatar、默认头像兜底、上传后端）。涉及首页/我的页头像展示时使用。
---

# 小程序微信头像能力（loan-mini-avatar）

## 适用场景
- 首页、我的页需要展示/编辑用户微信头像。
- 用户点击头像拉起微信头像选择，选择后本地预览并上传后端。
- 后端未返回头像时显示默认头像（首字占位，无需图片资源）。

## 关键约束（来自微信平台规则）
- 微信自 2022-10 起**回收** `wx.getUserProfile` / `wx.getUserInfo` 直接取头像昵称的能力。
- 改用「**头像昵称填写能力**」：必须由 `<button open-type="chooseAvatar" bind:chooseavatar="onChooseAvatar">` 触发，回调 `e.detail.avatarUrl` 返回**本地临时文件**路径。
- 该 button 仅微信小程序生效；H5 端无此能力，点击应 toast 提示。

## 实现步骤
1. **store 字段**：`store/user.js` 增加 `avatarUrl` state，`refreshProfile()` 从 `/api/mini/me.avatarUrl` 读取，`clear()` 重置，`setAvatar(url)` action 写内存 + 持久化 `loan_avatar`。
2. **模板**（首页示例）：
   ```html
   <button v-if="canChooseAvatar" class="avatar-ring" open-type="chooseAvatar"
           @chooseavatar="onChooseAvatar" hover-class="avatar-hover">
     <image v-if="avatarUrl" :src="avatarUrl" class="avatar-img" mode="aspectFill" />
     <text v-else class="avatar-text">{{ avatarChar }}</text>
   </button>
   <view v-else class="avatar-ring" @click="onAvatarFallback">
     <image v-if="avatarUrl" :src="avatarUrl" class="avatar-img" mode="aspectFill" />
     <text v-else class="avatar-text">{{ avatarChar }}</text>
   </view>
   ```
3. **canChooseAvatar**：`// #ifdef MP-WEIXIN` 返回 `!!store.token`，否则 `false`。
4. **回调**：
   ```js
   function onChooseAvatar(e) {
     const url = e.detail && e.detail.avatarUrl;
     if (!url) return;
     store.setAvatar(url);                       // 本地即时预览
     uploadAvatar(url).then(r => r && store.setAvatar(r)).catch(() => {});
   }
   ```
5. **上传**：`utils/avatar.js#uploadAvatar(tempFilePath)` 复用 `utils/wx.js#uploadImage` 上传到 `/api/mini/avatar`；接口未就绪/失败返回 `null`（不阻断本地预览）。

## 样式要点
- 头像容器：圆形 `84rpx`，`background: var(--glass-bg-deep)`，`border: 3rpx solid var(--glass-edge)`，`overflow: hidden`。
- `button` 默认有 padding/border，须 `padding:0; margin:0;` 且 `::after { border:none }`。
- 默认头像文字：`color: var(--text-secondary); font-size: 32rpx; font-weight: 600`。

## 默认头像策略
- 优先 `store.avatarUrl`（后端/本地选择）。
- 为空 → 首字占位（圆形玻璃底 + 姓名首字），**不依赖额外 PNG 资源**。
- 如需图片版默认头像，必须先将资源纳入 `loan-mini/static/` 并在提交前通过资源存在性检查；当前默认使用微信头像能力与纯色占位，不引用不存在的固定文件。

## 注意
- 临时路径 `avatarUrl` 仅本次会话有效；刷新后由 `/api/mini/me` 覆盖（后端有值优先）。
- 上传接口 `/api/mini/avatar` 需后端配合；未实现时本地预览仍工作。
