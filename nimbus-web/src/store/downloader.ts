import { saveBlob } from '../api/client';

/**
 * 下载管理器(单例): 文件下载任务入队, 展示进度/网速/状态
 * 完成自动触发浏览器保存, 支持取消与重试, 与上传任务共用「传输管理」页
 */
export type DownloadStatus = 'downloading' | 'done' | 'failed';

export interface DownloadTask {
  /** 本地任务 id */
  id: string;
  /** 文件名 */
  fileName: string;
  /** 总字节(服务端未返回 Content-Length 时为 undefined, 如批量 zip) */
  fileSize?: number;
  /** 已下载字节 */
  loaded: number;
  /** 进度 0-100 */
  progress: number;
  /** 网速 bytes/s */
  speed: number;
  status: DownloadStatus;
  error?: string;
  /** 预览打开地址(内联 MIME), 无则通知不显示「打开」按钮 */
  openUrl?: string;
  /** 文件在云盘中的文件夹 id(供「打开所在位置」定位) */
  folderId?: string;
  /** 完成时间戳(ms), 用于「今日已完成」统计 */
  finishAt?: number;
}

export interface DownloadOptions {
  fileName: string;
  url: string;
  method?: 'GET' | 'POST';
  /** POST 请求体(JSON) */
  data?: unknown;
  /** 已知总大小(可省略, 由 Content-Length 推导) */
  size?: number;
  /** 预览打开地址(可选): 下载完成通知「打开」时跳转, 须为可内联渲染的地址 */
  openUrl?: string;
  /** 云盘文件夹 id(可选): 「打开所在位置」定位用 */
  folderId?: string;
}

const MAX_CONCURRENT = 2;

/** localStorage 持久化键: 刷新后恢复下载历史 */
const STORAGE_KEY = 'nimbus_downloads_v1';

class DownloadManager {
  tasks: DownloadTask[] = [];
  private xhrs = new Map<string, XMLHttpRequest>();
  /** 最近一次入队参数, 供重试 */
  private options = new Map<string, DownloadOptions>();
  private subscribers = new Set<() => void>();

  subscribe(fn: () => void) {
    this.subscribers.add(fn);
    return () => {
      this.subscribers.delete(fn);
    };
  }

  private emit() {
    this.subscribers.forEach((fn) => fn());
    this.persist();
  }

  /** 任务快照持久化, 供刷新后恢复历史 */
  private persist() {
    try {
      const plain = this.tasks.map((t) => ({
        id: t.id,
        fileName: t.fileName,
        fileSize: t.fileSize,
        loaded: t.loaded,
        progress: t.progress,
        status: t.status,
        openUrl: t.openUrl,
        folderId: t.folderId,
        finishAt: t.finishAt,
        error: t.error,
      }));
      localStorage.setItem(STORAGE_KEY, JSON.stringify(plain));
    } catch {
      /* 存储不可用时忽略 */
    }
  }

