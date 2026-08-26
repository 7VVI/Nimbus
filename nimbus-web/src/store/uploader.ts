import { uploadApi } from '../api';

/**
 * 分片上传管理器(单例): 秒传检测 -> 分片排队上传(并发 2) -> 合并
 * 支持暂停/恢复(会话内断点续传, 已上传分片缓存在内存)与取消/重试
 */
export type UploadStatus = 'waiting' | 'uploading' | 'paused' | 'done' | 'failed';

export interface UploadTask {
  /** 本地任务 id */
  id: string;
  /** 文件名 */
  fileName: string;
  /** 文件大小 */
  fileSize: number;
  /** 目标文件夹 */
  folderId: string;
  /** 后端任务 id */
  uploadId?: string;
  /** 分片大小 */
  chunkSize: number;
  /** 分片总数 */
  chunkCount: number;
  /** 已上传分片序号 */
  existChunks: Set<number>;
  /** 已上传字节数 */
  uploadedBytes: number;
  status: UploadStatus;
  /** 进度 0-100 */
  progress: number;
  /** 秒传 */
  instant: boolean;
  /** 完成时间戳(ms), 用于「今日已完成」统计 */
  finishAt?: number;
  /** 网速 bytes/s */
  speed: number;
  error?: string;
  /** 当前分片请求, 暂停时中止 */
  xhr?: XMLHttpRequest;
  /** @internal 网速滑窗: 上次采样时间 */
  tick?: number;
  /** @internal 网速滑窗: 上次采样已传字节(含历史) */
  tickBytes?: number;
  /** @internal 当前分片开始前已传字节 */
  chunkBase?: number;
}

const CHUNK_SIZE = 5 * 1024 * 1024;
const MAX_CONCURRENT = 2;

/** localStorage 持久化键: 刷新后恢复传输历史 */
const STORAGE_KEY = 'nimbus_uploads_v1';

class UploadManager {
  tasks: UploadTask[] = [];
  /** 本地文件句柄: 分片切片用 */
  private files = new Map<string, File>();
  private subscribers = new Set<() => void>();
  /** 任务完成回调(用于页面刷新列表) */
  onFinished: (() => void) | null = null;

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

  /** 任务快照持久化, 供刷新后恢复历史记录 */
  private persist() {
    try {
      const plain = this.tasks.map((t) => ({
        id: t.id,
        fileName: t.fileName,
        fileSize: t.fileSize,
        folderId: t.folderId,
        uploadId: t.uploadId,
        chunkSize: t.chunkSize,
        chunkCount: t.chunkCount,
        existChunks: Array.from(t.existChunks),
        uploadedBytes: t.uploadedBytes,
        status: t.status,
        progress: t.progress,
        instant: t.instant,
        finishAt: t.finishAt,
        error: t.error,
      }));
      localStorage.setItem(STORAGE_KEY, JSON.stringify(plain));
    } catch {
      /* 存储不可用时忽略 */
    }
  }

