import { Alert, Button, Descriptions, Divider, Drawer, List, Modal, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { fileApi, previewApi } from '../api';
import { download } from '../api/client';
import type { NimbusFile, NimbusFileVersion, PreviewVO } from '../api/types';
import { FileIcon, categoryOf } from './FileIcon';
import { formatSize } from '../store/uploader';
import { enqueueFileDownload } from '../store/downloader';
import dayjs from 'dayjs';
import { DownloadOutlined, LinkOutlined, RollbackOutlined } from '@ant-design/icons';

interface Props {
  file: NimbusFile | null;
  onClose: () => void;
  /** 分享入口(由父级打开分享弹窗) */
  onShare?: (file: NimbusFile) => void;
  onChanged?: () => void;
}

/** 文本类: 直接拉取内容展示 */
const TEXT_EXTS = new Set(['txt', 'md', 'json', 'xml', 'csv', 'js', 'ts', 'css', 'html', 'yml', 'yaml', 'sql', 'sh', 'log']);

export function PreviewModal({ file, onClose, onShare, onChanged }: Props) {
  const [preview, setPreview] = useState<PreviewVO | null>(null);
  const [text, setText] = useState<string | null>(null);
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [versions, setVersions] = useState<NimbusFileVersion[]>([]);
  const [loading, setLoading] = useState(false);
  const [rollbacking, setRollbacking] = useState(false);

  useEffect(() => {
    if (!file) return;
    setPreview(null);
    setText(null);
    setBlobUrl(null);
    setLoading(true);
    (async () => {
      const info = await previewApi.info(file.id);
      setPreview(info);
      const ext = file.fileExt?.toLowerCase() ?? '';
      const category = categoryOf(ext);
      if (category === 'IMAGE') {
        const blob = await download(previewApi.contentUrl(file.id));
        setBlobUrl(URL.createObjectURL(blob));
      } else if (category === 'CODE' && TEXT_EXTS.has(ext)) {
        const blob = await download(previewApi.contentUrl(file.id));
        setText(await blob.text());
      }
      fileApi.versions(file.id).then(setVersions).catch(() => setVersions([]));
    })().catch(() => {
      /* 预览失败时仅展示文件信息 */
    }).finally(() => setLoading(false));
  }, [file]);

  const category = useMemo(() => categoryOf(file?.fileExt), [file]);

  const handleDownload = () => {
    if (!file) return;
    // 下载任务进入传输管理
    enqueueFileDownload(file);
  };

  const handleRollback = async (versionId: string) => {
    if (!file) return;
    setRollbacking(true);
    try {
      await fileApi.rollback(file.id, versionId);
      Modal.success({ content: '已回滚到该版本' });
      const info = await previewApi.info(file.id);
      setPreview(info);
      fileApi.versions(file.id).then(setVersions).catch(() => setVersions([]));
      onChanged?.();
    } finally {
      setRollbacking(false);
    }
  };

  return (
    <Drawer
      title={file ? file.fileName : ''}
      width={420}
      open={file !== null}
      onClose={onClose}
      extra={
        file ? (
          <Space>
            {file.isStarred === 1 && <Tag color="gold">已收藏</Tag>}
            <Tag>{file.version} 版</Tag>
          </Space>
        ) : undefined
      }
    >
      {file && (
        <Spin spinning={loading}>
          {/* 预览内容 */}
          {preview?.message && <Alert type="warning" showIcon message={preview.message} style={{ marginBottom: 16 }} />}
          {category === 'IMAGE' && blobUrl && (
            <div style={{ textAlign: 'center', marginBottom: 16 }}>
              <img src={blobUrl} alt={file.fileName} style={{ maxWidth: '100%', maxHeight: 320, borderRadius: 8 }} />
            </div>
          )}
          {category === 'VIDEO' && (
            <video src={previewApi.contentUrl(file.id)} controls style={{ width: '100%', borderRadius: 8 }} />
          )}
          {category === 'AUDIO' && (
            <audio src={previewApi.contentUrl(file.id)} controls style={{ width: '100%' }} />
          )}
          {text !== null && (
            <pre
              style={{
                background: '#F7F8FA',
                padding: 12,
                borderRadius: 8,
                maxHeight: 320,
                overflow: 'auto',
                fontSize: 12.5,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-all',
              }}
            >
              {text}
            </pre>
          )}
          {category !== 'IMAGE' && category !== 'VIDEO' && category !== 'AUDIO' && text === null && (
            <Alert
              type="info"
              showIcon
              message={preview?.message ?? '该类型暂不支持在线预览, 可下载后查看'}
              style={{ marginBottom: 16 }}
            />
          )}

          <Divider style={{ margin: '12px 0' }} />
          <Descriptions
            column={1}
            size="small"
            items={[
              { key: 'icon', label: '', children: <FileIcon fileName={file.fileName} ext={file.fileExt} size={40} /> },
              { key: 'size', label: '大小', children: formatSize(Number(file.fileSize)) },
              { key: 'mime', label: '类型', children: file.mimeType ?? '—' },
              { key: 'time', label: '修改时间', children: dayjs(file.updateTime).format('YYYY-MM-DD HH:mm:ss') },
            ]}
          />
          <Space style={{ marginTop: 12 }} wrap>
            <Button type="primary" icon={<DownloadOutlined />} onClick={handleDownload}>
              下载
            </Button>
            <Button icon={<LinkOutlined />} onClick={() => onShare?.(file)}>
              分享
            </Button>
          </Space>

          {versions.length > 0 && (
            <>
              <Divider>历史版本({versions.length})</Divider>
              <List
                size="small"
                dataSource={versions}
                renderItem={(v) => (
                  <List.Item
                    actions={[
                      <Button
                        key="rollback"
                        type="link"
                        size="small"
                        icon={<RollbackOutlined />}
                        loading={rollbacking}
                        onClick={() => handleRollback(v.id)}
                      >
                        回滚
                      </Button>,
                    ]}
                  >
                    <List.Item.Meta
                      title={<Typography.Text style={{ fontSize: 13 }}>v{v.versionNo}</Typography.Text>}
                      description={<span style={{ fontSize: 12 }}>{dayjs(v.createTime).format('MM-DD HH:mm')} · {formatSize(Number(v.fileSize))}</span>}
                    />
                  </List.Item>
                )}
              />
            </>
          )}
        </Spin>
      )}
    </Drawer>
  );
}