  /** 刷新后恢复: 已完成/失败保留为历史, 下载中标记为中断 */
  restore() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const list = JSON.parse(raw) as Array<Record<string, unknown>>;
      for (const item of list) {
        const task: DownloadTask = {
          id: String(item.id),
          fileName: String(item.fileName),
          fileSize: item.fileSize ? Number(item.fileSize) : undefined,
          loaded: Number(item.loaded ?? 0),
          progress: Number(item.progress ?? 0),
          status: (item.status === 'done' || item.status === 'failed' ? item.status : 'failed') as DownloadStatus,
          openUrl: item.openUrl ? String(item.openUrl) : undefined,
          folderId: item.folderId ? String(item.folderId) : undefined,
          finishAt: item.finishAt ? Number(item.finishAt) : undefined,
          error: item.status === 'done' || item.status === 'failed'
            ? (item.error ? String(item.error) : undefined)
            : '页面刷新已中断, 可重试',
          speed: 0,
        };
        this.tasks.push(task);
      }
      this.emit();
    } catch {
      /* 数据损坏忽略 */
    }
  }

  private runningCount() {
    return this.tasks.filter((t) => t.status === 'downloading').length;
  }

  /** 入队下载任务并自动开始 */
  enqueue(options: DownloadOptions) {
    const task: DownloadTask = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      fileName: options.fileName,
      fileSize: options.size,
      loaded: 0,
      progress: 0,
      speed: 0,
      status: 'downloading',
      openUrl: options.openUrl,
      folderId: options.folderId,
    };
    this.options.set(task.id, options);
    this.tasks.unshift(task);
    this.emit();
    this.start(task, options);
  }

  cancel(id: string) {
    this.xhrs.get(id)?.abort();
    // 中止回调移除任务
  }

  retry(id: string) {
    const options = this.options.get(id);
    if (!options) return;
    this.tasks = this.tasks.filter((t) => t.id !== id);
    this.options.delete(id);
    this.emit();
    this.enqueue(options);
  }

  clearFinished() {
    this.tasks = this.tasks.filter((t) => t.status === 'downloading');
    this.emit();
  }

  private start(task: DownloadTask, options: DownloadOptions) {
    // 并发控制: 超出排队
    const run = () => {
      if (this.runningCount() > MAX_CONCURRENT) {
        setTimeout(run, 300);
        return;
      }
      this.doStart(task, options);
    };
    run();
  }

  private doStart(task: DownloadTask, options: DownloadOptions) {
    const xhr = new XMLHttpRequest();
    this.xhrs.set(task.id, xhr);
    xhr.open(options.method ?? 'GET', options.url);
    const token = localStorage.getItem('nimbus_token');
    if (token) xhr.setRequestHeader('Authorization', token);
    if (options.data !== undefined) {
      xhr.setRequestHeader('Content-Type', 'application/json');
    }
    xhr.responseType = 'blob';
    let lastTick = performance.now();
    let lastBytes = 0;
    xhr.onprogress = (e) => {
      if (e.lengthComputable) {
        task.fileSize = e.total;
        task.loaded = e.loaded;
        task.progress = Math.round((e.loaded / e.total) * 100);
      } else {
        task.loaded = e.loaded;
      }
      const now = performance.now();
      const dt = now - lastTick;
      if (dt >= 400) {
        task.speed = ((e.loaded - lastBytes) / dt) * 1000;
        lastTick = now;
        lastBytes = e.loaded;
      }
      this.emit();
    };
    xhr.onload = () => {
      this.xhrs.delete(task.id);
      if (xhr.status === 200 && xhr.response instanceof Blob && xhr.response.size > 0) {
        task.status = 'done';
        task.loaded = task.fileSize ?? xhr.response.size;
        task.progress = 100;
        task.finishAt = Date.now();
        this.options.delete(task.id);
        saveBlob(xhr.response, task.fileName);
      } else if (xhr.status === 200 && xhr.response instanceof Blob && xhr.response.size === 0) {
        task.status = 'failed';
        task.error = '下载内容为空';
      } else {
        task.status = 'failed';
        task.error = `下载失败 HTTP ${xhr.status}`;
      }
      this.emit();
    };
    xhr.onerror = () => {
      this.xhrs.delete(task.id);
      task.status = 'failed';
      task.error = '网络异常, 下载失败';
      this.emit();
    };
    xhr.onabort = () => {
      this.xhrs.delete(task.id);
      // 用户取消: 任务移除
      this.tasks = this.tasks.filter((t) => t.id !== task.id);
      this.options.delete(task.id);
      this.emit();
    };
    if (options.data !== undefined) {
      xhr.send(JSON.stringify(options.data));
    } else {
      xhr.send();
    }
  }
}

export const downloader = new DownloadManager();

// 刷新后恢复持久化的下载历史
downloader.restore();

/** 网速格式化 */
export function formatSpeed(bytesPerSec: number): string {
  if (!bytesPerSec || bytesPerSec <= 0) return '—';
  if (bytesPerSec < 1024) return `${bytesPerSec.toFixed(0)} B/s`;
  if (bytesPerSec < 1024 * 1024) return `${(bytesPerSec / 1024).toFixed(1)} KB/s`;
  return `${(bytesPerSec / 1024 / 1024).toFixed(2)} MB/s`;
}

/** 单文件下载入队, 「打开」跳转内联预览地址(正确 MIME, 浏览器直接渲染) */
export function enqueueFileDownload(file: { id: string; fileName: string; fileSize?: string; folderId?: string }) {
  downloader.enqueue({
    fileName: file.fileName,
    url: `/api/netdisk/download/file/${file.id}`,
    size: file.fileSize ? Number(file.fileSize) : undefined,
    openUrl: `/api/netdisk/preview/${file.id}/content`,
    folderId: file.folderId,
  });
}

/** 批量 zip 下载入队(服务端实时打包) */
export function enqueueBatchDownload(fileIds: string[], folderIds: string[]) {
  downloader.enqueue({
    fileName: `nimbus-${Date.now()}.zip`,
    url: '/api/netdisk/download/batch',
    method: 'POST',
    data: { fileIds, folderIds },
  });
}

/** 分享文件下载入队(免登录, 提取码随 URL) */
export function enqueueShareDownload(code: string, password: string | undefined, file: { id: string; name: string }) {
  const query = password ? `?password=${encodeURIComponent(password)}` : '';
  downloader.enqueue({
    fileName: file.name,
    url: `/api/share/${code}/download/${file.id}${query}`,
  });
}