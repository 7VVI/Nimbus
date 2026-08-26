import { App, Button, Input, Modal, Popconfirm, Skeleton, Space, Spin, Table, Tag, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { fileApi, shareApi } from '../api';
import type { NimbusShare } from '../api/types';
import dayjs from 'dayjs';
import { CopyOutlined, DeleteOutlined, LinkOutlined, SearchOutlined } from '@ant-design/icons';
import { ShareModal } from '../components/ShareModal';
import { useNavigate } from 'react-router-dom';

const PAGE_SIZE = 10;

/** 权限位掩码转文案(1预览 2下载 4转存, 可组合) */
const permissionText = (mask: number) => {
  const parts: string[] = [];
  if (mask & 1) parts.push('可预览');
  if (mask & 2) parts.push('可下载');
  if (mask & 4) parts.push('可转存');
  return parts.join(' / ') || '—';
};

/** 共享协作: 我的分享列表 + 新建分享(文件选择 -> 分享配置) + 复制链接 */
export default function Shares() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [shares, setShares] = useState<NimbusShare[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [loading, setLoading] = useState(false);
  /** 文件选择弹窗开关 */
  const [pickerOpen, setPickerOpen] = useState(false);
  /** 分享配置弹窗(选择完成后打开) */
  const [config, setConfig] = useState<{ targetType: number; targetIds: string[] } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await shareApi.my(pageNum, PAGE_SIZE);
      setShares(result.records);
      setTotal(result.total);
    } finally {
      setLoading(false);
    }
  }, [pageNum]);

  useEffect(() => {
    load();
  }, [load]);

  const shareUrl = (code: string) => `${location.origin}/s/${code}`;

  /** 复制用链接: 加密分享自动附带提取码 */
  const shareLinkWithCode = (share: NimbusShare) =>
    share.shareType === 2 && share.password
      ? `${shareUrl(share.shortCode)}?code=${encodeURIComponent(share.password)}`
      : shareUrl(share.shortCode);

  const copy = async (text: string) => {
    await navigator.clipboard.writeText(text);
    message.success('已复制');
  };

  const cancelShare = async (id: string) => {
    try {
      await shareApi.cancel(id);
      message.success('分享已取消');
      load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '取消失败');
    }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>共享协作</h2>
          <div className="page-sub">通过链接分享你的文件, 支持提取码与有效期控制</div>
        </div>
        <Button type="primary" icon={<LinkOutlined />} onClick={() => setPickerOpen(true)}>
          新建分享
        </Button>
      </div>
      <div className="page-card">
        {loading && shares.length === 0 ? (
          <div style={{ padding: '10px 6px' }}>
            <Skeleton active title={false} paragraph={{ rows: 7 }} />
          </div>
        ) : (
          <Table<NimbusShare>
          rowKey="id"
          size="middle"
          loading={loading}
          dataSource={shares}
          pagination={{
            current: pageNum,
            pageSize: PAGE_SIZE,
            total,
            showTotal: (t) => `共 ${t} 个分享`,
            onChange: setPageNum,
          }}
          columns={[
            {
              title: '链接',
              key: 'link',
              render: (_, share) => (
                <Typography.Text
                  style={{ cursor: 'pointer', color: 'var(--accent)' }}
                  onClick={() => navigate(`/s/${share.shortCode}`)}
                >
                  /s/{share.shortCode}
                </Typography.Text>
              ),
            },
            {
              title: '类型',
              key: 'type',
              width: 90,
              render: (_, share) =>
                share.shareType === 2 ? <Tag color="orange">加密</Tag> : <Tag color="green">公开</Tag>,
            },
            {
              title: '权限',
              key: 'permission',
              width: 200,
              // 权限组合文案单行展示, 不换行
              render: (_, share) => (
                <span style={{ whiteSpace: 'nowrap', fontSize: 12.5 }}>{permissionText(share.permission)}</span>
              ),
            },
            {
              title: '有效期',
              key: 'expire',
              width: 160,
              render: (_, share) =>
                share.expireTime ? (
                  <span style={{ fontSize: 12.5, color: 'var(--text2)' }}>
                    {dayjs(share.expireTime).format('YYYY-MM-DD')} 到期
                  </span>
                ) : (
                  <span style={{ fontSize: 12.5 }}>永久</span>
                ),
            },
            {
              title: '浏览 / 转存',
              key: 'count',
              width: 110,
              render: (_, share) => (
                <span style={{ fontSize: 12.5, color: 'var(--text2)' }}>
                  {share.viewCount} / {share.saveCount}
                </span>
              ),
            },
            {
              title: '创建时间',
              key: 'createTime',
              width: 150,
              render: (_, share) => (
                <span style={{ fontSize: 12.5, color: 'var(--text3)' }}>
                  {dayjs(share.createTime).format('YYYY-MM-DD HH:mm')}
                </span>
              ),
            },
            {
              title: '操作',
              key: 'actions',
              width: 210,
              // 操作按钮单行展示, 不换行
              render: (_, share) => (
                <Space size={6} style={{ whiteSpace: 'nowrap' }}>
                  <a onClick={() => copy(shareLinkWithCode(share))}>
                    <CopyOutlined /> 链接
                  </a>
                  {share.shareType === 2 && share.password && (
                    <a onClick={() => copy(share.password ?? '')}>
                      <CopyOutlined /> 提取码
                    </a>
                  )}
                  <Popconfirm title="确定取消该分享?" onConfirm={() => cancelShare(share.id)}>
                    <a style={{ color: '#D44C47' }}>
                      <DeleteOutlined /> 取消
                    </a>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
        )}
      </div>

      {/* 新建分享: 第一步选择文件 */}
      <SharePickerModal
        open={pickerOpen}
        onSelect={(fileId) => {
          // 关闭选择器, 打开分享配置弹窗
          setPickerOpen(false);
          setConfig({ targetType: 1, targetIds: [fileId] });
        }}
        onClose={() => setPickerOpen(false)}
      />
      {/* 新建分享: 第二步配置(提取码/权限/有效期) */}
      <ShareModal
        open={config !== null}
        targetType={config?.targetType ?? 1}
        targetIds={config?.targetIds ?? []}
        onClose={() => setConfig(null)}
        onCreated={() => {
          message.success('分享已创建');
          load();
        }}
      />
    </div>
  );
}

/** 轻量文件选择器(分享目标): 受控弹窗, 一次仅弹一个 */
function SharePickerModal({
  open,
  onSelect,
  onClose,
}: {
  open: boolean;
  onSelect: (fileId: string) => void;
  onClose: () => void;
}) {
  const [files, setFiles] = useState<{ id: string; fileName: string }[]>([]);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);

  // 打开时重置; 关键字 300ms 防抖搜索
  useEffect(() => {
    if (!open) return;
    setFiles([]);
    setKeyword('');
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const timer = setTimeout(() => {
      setLoading(true);
      fileApi
        .page({ keyword: keyword || undefined, pageNum: 1, pageSize: 20 })
        .then((r) => setFiles(r.records))
        .catch(() => setFiles([]))
        .finally(() => setLoading(false));
    }, 300);
    return () => clearTimeout(timer);
  }, [open, keyword]);

  return (
    <Modal
      title="选择要分享的文件"
      open={open}
      onCancel={onClose}
      footer={null}
      width={520}
      destroyOnHidden
    >
      <Input
        prefix={<SearchOutlined style={{ color: 'var(--text3)' }} />}
        placeholder="搜索文件…"
        value={keyword}
        style={{ marginBottom: 10 }}
        onChange={(e) => setKeyword(e.target.value)}
        allowClear
      />
      <Spin spinning={loading}>
        <div style={{ maxHeight: 320, overflow: 'auto' }}>
          {files.map((f) => (
            <div
              key={f.id}
              style={{
                padding: '9px 10px',
                borderRadius: 8,
                cursor: 'pointer',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--hover)')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
              onClick={() => onSelect(f.id)}
            >
              <span style={{ fontSize: 13.5 }}>{f.fileName}</span>
              <span style={{ color: 'var(--accent)', fontSize: 12.5 }}>分享 →</span>
            </div>
          ))}
          {!loading && files.length === 0 && (
            <div style={{ textAlign: 'center', color: 'var(--text3)', padding: 24 }}>
              {keyword ? '没有匹配的文件' : '还没有文件, 请先上传'}
            </div>
          )}
        </div>
      </Spin>
    </Modal>
  );
}