import { App, Badge, Button, Empty, Progress, Space } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { uploader, formatSize } from '../store/uploader';
import type { UploadTask } from '../store/uploader';
import { downloader, formatSpeed } from '../store/downloader';
import type { DownloadTask } from '../store/downloader';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CheckOutlined,
  CloudDownloadOutlined,
  CloudUploadOutlined,
  ExclamationCircleOutlined,
  FolderOutlined,
  InfoCircleOutlined,
  PauseOutlined,
  PlayCircleOutlined,
  SyncOutlined,
} from '@ant-design/icons';

type TabKey = 'all' | 'active' | 'done' | 'failed';

const TABS: { key: TabKey; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'active', label: '进行中' },
  { key: 'done', label: '已完成' },
  { key: 'failed', label: '失败' },
];

/** 剩余时间估算 */
function formatEta(task: { fileSize: number; loaded: number; speed: number }): string {
  if (task.speed <= 0) return '计算中…';
  const remain = Math.max(0, task.fileSize - task.loaded) / task.speed;
  if (remain < 60) return `剩余 ${Math.ceil(remain)} 秒`;
  if (remain < 3600) return `剩余 ${Math.ceil(remain / 60)} 分钟`;
  return `剩余 ${(remain / 3600).toFixed(1)} 小时`;
}

function isToday(ts?: number): boolean {
  if (!ts) return false;
  const now = new Date();
  const d = new Date(ts);
  return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate();
}

/** 圆形状态图标(原型风格: 上传蓝 / 下载紫 / 失败红 / 秒传绿) */
function StatusIcon({ upload, status, instant }: { upload: boolean; status: string; instant?: boolean }) {
  const style = { fontSize: 15.5, strokeWidth: 0 };
  if (status === 'failed') {
    return (
      <span
        style={{
          width: 34, height: 34, borderRadius: '50%', background: '#FEEDEC', flexShrink: 0,
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color: '#D44C47',
        }}
      >
        <ExclamationCircleOutlined style={style} />
      </span>
    );
  }
  if (status === 'done' && instant) {
    return (
      <span
        style={{
          width: 34, height: 34, borderRadius: '50%', background: '#E9F5EE', flexShrink: 0,
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color: '#299764',
        }}
      >
        <CheckOutlined style={{ fontSize: 14, strokeWidth: 0 }} />
      </span>
    );
  }
  const color = upload ? '#3E63DD' : '#8E4EC6';
  const bg = upload ? '#EDF1FE' : '#F4EBF9';
  return (
    <span
      style={{
        width: 34, height: 34, borderRadius: '50%', background: bg, flexShrink: 0,
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color,
      }}
    >
      {upload ? <CloudUploadOutlined style={style} /> : <CloudDownloadOutlined style={style} />}
    </span>
  );
}

