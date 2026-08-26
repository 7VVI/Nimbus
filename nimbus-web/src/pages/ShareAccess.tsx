import { App, Button, Input, Skeleton, Space, Spin, Tag, Typography } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { download, getToken } from '../api/client';
import { shareApi } from '../api';
import type { NimbusShare, ShareItemVO } from '../api/types';
import { FileIcon } from '../components/FileIcon';
import { MoveModal } from '../components/MoveModal';
import { formatSize } from '../store/uploader';
import { enqueueShareDownload } from '../store/downloader';
import dayjs from 'dayjs';
import { DownloadOutlined, KeyOutlined, LinkOutlined, SaveOutlined } from '@ant-design/icons';

/**
 * 分享访问页(免登录): /s/:code
 * 支持链接自带提取码(?code=xxx): 自动识别并完成校验, 无需手动输入
 */
export default function ShareAccess() {
  const { code = '' } = useParams();
  const [searchParams] = useSearchParams();
  const urlCode = searchParams.get('code') ?? '';
  const { message } = App.useApp();
  const [share, setShare] = useState<NimbusShare | null>(null);
  const [items, setItems] = useState<ShareItemVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [needPassword, setNeedPassword] = useState(false);
  const [password, setPassword] = useState('');
  const [browsingFolder, setBrowsingFolder] = useState<ShareItemVO | null>(null);
  const [saveOpen, setSaveOpen] = useState(false);
  const [previewUrls, setPreviewUrls] = useState<Record<string, string>>({});

  const loggedIn = useMemo(() => getToken() !== null, []);

  const loadAccess = useCallback(
    async (pwd?: string) => {
      setLoading(true);
      try {
        const result = await shareApi.access(code, pwd || undefined);
        setShare(result.share);
        setItems(result.items);
        setNeedPassword(false);
      } catch (e) {
        const err = e as { code?: number };
        if (err.code === 1107) {
          // 提取码错误: 提示输入
          setNeedPassword(true);
        } else {
          message.error(e instanceof Error ? e.message : '分享不存在');
        }
      } finally {
        setLoading(false);
      }
    },
    [code, message],
  );

  useEffect(() => {
    setShare(null);
    setItems([]);
    setPassword(urlCode);
    setBrowsingFolder(null);
    // URL 附带提取码时自动完成校验
    loadAccess(urlCode || undefined);
  }, [code, urlCode, loadAccess]);

  // 文件夹条目: 进入子目录
  const browseChildren = async (folder: ShareItemVO) => {
    setLoading(true);
    try {
      const list = await shareApi.items(code, folder.id, password || undefined);
      setBrowsingFolder(folder);
      setItems(list);
    } finally {
      setLoading(false);
    }
  };

  const backToRoot = async () => {
    setLoading(true);
    try {
      const list = await shareApi.items(code, undefined, password || undefined);
      setBrowsingFolder(null);
      setItems(list);
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = (item: ShareItemVO) => {
    // 分享下载同样进入传输管理(免登录)
    enqueueShareDownload(code, password || undefined, { id: item.id, name: item.name });
  };

  // 图片内联预览: 拉取 blob 生成对象地址
  const previewImage = async (item: ShareItemVO) => {
    if (previewUrls[item.id]) {
      setPreviewing(item.id);
      return;
    }
    try {
      const blob = await download(`/api/share/${code}/download/${item.id}`, {
        params: { password: password || undefined },
      });
      setPreviewUrls((prev) => ({ ...prev, [item.id]: URL.createObjectURL(blob) }));
      setPreviewing(item.id);
    } catch {
      message.error('预览失败');
    }
  };

  const [previewing, setPreviewing] = useState<string | null>(null);

  const handleSave = async (folderId: string) => {
    try {
      const count = await shareApi.save(code, password || undefined, folderId);
      message.success(`转存成功, 共 ${count} 个文件`);
      setSaveOpen(false);
    } catch (e) {
      if ((e as { code?: number }).code === 401) {
        message.error('请先登录后再转存');
        location.href = `/login?redirect=${encodeURIComponent(`/s/${code}`)}`;
      } else {
        message.error(e instanceof Error ? e.message : '转存失败');
      }
    }
  };

  const isImage = (item: ShareItemVO) =>
    item.targetType === 1 && ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg'].includes((item.fileExt ?? '').toLowerCase());

  return (
    <div className="share-access-page">
      <div className="share-access-card">
        {/* 头部 */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 38,
                height: 38,
                borderRadius: 10,
                background: 'var(--accent)',
                color: '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 18,
                fontWeight: 700,
              }}
            >
              N
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: 15 }}>Nimbus 云盘 · 分享</div>
              <div style={{ fontSize: 12, color: 'var(--text3)' }}>
                {share ? `${share.viewCount} 次浏览` : '加载中…'}
              </div>
            </div>
          </div>
          <Link to={loggedIn ? '/files' : '/login'} style={{ fontSize: 13 }}>
            {loggedIn ? '进入我的网盘 →' : '登录后可转存 →'}
          </Link>
        </div>

        <Spin spinning={loading}>
          {!share && !needPassword && (
            <div style={{ padding: '18px 6px' }}>
              <Skeleton active title={false} paragraph={{ rows: 5 }} />
            </div>
          )}

          {/* 提取码输入 */}
          {needPassword && (
            <div style={{ textAlign: 'center', padding: '30px 0' }}>
              <div style={{ fontSize: 22, marginBottom: 8 }}>
                <KeyOutlined style={{ color: 'var(--orange, #DE911D)' }} />
              </div>
              <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 4 }}>此分享需要提取码</div>
              <div style={{ fontSize: 12.5, color: 'var(--text3)', marginBottom: 16 }}>
                请输入分享者提供的提取码后访问
              </div>
              <Space.Compact style={{ width: 260 }}>
                <Input
                  placeholder="请输入提取码"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onPressEnter={() => loadAccess(password)}
                />
                <Button type="primary" onClick={() => loadAccess(password)}>
                  访问
                </Button>
              </Space.Compact>
            </div>
          )}

          {/* 分享内容 */}
          {share && (
            <>
              <div style={{ fontSize: 12.5, color: 'var(--text2)', marginBottom: 12 }}>
                {share.shareType === 2 && <Tag color="orange">加密分享</Tag>}
                <Tag>可预览</Tag>
                {(share.permission & 2) !== 0 && <Tag color="blue">可下载</Tag>}
                {(share.permission & 4) !== 0 && <Tag color="green">可转存</Tag>}
                {share.expireTime && (
                  <span>有效期至 {dayjs(share.expireTime).format('YYYY-MM-DD HH:mm')}</span>
                )}
              </div>

              {/* 面包屑 */}
              <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
                <LinkOutlined style={{ color: 'var(--text3)' }} />
                {browsingFolder ? (
                  <>
                    <a onClick={backToRoot} style={{ color: 'var(--text2)' }}>分享内容</a>
                    <span style={{ color: 'var(--border2)' }}>/</span>
                    <span style={{ fontWeight: 600 }}>{browsingFolder.name}</span>
                  </>
                ) : (
                  <span style={{ fontWeight: 600 }}>分享内容</span>
                )}
              </div>

              {/* 条目列表 */}
              {items.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '30px 0', color: 'var(--text3)' }}>此文件夹为空</div>
              ) : (
                items.map((item) => (
                  <div
                    key={`${item.targetType}-${item.id}`}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 12,
                      padding: '10px 10px',
                      borderRadius: 10,
                      cursor: 'pointer',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--hover)')}
                    onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                  >
                    {item.targetType === 2 ? (
                      <FileIcon size={36} />
                    ) : (
                      <FileIcon fileName={item.name} ext={item.fileExt} size={36} />
                    )}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <Typography.Text
                        strong={item.targetType === 2}
                        style={{ fontSize: 13.5, display: 'block' }}
                        ellipsis
                      >
                        {item.name}
                      </Typography.Text>
                      {item.targetType === 1 && (
                        <span style={{ fontSize: 12, color: 'var(--text3)' }}>
                          {formatSize(Number(item.fileSize ?? 0))}
                        </span>
                      )}
                    </div>
                    {item.targetType === 2 ? (
                      <Button size="small" onClick={() => browseChildren(item)}>
                        打开
                      </Button>
                    ) : (
                      <Space>
                        {isImage(item) && (
                          <Button size="small" type="link" onClick={() => previewImage(item)}>
                            预览
                          </Button>
                        )}
                        <Button
                          size="small"
                          type="link"
                          icon={<DownloadOutlined />}
                          onClick={() => handleDownload(item)}
                        >
                          下载
                        </Button>
                      </Space>
                    )}
                  </div>
                ))
              )}

              {/* 图片预览 */}
              {previewing && previewUrls[previewing] && (
                <div style={{ marginTop: 14, textAlign: 'center' }}>
                  <img
                    src={previewUrls[previewing]}
                    alt="preview"
                    style={{ maxWidth: '100%', maxHeight: 260, borderRadius: 10 }}
                    onClick={() => setPreviewing(null)}
                  />
                </div>
              )}

              {/* 转存 */}
              <div style={{ marginTop: 22, paddingTop: 16, borderTop: '1px solid var(--border)' }}>
                <Space wrap>
                  <Button
                    size="large"
                    type="primary"
                    icon={<SaveOutlined />}
                    onClick={() => {
                      if (!loggedIn) {
                        location.href = `/login?redirect=${encodeURIComponent(`/s/${code}`)}`;
                        return;
                      }
                      setSaveOpen(true);
                    }}
                  >
                    保存到我的网盘
                  </Button>
                  {!loggedIn && (
                    <span style={{ fontSize: 12.5, color: 'var(--text3)' }}>
                      登录后可将分享内容一键转存
                    </span>
                  )}
                </Space>
              </div>
            </>
          )}
        </Spin>
      </div>

      {/* 转存目标选择 */}
      <MoveModal
        open={saveOpen}
        title="保存到…"
        onOk={async (folderId) => {
          await handleSave(folderId);
        }}
        onCancel={() => setSaveOpen(false)}
      />
    </div>
  );
}