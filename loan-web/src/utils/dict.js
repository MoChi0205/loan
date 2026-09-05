import { useDictStore } from '@/store/dict';

/**
 * 枚举解析工具：将后端下发的枚举 code 解析为中文 label / 选项 / 语义色。
 *
 * <p>契约：前端不硬编码枚举。展示与选择统一走这里，从 Pinia dict store 取后端字典。
 */

/**
 * code → 中文 label（未命中返回原 code）。
 *
 * @param {string} type 字典类型
 * @param {string} code 枚举编码
 * @returns {string} 中文语义
 */
export function dictLabel(type, code) {
  const store = useDictStore();
  return store.map(type)[code] ?? code ?? '-';
}

/**
 * 取某字典类型的下拉选项（label 中文、value 为 code）。
 *
 * @param {string} type 字典类型
 * @returns {Array<{label:string, value:string, colorType:string}>} 选项列表
 */
export function dictOptions(type) {
  const store = useDictStore();
  return (store.dict[type] || []).map((it) => ({
    label: it.label,
    value: it.code,
    colorType: it.colorType,
  }));
}

/**
 * code → 语义色类型（success/warning/danger/info/primary/muted），未命中返回 'muted'。
 *
 * @param {string} type 字典类型
 * @param {string} code 枚举编码
 * @returns {string} 语义色类型
 */
export function dictColor(type, code) {
  const store = useDictStore();
  const item = (store.dict[type] || []).find((it) => it.code === code);
  return item?.colorType || 'muted';
}