/** 原型传输行: 图标 + 名称/徽标 + 副行(进度/速度·剩余) + 右侧已传-总/百分比 + 操作 */
function TransferLine({
  name, upload, status, instant, progress, loaded, total, speed, error, finishAt,
  onPause, onResume, onRetry, onOpen,
}: {
  name: string;
  upload: boolean;
  status: string;
  instant?: boolean;
  progress: number;
  loaded: number;
  total: number;
  speed: number;
  error?: string;
  finishAt?: number;
  onPause?: () => void;
  onResume?: () => void;
  onRetry?: () => void;
  onOpen?: () => void;
}) {
  return (
    <div className="row-hover transfer-item" style={{ display: 'flex', alignItems: 'center', gap: 13, padding: '12px 18px' }}>
      <StatusIcon upload={upload} status={status} instant={instant} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ fontSize: 13.5, fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {name}
          </span>
          {status === 'done' && instant && <Badge color="green" text="秒传完成" />}
          {status === 'done' && !instant && <Badge color="gray" text="已完成" />}
          {status === 'failed' && <Badge color="red" text="失败" />}
          {status === 'waiting' && <Badge color="gray" text="排队中" />}
          {status === 'paused' && <Badge color="orange" text="已暂停" />}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 4, fontSize: 11.5 }}>
          {status === 'uploading' || status === 'downloading' ? (
            <>
              <div style={{ width: 180 }}>
                <Progress percent={progress} size="small" showInfo={false} strokeColor="var(--accent)" trailColor="#EBEDF0" />
              </div>
              <span style={{ color: 'var(--text2)' }}>{formatSpeed(speed)} · {formatEta({ fileSize: total, loaded, speed })}</span>
            </>
          ) : status === 'waiting' ? (
            <span style={{ color: 'var(--text3)' }}>等待前序任务完成</span>
          ) : status === 'paused' ? (
            <span style={{ color: 'var(--text3)' }}>已暂停, 可随时继续</span>
          ) : status === 'done' && instant ? (
            <span style={{ color: 'var(--text3)' }}>服务器已存在相同文件，瞬间完成</span>
          ) : status === 'failed' ? (
            <span style={{ color: '#D44C47' }}>{error ?? '传输失败, 可重试'}</span>
          ) : (
            <span style={{ color: 'var(--text3)' }}>
              已完成 · {finishAt ? new Date(finishAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : ''}
            </span>
          )}
        </div>
      </div>
      <div style={{ textAlign: 'right', flexShrink: 0 }}>
        <div style={{ fontSize: 12.5, fontWeight: 600 }}>
          {status === 'uploading' || status === 'downloading' || status === 'paused' ? `${formatSize(loaded)} / ${formatSize(total)}` : formatSize(total)}
        </div>
        {(status === 'uploading' || status === 'downloading') && (
          <div style={{ fontSize: 11, color: 'var(--accent)', fontWeight: 700 }}>{progress}%</div>
        )}
      </div>
      <div style={{ display: 'flex', gap: 4, marginLeft: 8 }}>
        {(status === 'uploading' || status === 'downloading') && onPause && (
          <Button size="small" type="text" icon={<PauseOutlined />} onClick={onPause} title="暂停" />
        )}
        {status === 'paused' && onResume && (
          <Button size="small" type="text" icon={<PlayCircleOutlined />} onClick={onResume} title="继续" />
        )}
        {status === 'failed' && onRetry && (
          <Button size="small" type="text" icon={<SyncOutlined />} onClick={onRetry} title="断点续传 / 重试" />
        )}
        {status === 'done' && onOpen && (
          <Button size="small" type="text" icon={<FolderOutlined />} onClick={onOpen} title="打开所在位置" />
        )}
      </div>
    </div>
  );
}

