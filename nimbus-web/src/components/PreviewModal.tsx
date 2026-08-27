import { Alert, Button, Descriptions, Divider, List, Modal, Space, Spin, Tag, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { fileApi, previewApi } from '../api';
import type { NimbusFile, NimbusFileVersion, PreviewVO } from '../api/types';
import { FileIcon } from './FileIcon';
import { FilePreview } from './FilePreview';
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

export function PreviewModal({ file, onClose, onShare, onChanged }: Props) {
  const [preview, setPreview] = useState<PreviewVO | null>(null);
  const [versions, setVersions] = useState<NimbusFileVersion[]>([]);
  const [loading, setLoading] = useState(false);
  const [rollbacking, setRollbacking] = useState(false);

  useEffect(() => {
    if (!file) return;
    setPreview(null);
    setLoading(true);
    (async () => {
      const info = await previewApi.info(file.id);
      setPreview(info);
      fileApi.versions(file.id).then(setVersions).catch(() => setVersions([]));
    })().catch(() => {
      /* 预览失败时仅展示文件信息 */
    }).finally(() => setLoading(false));
  }, [file]);

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
    <Modal
      title={
        <Space>
          <span>{file?.fileName}</span>
          {file?.isStarred === 1 && <Tag color="gold">已收藏</Tag>}
          {file && <Tag>{file.version} 版</Tag>}
        </Space>
      }
      open={file !== null}
      onCancel={onClose}
      footer={null}
      width={960}
      centered
      destroyOnHidden
      style={{ maxWidth: '94vw' }}
      styles={{ body: { maxHeight: '74vh', overflow: 'auto', paddingTop: 12 } }}
    >
      {file && (
        <Spin spinning={loading}>
          {/* 预览内容: 按格式分发(图片/音视频/PDF/Word/Excel/Markdown/文本) */}
          {preview?.message && <Alert type="warning" showIcon message={preview.message} style={{ marginBottom: 16 }} />}
          <div style={{ marginBottom: 16 }}>
            <FilePreview file={file} />
          </div>

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
    </Modal>
  );
}