  /** 刷新后恢复: 已完成/失败保留为历史, 进行中标记为中断并清理服务端残留分片 */
  restore() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const list = JSON.parse(raw) as Array<Record<string, unknown>>;
      for (const item of list) {
        const task: UploadTask = {
          id: String(item.id),
          fileName: String(item.fileName),
          fileSize: Number(item.fileSize ?? 0),
          folderId: String(item.folderId ?? '0'),
          uploadId: item.uploadId ? String(item.uploadId) : undefined,
          chunkSize: Number(item.chunkSize ?? CHUNK_SIZE),
          chunkCount: Number(item.chunkCount ?? 1),
          existChunks: new Set<number>((item.existChunks as number[]) ?? []),
          uploadedBytes: Number(item.uploadedBytes ?? 0),
          status: (item.status as UploadStatus) ?? 'failed',
          progress: Number(item.progress ?? 0),
          instant: Boolean(item.instant),
          finishAt: item.finishAt ? Number(item.finishAt) : undefined,
          error: item.error ? String(item.error) : undefined,
          speed: 0,
        };
        // 进行中/排队/暂停: 刷新即中断(文件句柄已丢失, 无法续传; 服务端分片交由 TTL 清理)
        if (task.status !== 'done' && task.status !== 'failed') {
          task.status = 'failed';
          task.error = '页面刷新已中断, 请重新上传';
          if (task.uploadId) {
            uploadApi.cancel(task.uploadId).catch(() => {});
          }
        }
        this.tasks.push(task);
      }
      this.emit();
    } catch {
      /* 数据损坏忽略 */
    }
  }

  /** 入队文件列表(秒传检测与分片任务自动创建) */
  enqueue(files: File[], folderId: string) {
    for (const file of files) {
      const task: UploadTask = {
        id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        fileName: file.name,
        fileSize: file.size,
        folderId,
        chunkSize: CHUNK_SIZE,
        chunkCount: 1,
        existChunks: new Set(),
        uploadedBytes: 0,
        status: 'waiting',
        progress: 0,
        instant: false,
        speed: 0,
      };
      this.files.set(task.id, file);
      this.tasks.unshift(task);
      this.emit();
      // 异步: 算哈希 -> 初始化(秒传检测) -> 自动开始
      this.initTask(task, file).catch((e) => this.fail(task, e));
    }
  }

  pause(id: string) {
    const task = this.tasks.find((t) => t.id === id);
    if (!task || task.status !== 'uploading') return;
    task.xhr?.abort();
    task.status = 'paused';
    this.emit();
  }

  resume(id: string) {
    const task = this.tasks.find((t) => t.id === id);
    if (!task || (task.status !== 'paused' && task.status !== 'failed')) return;
    task.status = 'waiting';
    task.error = undefined;
    this.emit();
    this.pump();
  }

  retry(id: string) {
    this.resume(id);
  }

  async cancel(id: string) {
    const task = this.tasks.find((t) => t.id === id);
    if (!task) return;
    task.xhr?.abort();
    if (task.uploadId) {
      try {
        await uploadApi.cancel(task.uploadId);
      } catch {
        // 任务可能已过期, 忽略
      }
    }
    this.files.delete(task.id);
    this.tasks = this.tasks.filter((t) => t.id !== id);
    this.emit();
  }

  /** 批量移除已完成任务 */
  clearFinished() {
    this.tasks = this.tasks.filter((t) => t.status !== 'done' && t.status !== 'failed');
    this.emit();
  }

  private runningCount() {
    return this.tasks.filter((t) => t.status === 'uploading').length;
  }

  /** 启动等待中的任务(控制并发) */
  private pump() {
    const waiting = this.tasks.filter((t) => t.status === 'waiting');
    for (const task of waiting) {
      if (this.runningCount() >= MAX_CONCURRENT) break;
      this.startTask(task).catch((e) => this.fail(task, e));
    }
  }

  private fail(task: UploadTask, e: unknown) {
    task.status = 'failed';
    task.error = e instanceof Error ? e.message : '上传失败';
    this.xhrClear(task);
    this.emit();
  }

  private xhrClear(task: UploadTask) {
    task.xhr = undefined;
  }

  private async initTask(task: UploadTask, file: File) {
    const fileHash = await sha256(file);
    const resp = await uploadApi.init({
      fileName: file.name,
      fileSize: file.size,
      fileHash,
      folderId: task.folderId,
    });
    if (resp.instant) {
      // 服务器已存在相同文件, 秒传完成
      task.status = 'done';
      task.progress = 100;
      task.instant = true;
      task.finishAt = Date.now();
      this.files.delete(task.id);
      this.onFinished?.();
      this.emit();
      return;
    }
    task.uploadId = resp.uploadId;
    task.chunkSize = resp.chunkSize ?? CHUNK_SIZE;
    task.chunkCount = resp.chunkCount ?? 1;
    // 服务端已有的分片(上次中断残留)跳过
    for (const index of resp.existChunks) {
      task.existChunks.add(index);
      const size = index === task.chunkCount - 1
        ? task.fileSize % task.chunkSize || task.chunkSize
        : task.chunkSize;
      task.uploadedBytes = Math.min(task.uploadedBytes + size, task.fileSize);
    }
    task.progress = Math.floor((task.uploadedBytes / task.fileSize) * 100);
    this.pump();
  }

  private async startTask(task: UploadTask) {
    task.status = 'uploading';
    task.error = undefined;
    this.emit();
    const file = this.files.get(task.id);
    if (!file) return;
    for (let i = 0; i < task.chunkCount; i++) {
      if (task.existChunks.has(i)) continue;
      const start = i * task.chunkSize;
      const end = Math.min(start + task.chunkSize, file.size);
      const blob = file.slice(start, end);
      // 记录分片开始前的已传字节, 供网速滑窗
      task.chunkBase = task.uploadedBytes;
      const ok = await this.uploadChunk(task, i, blob);
      if (!ok) return; // 被暂停/取消/失败, 状态已在回调中设置
      task.existChunks.add(i);
      task.uploadedBytes = Math.min(task.uploadedBytes + blob.size, task.fileSize);
      task.progress = Math.floor((task.uploadedBytes / task.fileSize) * 100);
      this.emit();
    }
    if (task.status === 'uploading' && task.uploadId) {
      await uploadApi.merge(task.uploadId);
      task.status = 'done';
      task.progress = 100;
      task.finishAt = Date.now();
      this.files.delete(task.id);
      this.onFinished?.();
      this.emit();
    }
  }

  /** 上传单个分片, 被暂停/取消时返回 false */
  private uploadChunk(task: UploadTask, chunkIndex: number, blob: Blob): Promise<boolean> {
    return new Promise((resolve) => {
      const form = new FormData();
      form.append('uploadId', task.uploadId ?? '');
      form.append('chunkIndex', String(chunkIndex));
      form.append('file', blob);
      const xhr = new XMLHttpRequest();
      task.xhr = xhr;
      xhr.open('POST', '/api/upload/chunk');
      const token = localStorage.getItem('nimbus_token');
      if (token) xhr.setRequestHeader('Authorization', token);
      // 分片实时进度 -> 网速滑窗(400ms 采样)
      xhr.upload.onprogress = (e) => {
        if (!e.lengthComputable || task.status !== 'uploading') return;
        const now = performance.now();
        const base = task.chunkBase ?? task.uploadedBytes;
        if (task.tick !== undefined && task.tickBytes !== undefined && now - task.tick >= 300) {
          task.speed = Math.max(0, ((base + e.loaded - task.tickBytes) / (now - task.tick)) * 1000);
          task.tick = now;
          task.tickBytes = base + e.loaded;
          this.emit();
        } else if (task.tick === undefined) {
          task.tick = now;
          task.tickBytes = base;
        }
      };
      xhr.onload = () => {
        this.xhrClear(task);
        if (xhr.status === 401) {
          resolve(false);
          return;
        }
        if (xhr.status >= 200 && xhr.status < 300) {
          let body: { code?: number; msg?: string } = {};
          try {
            body = JSON.parse(xhr.responseText || '{}');
          } catch {
            /* 忽略 */
          }
          if (body.code === 200 || body.code === undefined) {
            resolve(true);
          } else {
            this.fail(task, new Error(body.msg || '分片上传失败'));
            resolve(false);
          }
        } else {
          this.fail(task, new Error(`分片上传失败 HTTP ${xhr.status}`));
          resolve(false);
        }
      };
      xhr.onerror = () => {
        this.xhrClear(task);
        this.fail(task, new Error('网络异常, 分片上传失败'));
        resolve(false);
      };
      xhr.onabort = () => {
        this.xhrClear(task);
        // 暂停/取消已由 pause()/cancel() 设置状态, 此处仅结束本次分片
        resolve(false);
      };
      xhr.send(form);
    });
  }
}

export const uploader = new UploadManager();

// 刷新后恢复持久化的传输历史
uploader.restore();

/** SHA-256 十六进制(用于秒传检测) */
export function sha256(file: File): Promise<string> {
  return file.arrayBuffer().then((buffer) =>
    crypto.subtle.digest('SHA-256', buffer).then((hash) =>
      Array.from(new Uint8Array(hash))
        .map((b) => b.toString(16).padStart(2, '0'))
        .join(''),
    ),
  );
}

/** 可读文件大小 */
export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}