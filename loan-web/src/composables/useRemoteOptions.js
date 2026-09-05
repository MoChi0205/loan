import { ref } from 'vue';

/**
 * 远程分页候选项公共状态：防抖、旧响应丢弃、追加分页、单项回显。
 * loader 接收 {keyword,page,size}，返回 PageResult 或 {data: PageResult}。
 */
export function useRemoteOptions(loader, options = {}) {
  const { pageSize = 20, debounce = 250, keywordKey = 'keyword', normalize = (item) => item } = options;
  const items = ref([]);
  const loading = ref(false);
  const finished = ref(false);
  const error = ref(null);
  let keyword = '';
  let page = 1;
  let sequence = 0;
  let timer;

  async function fetchPage(append = false) {
    const current = ++sequence;
    loading.value = true;
    error.value = null;
    try {
      const params = { page, size: pageSize, [keywordKey]: keyword || undefined };
      const res = await loader(params);
      if (current !== sequence) return;
      const payload = res?.data ?? res ?? {};
      const records = (payload.records || []).map(normalize);
      const merged = append ? items.value.concat(records) : records;
      const seen = new Set();
      items.value = merged.filter((item) => {
        const key = item.value ?? item.id;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      });
      finished.value = items.value.length >= Number(payload.total || 0) || records.length < pageSize;
    } catch (e) {
      if (current === sequence) {
        if (!append) items.value = [];
        error.value = e || new Error('候选项加载失败');
      }
    } finally {
      if (current === sequence) loading.value = false;
    }
  }

  function search(value = '') {
    clearTimeout(timer);
    keyword = String(value || '').trim();
    page = 1;
    finished.value = false;
    timer = setTimeout(() => fetchPage(false), debounce);
  }

  async function loadMore() {
    if (loading.value || finished.value) return;
    page += 1;
    await fetchPage(true);
  }

  function add(item) {
    const normalized = normalize(item);
    const key = normalized.value ?? normalized.id;
    if (!items.value.some((entry) => (entry.value ?? entry.id) === key)) items.value.unshift(normalized);
  }

  return { items, loading, finished, error, search, loadMore, fetchPage, add };
}
