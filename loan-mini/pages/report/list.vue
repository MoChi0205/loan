<template>
  <view class="wrapper-page theme-root" :data-theme="themeMode">
    <MyReportList :active="true" />
    <TabBar :current="store.isStaff ? 'clients' : 'report'" />
  </view>
</template>

<script setup>
/**
 * 兼容壳页：员工侧「我的报告」已并入「我的客户」合并页（D74）。
 *
 * <p>客户端仍以本页作为独立 tab（客户无「我的客户」概念）；
 * 员工侧保留本路由仅为不打断既有跳转（如报告详情返回、H5 深链），
 * 底部导航据此指向合并页 {@code clients}。
 */
import TabBar from '../../components/TabBar.vue';
import MyReportList from '../../components/MyReportList.vue';
import { useUserStore } from '../../store/user';
import { useThemeMode } from '../../theme';

const store = useUserStore();
const themeMode = useThemeMode();
</script>

<style scoped>
.wrapper-page {
  min-height: 100vh;
  background: var(--bg-page);
  box-sizing: border-box;
}
</style>
