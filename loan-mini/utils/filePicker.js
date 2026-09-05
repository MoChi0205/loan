const DEFAULT_EXTENSIONS = ['pdf', 'xls', 'xlsx', 'jpg', 'jpeg', 'png', 'webp'];
const MAX_SIZE = 20 * 1024 * 1024;

function extensionOf(name) {
  const matched = String(name || '').toLowerCase().match(/\.([a-z0-9]+)$/);
  return matched ? matched[1] : '';
}

export function validateMaterialFile(file, extensions = DEFAULT_EXTENSIONS) {
  if (!file || !file.path) throw new Error('未选择文件');
  if (Number(file.size || 0) > MAX_SIZE) throw new Error('单个文件不能超过 20MB');
  const ext = extensionOf(file.name || file.path);
  if (ext && extensions.indexOf(ext) < 0) throw new Error('仅支持 PDF、Excel 和图片文件');
  return file;
}

/**
 * 统一材料选择：优先选择任意文件，环境不支持时降级选择图片。
 * 返回 { path, name, size }，调用方只关心统一模型。
 */
export function chooseMaterialFile() {
  return new Promise((resolve, reject) => {
    const success = (res) => {
      const raw = (res.tempFiles && res.tempFiles[0]) || null;
      const path = (raw && (raw.path || raw.tempFilePath)) || (res.tempFilePaths && res.tempFilePaths[0]);
      try {
        resolve(validateMaterialFile({
          path,
          name: (raw && raw.name) || path || '',
          size: (raw && raw.size) || 0,
        }));
      } catch (error) {
        reject(error);
      }
    };
    const fail = (error) => reject(error && error.errMsg ? new Error(error.errMsg) : error);
    if (typeof uni.chooseFile === 'function') {
      uni.chooseFile({ count: 1, extension: DEFAULT_EXTENSIONS, success, fail });
      return;
    }
    if (typeof uni.chooseMessageFile === 'function') {
      uni.chooseMessageFile({ count: 1, type: 'file', extension: DEFAULT_EXTENSIONS, success, fail });
      return;
    }
    uni.chooseImage({ count: 1, sizeType: ['compressed'], success, fail });
  });
}
