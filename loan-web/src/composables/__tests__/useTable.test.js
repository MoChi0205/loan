import { describe, expect, it } from 'vitest';
import { nextTick } from 'vue';
import { useTable } from '@/composables/useTable';

describe('useTable', () => {
  it('旧请求晚返回时不覆盖最新结果', async () => {
    const pending = [];
    const table = useTable(() => new Promise((resolve) => pending.push(resolve)));
    const first = table.load();
    const second = table.load();
    pending[1]({ records: [{ code: 'new' }], total: 1 });
    await second;
    await nextTick();
    pending[0]({ records: [{ code: 'old' }], total: 1 });
    await first;
    expect(table.data.value).toEqual([{ code: 'new' }]);
    expect(table.loading.value).toBe(false);
  });
});
