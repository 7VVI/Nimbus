import { App, Button, Input, Modal, Space } from 'antd';
import { useCallback, useState } from 'react';
import { fileApi, folderApi } from '../api';
import type { NimbusFile } from '../api/types';
import { MoveModal } from '../components/MoveModal';
import { PreviewModal } from '../components/PreviewModal';
import { ShareModal } from '../components/ShareModal';
import type { RowItem } from '../components/FileTable';
import { enqueueFileDownload } from '../store/downloader';

/**
 * 文件行通用操作集合(预览/收藏/分享/下载/重命名/移动/复制/删除)
 * 供 最近/收藏/搜索 等文件列表页复用, 完成后回调 refresh 刷新列表
 */
export function usePageActions(refresh: () => void) {
  const { message } = App.useApp();
  const [previewFile, setPreviewFile] = useState<NimbusFile | null>(null);
  const [shareTarget, setShareTarget] = useState<RowItem | null>(null);
  const [moveTarget, setMoveTarget] = useState<RowItem | null>(null);
  const [copyFile, setCopyFile] = useState<NimbusFile | null>(null);
  const [renameTarget, setRenameTarget] = useState<{ type: 'file' | 'folder'; id: string; name: string } | null>(null);

  const open = (item: RowItem) => {
    if ('fileName' in item) {
      setPreviewFile(item);
    }
  };

  const star = useCallback(
    async (file: NimbusFile, starred: boolean) => {
      try {
        await fileApi.star(file.id, starred);
        refresh();
      } catch (e) {
        message.error(e instanceof Error ? e.message : '操作失败');
      }
    },
    [refresh, message],
  );

  const download = useCallback((file: NimbusFile) => {
    // 下载任务进入传输管理, 展示进度与网速
    enqueueFileDownload(file);
  }, []);

  const remove = (item: RowItem) => {
    Modal.confirm({
      title: '移入回收站',
      content: '确定将所选项目移入回收站吗? 可在回收站中恢复。',
      okText: '移入回收站',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          if ('fileName' in item) {
            await fileApi.delete(item.id);
          } else {
            await folderApi.delete(item.id);
          }
          message.success('已移入回收站');
          refresh();
        } catch (e) {
          message.error(e instanceof Error ? e.message : '删除失败');
        }
      },
    });
  };

  const rename = (item: RowItem) => {
    setRenameTarget({
      type: 'fileName' in item ? 'file' : 'folder',
      id: item.id,
      name: 'fileName' in item ? item.fileName : item.folderName,
    });
  };

  const submitRename = async (name: string) => {
    if (!renameTarget) return;
    try {
      if (renameTarget.type === 'file') {
        await fileApi.rename(renameTarget.id, name);
      } else {
        await folderApi.rename(renameTarget.id, name);
      }
      message.success('重命名成功');
      refresh();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '重命名失败');
    }
    setRenameTarget(null);
  };

  const execMove = async (targetFolderId: string) => {
    if (!moveTarget) return;
    try {
      if ('fileName' in moveTarget) {
        await fileApi.move(moveTarget.id, targetFolderId);
      } else {
        await folderApi.move(moveTarget.id, targetFolderId);
      }
      message.success('移动成功');
      refresh();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '移动失败');
    }
    setMoveTarget(null);
  };

  const execCopy = async (targetFolderId: string) => {
    if (!copyFile) return;
    try {
      await fileApi.copy(copyFile.id, targetFolderId);
      message.success('复制成功');
      refresh();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '复制失败');
    }
    setCopyFile(null);
  };

  return {
    open,
    star,
    download,
    remove,
    rename,
    share: setShareTarget,
    move: setMoveTarget,
    copy: setCopyFile,
    renderModals: () => {
      return (
        <>
          <MoveModal open={moveTarget !== null} title="移动到…" onOk={execMove} onCancel={() => setMoveTarget(null)} />
          <MoveModal
            open={copyFile !== null}
            title={`复制「${copyFile?.fileName ?? ''}」到…`}
            onOk={execCopy}
            onCancel={() => setCopyFile(null)}
          />
          <ShareModal
            open={shareTarget !== null}
            targetType={'fileName' in (shareTarget ?? {}) ? 1 : 2}
            targetIds={shareTarget ? [shareTarget.id] : []}
            onClose={() => setShareTarget(null)}
            onCreated={() => message.success('分享已创建')}
          />
          <PreviewModal
            file={previewFile}
            onClose={() => setPreviewFile(null)}
            onShare={setShareTarget}
            onChanged={refresh}
          />
          {renameTarget && (
            <RenameModal initial={renameTarget.name} onCancel={() => setRenameTarget(null)} onSubmit={submitRename} />
          )}
        </>
      );
    },
  };
}

function RenameModal({
  initial,
  onCancel,
  onSubmit,
}: {
  initial: string;
  onCancel: () => void;
  onSubmit: (name: string) => Promise<void>;
}) {
  const [name, setName] = useState(initial);
  const [submitting, setSubmitting] = useState(false);
  const submit = async () => {
    if (!name.trim()) return;
    setSubmitting(true);
    try {
      await onSubmit(name.trim());
    } finally {
      setSubmitting(false);
    }
  };
  return (
    <Modal title="重命名" open onCancel={onCancel} footer={null} width={380} destroyOnHidden>
      <Input value={name} maxLength={255} autoFocus onChange={(e) => setName(e.target.value)} onPressEnter={submit} />
      <div style={{ marginTop: 12, textAlign: 'right' }}>
        <Space>
          <Button onClick={onCancel}>取消</Button>
          <Button type="primary" loading={submitting} onClick={submit}>
            确定
          </Button>
        </Space>
      </div>
    </Modal>
  );
}