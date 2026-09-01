import { defineStore } from 'pinia';
import { fetchDictAll } from '@/api/dict';
import { KEYS, getStorageJSON, setStorageJSON } from '@/utils/storage';

const CACHE_KEY = KEYS.DICT;

/**
 * 枚举字典 Store。
 *
 * <p>契约：前端不硬编码枚举。展示与选择统一走这里，从 Pinia dict store 取后端字典。
 *
 * <p>性能/可靠性：localStorage 缓存首次同步可用，避免字典未 loaded 时 DictTag 显示英文 code。
 */
export const useDictStore = defineStore('dict', {
  state: () => ({
    /** 字典数据：{ type: [{ code, label, colorType }] } */
    dict: loadCache(),
    /** 是否已加载（远程） */
    loaded: !!loadCache(),
  }),
  getters: {
    /**
     * 取某字典类型的条目列表。
     * @param {string} type 字典类型（customerGroup / stepResult / totalResult / grade ...）
     */
    list: (state) => (type) => state.dict[type] || [],

    /**
     * 取某字典类型的 code→label 映射。
     */
    map: (state) => (type) => {
      const m = {};
      (state.dict[type] || []).forEach((it) => {
        m[it.code] = it.label;
      });
      return m;
    },
  },
  actions: {
    /** 拉取并缓存全部字典。 */
    async load() {
      try {
        const res = await fetchDictAll();
        this.dict = res.data || {};
        this.loaded = true;
        saveCache(this.dict);
      } catch (e) {
        // 字典加载失败：保留已有缓存或空字典，不阻断页面（拦截器已提示）
        if (!this.dict || !Object.keys(this.dict).length) this.dict = {};
      }
    },
  },
});

/** 同步读缓存（启动时立即可用，避免 DictTag 显示 code） */
function loadCache() {
  const obj = getStorageJSON(CACHE_KEY, null);
  return obj && typeof obj === 'object' ? obj : {};
}
/** 持久化缓存（远程加载成功后写入，下次启动即可用） */
function saveCache(dict) {
  setStorageJSON(CACHE_KEY, dict);
}
