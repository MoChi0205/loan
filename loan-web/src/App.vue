<template>
  <router-view />
  <!-- 全局右键菜单（任意页面 openContextMenu 触发） -->
  <AppContextMenu />
</template>

<script setup>
import { onMounted } from 'vue';
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
