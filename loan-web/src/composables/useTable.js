import { reactive, ref } from 'vue';

/**
 * 列表页通用 hook（composable）：统一管理查询条件 / 分页 / 加载态 / 数据。
 *
 * <p>消除列表页样板代码（query/loading/data/total/load/search/reset 各自定义）。
 *
 * 用法：
 *   const { loading, data, total, query, load, onSearch, onReset } = useTable(
 *     pageProducts,
 *     { productName: '', bankName: '', status: '' },
 *   );
 *   onMounted(load);
 *
 *   // loader 接收当前 query（已含 page/size），返回后端 PageResult：{ records, total }
 *   async function pageProducts(query) {
 *     const res = await request({ url: '/api/...', params: query });
 *     return res.data; // { records, total }
 *   }
 */
export function useTable(loader, initialQuery = {}, options = {}) {
  const { pageSize = 10 } = options;
  const loading = ref(false);
  const data = ref([]);
  const total = ref(0);
  const query = reactive({
    page: 1,
    size: pageSize,
    sortBy: '',
    sortDir: '',
    ...initialQuery,
  });

  async function load() {
    if (typeof loader !== 'function') return;
    loading.value = true;
    try {
      const res = await loader(query);
      // 兼容两种返回：1) 直接 PageResult  2) 包裹在 { data: PageResult }
      const payload = res?.data ?? res;
      data.value = payload?.records ?? [];
      total.value = Number(payload?.total ?? 0);
    } catch (e) {
      data.value = [];
      total.value = 0;
    } finally {
      loading.value = false;
    }
  }

  function onSearch() {
    query.page = 1;
    return load();
  }

  function onReset() {
    Object.keys(query).forEach((k) => {
      if (k === 'page' || k === 'size') return;
      query[k] = initialQuery[k] ?? '';
    });
    query.page = 1;
    return load();
  }

  function onPageChange() {
    return load();
  }

  function onSizeChange() {
    query.page = 1;
    return load();
  }

  /**
   * el-table @sort-change 处理：列头点击正/倒序 → 写入 sortBy/sortDir → 重新加载（跨页排序）。
   * 用法：<el-table @sort-change="handleSortChange">
   * @param {{ prop?: string, order?: string|null }} ctx 排序上下文
   */
  function handleSortChange(ctx) {
    const prop = ctx?.prop;
    const order = ctx?.order;
    query.sortBy = order ? prop : '';
    query.sortDir = order === 'descending' ? 'desc' : order === 'ascending' ? 'asc' : '';
    query.page = 1;
    return load();
  }

  return {
    loading,
    data,
    total,
    query,
    load,
    onSearch,
    onReset,
    onPageChange,
    onSizeChange,
    handleSortChange,
  };
}
