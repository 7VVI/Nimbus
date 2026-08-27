import { Button } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CloseOutlined, MinusOutlined, SwapOutlined } from '@ant-design/icons';
import { uploader, formatSize } from '../store/uploader';
import type { UploadTask } from '../store/uploader';
import { downloader, formatSpeed } from '../store/downloader';
import type { DownloadTask } from '../store/downloader';

/**
 * 右下角悬浮传输面板(原型 FloatingTransfer): 下载/上传任务实时进度
 * - 有活跃任务时出现, 不自动关闭, 可收起/手动关闭
 * - 全部任务结束后自动复位(下次新任务再次弹出)
 */
export function FloatingTransfer() {
  const navigate = useNavigate();
  const [uploads, setUploads] = useState<UploadTask[]>([]);
  const [downloads, setDownloads] = useState<DownloadTask[]>([]);
  const [collapsed, setCollapsed] = useState(false);
  /** 用户手动关闭: 本次传输期间不再弹出 */
  const [closed, setClosed] = useState(false);

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

  const activeTasks = [
    ...downloads.filter((t) => t.status === 'downloading'),
    ...uploads.filter((t) => t.status === 'uploading' || t.status === 'waiting'),
  ];
  // 全部结束后复位关闭状态, 下次新任务自动弹出
  useEffect(() => {
    if (activeTasks.length === 0) {
      setClosed(false);
      setCollapsed(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTasks.length]);

  if (activeTasks.length === 0 || closed) {
    return null;
  }

  const totalSpeed =
    uploads.filter((t) => t.status === 'uploading').reduce((acc, t) => acc + t.speed, 0)
    + downloads.filter((t) => t.status === 'downloading').reduce((acc, t) => acc + t.speed, 0);

  return (
    <div
      className="nimbus-floating-transfer pop-in"
      style={{
        position: 'fixed',
        right: 22,
        bottom: 22,
        width: 330,
        background: '#fff',
        borderRadius: 14,
        border: '1px solid var(--border)',
        boxShadow: '0 12px 40px rgba(18, 22, 33, 0.16)',
        overflow: 'hidden',
        zIndex: 1000,
      }}
    >
      {/* 头部 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 9, padding: '12px 14px', borderBottom: collapsed ? 'none' : '1px solid var(--border)' }}>
        <div
          style={{
            width: 26,
            height: 26,
            borderRadius: 8,
            background: 'var(--accent-lt)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'var(--accent)',
          }}
        >
          <SwapOutlined style={{ fontSize: 14 }} />
        </div>
        <span style={{ fontSize: 13, fontWeight: 600, flex: 1 }}>正在传输 {activeTasks.length} 项</span>
        {totalSpeed > 0 && (
          <span style={{ fontSize: 11.5, color: 'var(--text3)' }}>{formatSpeed(totalSpeed)}</span>
        )}
        <Button
          type="text"
          size="small"
          icon={<MinusOutlined />}
          title={collapsed ? '展开' : '收起'}
          onClick={() => setCollapsed(!collapsed)}
        />
        <Button type="text" size="small" icon={<CloseOutlined />} title="关闭" onClick={() => setClosed(true)} />
      </div>

      {/* 任务列表 */}
      {!collapsed && (
        <div style={{ padding: '6px 14px 12px' }}>
          {activeTasks.slice(0, 4).map((task) => {
            const isUp = 'chunkCount' in task;
            const loaded = isUp ? (task as UploadTask).uploadedBytes : (task as DownloadTask).loaded;
            const total = isUp
              ? (task as UploadTask).fileSize
              : ((task as DownloadTask).fileSize ?? (task as DownloadTask).loaded);
            const pct = total > 0 ? Math.min(100, Math.round((loaded / total) * 100)) : 0;
            return (
              <div key={task.id} style={{ marginTop: 10 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 5, gap: 8 }}>
                  <span style={{ fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {task.fileName}
                  </span>
                  <span style={{ color: 'var(--text3)', flexShrink: 0 }}>
                    {formatSize(loaded)} / {formatSize(total)}
                  </span>
                </div>
                {/* 细进度条(原型 ProgressLine h=5) */}
                <div style={{ height: 5, borderRadius: 3, background: '#EBEDF0', overflow: 'hidden' }}>
                  <div
                    style={{
                      width: `${pct}%`,
                      height: '100%',
                      borderRadius: 3,
                      background: 'var(--accent)',
                      transition: 'width .25s ease',
                    }}
                  />
                </div>
              </div>
            );
          })}
          {activeTasks.length > 4 && (
            <div style={{ fontSize: 11.5, color: 'var(--text3)', marginTop: 8 }}>还有 {activeTasks.length - 4} 项进行中…</div>
          )}
          <Button
            block
            size="small"
            style={{ marginTop: 12, height: 30, borderRadius: 7, fontSize: 12, fontWeight: 500, color: 'var(--text2)' }}
            onClick={() => navigate('/transfers')}
          >
            查看全部任务
          </Button>
        </div>
      )}
    </div>
  );
}

export default FloatingTransfer;