/** 传输管理: 对齐原型 - 统计卡 + 分段筛选 + 任务列表(刷新后持久化历史) */
export default function Transfers() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [uploads, setUploads] = useState<UploadTask[]>([]);
  const [downloads, setDownloads] = useState<DownloadTask[]>([]);
  const [tab, setTab] = useState<TabKey>('all');
  const fileInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const refresh = () => {
      setUploads([...uploader.tasks]);
      setDownloads([...downloader.tasks]);
    };
    refresh();
    const unsub1 = uploader.subscribe(refresh);
    const unsub2 = downloader.subscribe(refresh);
    return () => {
      unsub1();
      unsub2();
    };
  }, []);

  const isActive = (s: string) => s === 'uploading' || s === 'downloading' || s === 'waiting' || s === 'paused';
  const isDone = (s: string) => s === 'done';

  const uploadSpeed = uploads.filter((t) => t.status === 'uploading').reduce((acc, t) => acc + t.speed, 0);
  const downloadSpeed = downloads.filter((t) => t.status === 'downloading').reduce((acc, t) => acc + t.speed, 0);
  const todayDone = [...uploads, ...downloads].filter((t) => t.status === 'done' && isToday(t.finishAt)).length;

  const counts = {
    all: uploads.length + downloads.length,
    active: uploads.filter((t) => isActive(t.status)).length + downloads.filter((t) => isActive(t.status)).length,
    done: uploads.filter((t) => isDone(t.status)).length + downloads.filter((t) => isDone(t.status)).length,
    failed: uploads.filter((t) => t.status === 'failed').length + downloads.filter((t) => t.status === 'failed').length,
  };

  const filteredUploads = uploads.filter((t) =>
    tab === 'all' ? true : tab === 'done' ? isDone(t.status) : tab === 'failed' ? t.status === 'failed' : isActive(t.status),
  );
  const filteredDownloads = downloads.filter((t) =>
    tab === 'all' ? true : tab === 'done' ? isDone(t.status) : tab === 'failed' ? t.status === 'failed' : isActive(t.status),
  );

  const handleNewUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    if (files.length > 0) {
      uploader.enqueue(files, '0');
      message.success(`${files.length} 个文件已加入上传队列(根目录)`);
    }
    e.target.value = '';
  };

  return (
    <div style={{ maxWidth: 1160, margin: '0 auto' }}>
      <div className="page-head">
        <div>
          <h2>传输管理</h2>
          <div className="page-sub">上传 / 下载任务与历史, 刷新不会丢失</div>
        </div>
        <Space>
          <Button type="primary" icon={<CloudUploadOutlined />} onClick={() => fileInput.current?.click()}>
            新建上传
          </Button>
          <input ref={fileInput} type="file" multiple hidden onChange={handleNewUpload} />
        </Space>
      </div>

      {/* 统计卡 */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
        {[
          ['上传速度', formatSpeed(uploadSpeed), <ArrowUpOutlined key="u" />, '#3E63DD', '#EDF1FE'],
          ['下载速度', formatSpeed(downloadSpeed), <ArrowDownOutlined key="d" />, '#8E4EC6', '#F4EBF9'],
          ['今日已完成', `${todayDone} 项`, <CheckOutlined key="c" />, '#299764', '#E9F5EE'],
        ].map((card) => (
          <div
            key={String(card[0])}
            style={{
              flex: 1, background: '#fff', border: '1px solid var(--border)', borderRadius: 12,
              padding: '14px 18px', display: 'flex', alignItems: 'center', gap: 13,
            }}
          >
            <div
              style={{
                width: 38, height: 38, borderRadius: 10, background: String(card[4]),
                display: 'flex', alignItems: 'center', justifyContent: 'center', color: String(card[3]),
              }}
            >
              {card[2]}
            </div>
            <div>
              <div style={{ fontSize: 11.5, color: 'var(--text3)' }}>{card[0]}</div>
              <div style={{ fontSize: 16, fontWeight: 700, letterSpacing: '-0.01em' }}>{card[1]}</div>
            </div>
          </div>
        ))}
      </div>

      {/* 分段筛选 */}
      <div style={{ display: 'flex', gap: 6, marginBottom: 14 }}>
        {TABS.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            style={{
              padding: '5px 14px', borderRadius: 8, fontSize: 13, fontWeight: tab === t.key ? 600 : 400,
              background: tab === t.key ? 'var(--accent)' : '#fff',
              color: tab === t.key ? '#fff' : 'var(--text2)',
              border: tab === t.key ? '1px solid var(--accent)' : '1px solid var(--border2)',
              cursor: 'pointer', transition: 'all .15s',
            }}
          >
            {t.label} {counts[t.key]}
          </button>
        ))}
      </div>

      {/* 任务列表 */}
      <div key={tab} style={{ background: '#fff', border: '1px solid var(--border)', borderRadius: 12, padding: '4px 0' }}>
        {filteredUploads.length + filteredDownloads.length === 0 ? (
          <Empty description="暂无任务" style={{ padding: '44px 0' }} />
        ) : (
          <>
            {filteredDownloads.map((task) => (
              <div key={`d-${task.id}`} style={{ borderBottom: '1px solid #F1F2F4' }}>
                <TransferLine
                  name={task.fileName}
                  upload={false}
                  status={task.status}
                  progress={task.progress}
                  loaded={task.loaded}
                  total={task.fileSize ?? task.loaded}
                  speed={task.speed}
                  error={task.error}
                  finishAt={task.finishAt}
                                    onPause={() => downloader.cancel(task.id)}
                  onRetry={() => downloader.retry(task.id)}
                  // 打开云盘中的位置: 定位到该文件所在文件夹
                  onOpen={() => navigate(task.folderId && task.folderId !== '0' ? `/files?folderId=${task.folderId}` : '/files')}
                />
              </div>
            ))}
            {filteredUploads.map((task) => (
              <div key={`u-${task.id}`} style={{ borderBottom: '1px solid #F1F2F4' }}>
                <TransferLine
                  name={task.fileName}
                  upload
                  status={task.status}
                  instant={task.instant}
                  progress={task.progress}
                  loaded={task.uploadedBytes}
                  total={task.fileSize}
                  speed={task.speed}
                  error={task.error}
                  finishAt={task.finishAt}
                  onPause={() => uploader.pause(task.id)}
                                    onResume={() => uploader.resume(task.id)}
                  onRetry={() => uploader.resume(task.id)}
                  // 打开云盘中的位置: 上传任务记录了目标文件夹
                  onOpen={() => navigate(task.folderId && task.folderId !== '0' ? `/files?folderId=${task.folderId}` : '/files')}
                />
              </div>
            ))}
          </>
        )}
        {(counts.done > 0 || counts.failed > 0) && tab === 'all' && (
          <div style={{ padding: '10px 18px', textAlign: 'right' }}>
            <Button
              size="small"
              onClick={() => {
                uploader.clearFinished();
                downloader.clearFinished();
              }}
            >
              清除已完成 / 失败
            </Button>
          </div>
        )}
      </div>

      {/* 断点续传提示 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 14, padding: '0 4px', fontSize: 12, color: 'var(--text3)' }}>
        <InfoCircleOutlined /> 支持断点续传：传输中断后自动保留进度，网络恢复后从断点继续，无需重新上传。
      </div>
    </div>
  );
}