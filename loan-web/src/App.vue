<template>
  <!-- on-demand 引入下通过 ConfigProvider 注入中文语言包（替代原 app.use(ElementPlus,{locale})） -->
  <el-config-provider :locale="zhCn">
    <router-view />
    <!-- 全局右键菜单（任意页面 openContextMenu 触发） -->
    <AppContextMenu />
  </el-config-provider>
</template>

<script setup>
import { onMounted } from 'vue';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import { useDictStore } from '@/store/dict';
import AppContextMenu from '@/components/AppContextMenu.vue';

/**
 * 根组件：应用启动时预加载枚举字典（后端统一定义枚举值），
 * 供全局 DictTag / DictSelect / dictLabel 解析中文语义。
 */
const dictStore = useDictStore();

onMounted(() => {
  dictStore.load();
});
</script>
