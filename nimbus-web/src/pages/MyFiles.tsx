import { App, Button, Empty, Input, Modal, Select, Space, Tooltip } from 'antd';
import { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CloudUploadOutlined,
  FolderAddOutlined,
  FolderOpenOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { fileApi, folderApi } from '../api';
import type { BreadcrumbVO, NimbusFile, NimbusFolder } from '../api/types';
import { CurrentFolderContext } from '../components/AppLayout';
import { FileTable, DRAG_ROW } from '../components/FileTable';
import type { RowItem } from '../components/FileTable';
import { MoveModal } from '../components/MoveModal';
import { PreviewModal } from '../components/PreviewModal';
import { ShareModal } from '../components/ShareModal';
import { uploader } from '../store/uploader';
import { enqueueBatchDownload, enqueueFileDownload } from '../store/downloader';

const PAGE_SIZE = 20;

interface RenameTarget {
  type: 'file' | 'folder';
  id: string;
  name: string;
}

/** 我的文件: 面包屑 + 新建文件夹/上传 + 文件表格(文件夹/文件混排) + 批量操作 */
export default function MyFiles() {
  const { message } = App.useApp();
  const { folderId, setFolderId } = useContext(CurrentFolderContext);
    // 支持从外部(如传输页「打开所在位置」)通过 ?folderId= 定位目录
  const [searchParams] = useSearchParams();
  const folderIdFromUrl = searchParams.get('folderId');

  const [folders, setFolders] = useState<NimbusFolder[]>([]);
  const [files, setFiles] = useState<NimbusFile[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [breadcrumb, setBreadcrumb] = useState<BreadcrumbVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [sortKey, setSortKey] = useState('time');
  const [order, setOrder] = useState('desc');

  // 弹窗状态
  const [previewFile, setPreviewFile] = useState<NimbusFile | null>(null);
  const [shareTarget, setShareTarget] = useState<RowItem | null>(null);
  const [moveTarget, setMoveTarget] = useState<RowItem | null>(null);
  const [copyFile, setCopyFile] = useState<NimbusFile | null>(null);
  const [batchMoveOpen, setBatchMoveOpen] = useState(false);
  const [renameTarget, setRenameTarget] = useState<RenameTarget | null>(null);
  const [newFolderOpen, setNewFolderOpen] = useState(false);
  const [selectedFiles, setSelectedFiles] = useState<NimbusFile[]>([]);
  const [selectedFolders, setSelectedFolders] = useState<NimbusFolder[]>([]);
  const fileInput = useRef<HTMLInputElement>(null);
  const dragCount = useRef(0);
  const [dragOver, setDragOver] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [content, crumbs] = await Promise.all([
        folderApi.content(folderId, pageNum, PAGE_SIZE, sortKey, order),
        folderApi.breadcrumb(folderId),
      ]);
      setFolders(content.folders);
      setFiles(content.files.records);
      setTotal(content.files.total);
      setBreadcrumb(crumbs);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [folderId, pageNum, sortKey, order, message]);

  useEffect(() => {
    load();
  }, [load]);

  // 文件夹路径变化同步顶栏上传目标
  useEffect(() => {
    setFolderId(folderId);
  }, [folderId, setFolderId]);

  // 外部定位: URL 携带 folderId 时切换到该目录(如传输页「打开所在位置」)
  useEffect(() => {
    if (folderIdFromUrl !== null && folderIdFromUrl !== folderId) {
      setFolderId(folderIdFromUrl);
      setPageNum(1);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [folderIdFromUrl]);

  // 上传完成自动刷新列表
  useEffect(() => {
    uploader.onFinished = () => {
      setPageNum(1);
      load();
    };
    return () => {
      uploader.onFinished = null;
    };
  }, [load]);

  const openFolder = (folder: NimbusFolder) => {
    setFolderId(folder.id);
    setPageNum(1);
  };

  const openItem = (item: RowItem) => {
    if ('folderName' in item) {
      openFolder(item);
    } else {
      setPreviewFile(item);
    }
  };

  const enqueueFiles = (list: File[]) => {
    if (list.length > 0) {
      uploader.enqueue(list, folderId);
      message.success(`${list.length} 个文件已加入上传队列`);
    }
  };

  const handleFilesChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    enqueueFiles(Array.from(e.target.files ?? []));
    e.target.value = '';
  };

  const handleDownload = (file: NimbusFile) => {
    // 下载任务进入传输管理, 展示进度与网速
    enqueueFileDownload(file);
    message.success('下载任务已加入队列, 查看「传输管理」');
  };

  const requestRename = (item: RowItem) => {
    setRenameTarget({
      type: 'folderName' in item ? 'folder' : 'file',
      id: item.id,
      name: 'folderName' in item ? item.folderName : item.fileName,
    });
  };

  const handleRename = async () => {
    if (!renameTarget) return;
    try {
      if (renameTarget.type === 'folder') {
        await folderApi.rename(renameTarget.id, renameTarget.name);
      } else {
        await fileApi.rename(renameTarget.id, renameTarget.name);
      }
      message.success('重命名成功');
      load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '重命名失败');
    }
    setRenameTarget(null);
  };

  const handleMove = async (targetFolderId: string) => {
    if (!moveTarget) return;
    try {
      if ('folderName' in moveTarget) {
        await folderApi.move(moveTarget.id, targetFolderId);
      } else {
        await fileApi.move(moveTarget.id, targetFolderId);
      }
      message.success('移动成功');
      load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '移动失败');
    }
    setMoveTarget(null);
  };

  const handleCopy = async (targetFolderId: string) => {
    if (!copyFile) return;
    try {
      await fileApi.copy(copyFile.id, targetFolderId);
      message.success('复制成功');
    } catch (e) {
      message.error(e instanceof Error ? e.message : '复制失败');
    }
    setCopyFile(null);
  };

  const handleDelete = (item: RowItem) => {
    Modal.confirm({
      title: '移入回收站',
      content: '确定将所选项目移入回收站吗? 可在回收站中恢复。',
      okText: '移入回收站',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          if ('folderName' in item) {
            await folderApi.delete(item.id);
          } else {
            await fileApi.delete(item.id);
          }
          message.success('已移入回收站');
          load();
        } catch (e) {
          message.error(e instanceof Error ? e.message : '删除失败');
        }
      },
    });
  };

  const batchDownload = () => {
    enqueueBatchDownload(
      selectedFiles.map((f) => f.id),
      selectedFolders.map((f) => f.id),
    );
    message.success('批量下载已加入队列(打包为 zip), 查看「传输管理」');
  };

  const batchMove = async (targetFolderId: string) => {
    try {
      for (const f of selectedFiles) await fileApi.move(f.id, targetFolderId);
      for (const f of selectedFolders) await folderApi.move(f.id, targetFolderId);
      message.success(`已移动 ${selectedFiles.length + selectedFolders.length} 项`);
      setSelectedFiles([]);
      setSelectedFolders([]);
      load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '移动失败');
    }
    setBatchMoveOpen(false);
  };

  const batchDelete = () => {
    Modal.confirm({
      title: '批量移入回收站',
      content: `确定将选中的 ${selectedFiles.length + selectedFolders.length} 项移入回收站吗?`,
      okText: '移入回收站',
      okButtonProps: { danger: true },
      onOk: async () => {
        for (const f of selectedFiles) await fileApi.delete(f.id);
        for (const f of selectedFolders) await folderApi.delete(f.id);
        setSelectedFiles([]);
        setSelectedFolders([]);
        message.success('已移入回收站');
        load();
      },
    });
  };

  const selectedCount = selectedFiles.length + selectedFolders.length;

  /** 拖拽移动: 解析行 id -> 调移动接口 */
  const handleMoveByDrag = async (rowId: string, targetFolderId: string) => {
    const [kind, id] = rowId.split('-');
    const item = kind === 'f' ? files.find((f) => f.id === id) : folders.find((f) => f.id === id);
    if (!item) return;
    try {
      if (kind === 'f') {
        await fileApi.move(id, targetFolderId);
      } else {
        await folderApi.move(id, targetFolderId);
      }
      message.success('已移动到目标文件夹');
      load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '移动失败');
    }
  };

  /** 面包屑作为放置目标: 拖拽经过高亮, 释放即移动 */
  const crumbDropProps = (folderId: string) => ({
    onDragOver: (e: React.DragEvent) => {
      e.preventDefault();
      (e.currentTarget as HTMLElement).classList.add('crumb-drop-hover');
    },
    onDragLeave: (e: React.DragEvent) => {
      (e.currentTarget as HTMLElement).classList.remove('crumb-drop-hover');
    },
    onDrop: (e: React.DragEvent) => {
      e.preventDefault();
      (e.currentTarget as HTMLElement).classList.remove('crumb-drop-hover');
      const rowId = e.dataTransfer.getData(DRAG_ROW);
      if (rowId) handleMoveByDrag(rowId, folderId);
    },
  });

  return (
    <div>
      {/* 面包屑(可作拖拽放置目标: 移到对应层级) */}
      <div className="breadcrumb-bar">
        {breadcrumb.length === 0 ? (
          <span className="crumb current" {...crumbDropProps('0')}>
            我的文件
          </span>
        ) : (
          breadcrumb.map((crumb, index) => (
            <span key={crumb.id}>
              <span
                className={`crumb ${index === breadcrumb.length - 1 ? 'current' : ''}`}
                onClick={() => {
                  setFolderId(crumb.id);
                  setPageNum(1);
                }}
                {...crumbDropProps(crumb.id)}
              >
                {crumb.name}
              </span>
              {index < breadcrumb.length - 1 && <span style={{ color: 'var(--border2)' }}>/</span>}
            </span>
          ))
        )}
      </div>

      {/* 工具栏 */}
      <div className="page-card" style={{ padding: '10px 16px', marginBottom: 12 }}>
        <Space wrap style={{ display: 'flex', justifyContent: 'space-between' }}>
          <Space>
            <Button icon={<FolderAddOutlined />} onClick={() => setNewFolderOpen(true)}>
              新建文件夹
            </Button>
            <Button type="primary" icon={<CloudUploadOutlined />} onClick={() => fileInput.current?.click()}>
              上传
            </Button>
            <input ref={fileInput} type="file" multiple hidden onChange={handleFilesChange} />
            <Button icon={<ReloadOutlined />} onClick={load} />
          </Space>
          <Space>
            <Select
              value={sortKey}
              style={{ width: 130 }}
              onChange={(v) => setSortKey(v)}
              options={[
                { value: 'time', label: '按修改时间' },
                { value: 'name', label: '按名称' },
                { value: 'size', label: '按大小' },
              ]}
            />
            <Tooltip title={order === 'desc' ? '降序' : '升序'}>
              <Button
                icon={order === 'desc' ? <ArrowDownOutlined /> : <ArrowUpOutlined />}
                onClick={() => setOrder(order === 'desc' ? 'asc' : 'desc')}
              />
            </Tooltip>
          </Space>
        </Space>
      </div>

      {/* 批量操作条 */}
      {selectedCount > 0 && (
        <div className="selection-bar">
          <span>已选 {selectedCount} 项</span>
          <Button size="small" icon={<CloudUploadOutlined />} onClick={batchDownload}>
            下载
          </Button>
          <Button size="small" icon={<FolderOpenOutlined />} onClick={() => setBatchMoveOpen(true)}>
            移动
          </Button>
          <Button size="small" danger onClick={batchDelete}>
            删除
          </Button>
          <a
            onClick={() => {
              setSelectedFiles([]);
              setSelectedFolders([]);
            }}
          >
            取消选择
          </a>
        </div>
      )}

      {/* 文件表格(拖拽上传区域) */}
      <div
        className="page-card"
        style={dragOver ? { outline: '2px dashed var(--accent)' } : undefined}
        onDragEnter={(e) => {
          e.preventDefault();
          dragCount.current++;
          setDragOver(true);
        }}
        onDragLeave={(e) => {
          e.preventDefault();
          dragCount.current--;
          if (dragCount.current <= 0) {
            dragCount.current = 0;
            setDragOver(false);
          }
        }}
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          e.preventDefault();
          dragCount.current = 0;
          setDragOver(false);
          enqueueFiles(Array.from(e.dataTransfer.files ?? []));
        }}
      >
        {folders.length === 0 && files.length === 0 && !loading ? (
          <div style={{ padding: '48px 0' }}>
            <Empty description={<span>此文件夹为空</span>} />
            <div style={{ textAlign: 'center', color: 'var(--text3)', fontSize: 13, marginTop: 8 }}>
              点击右上角「上传」添加文件, 或拖拽文件到此处
            </div>
          </div>
        ) : (
          <FileTable
            loading={loading}
            folders={folders}
            files={files}
            total={total}
            pageNum={pageNum}
            pageSize={PAGE_SIZE}
            onPageChange={(page) => setPageNum(page)}
            onOpen={openItem}
            onStar={async (file, starred) => {
              try {
                await fileApi.star(file.id, starred);
                // 局部更新收藏状态, 不重载列表, 顺序保持不变
                setFiles((prev) => prev.map((f) => (f.id === file.id ? { ...f, isStarred: starred ? 1 : 0 } : f)));
              } catch (e) {
                message.error(e instanceof Error ? e.message : '操作失败');
              }
            }}
            onShare={setShareTarget}
            onDownload={handleDownload}
            onRename={requestRename}
            onMove={setMoveTarget}
            onCopy={setCopyFile}
            onDelete={handleDelete}
            onMoveToFolder={handleMoveByDrag}
            onSelectionChange={(fs, ds) => {
              setSelectedFiles(fs);
              setSelectedFolders(ds);
            }}
          />
        )}
      </div>

      {/* 新建文件夹 */}
      <Modal
        title="新建文件夹"
        open={newFolderOpen}
        onCancel={() => setNewFolderOpen(false)}
        okText="创建"
        width={380}
        destroyOnHidden
        footer={null}
      >
        <NewFolderForm
          onCancel={() => setNewFolderOpen(false)}
          onSubmit={async (name) => {
            try {
              await folderApi.create(folderId, name);
              message.success('文件夹已创建');
              setNewFolderOpen(false);
              load();
            } catch (e) {
              message.error(e instanceof Error ? e.message : '创建失败');
            }
          }}
        />
      </Modal>

      {/* 重命名 */}
      <Modal
        title="重命名"
        open={renameTarget !== null}
        onCancel={() => setRenameTarget(null)}
        onOk={handleRename}
        okText="确定"
        width={380}
        destroyOnHidden
        footer={null}
      >
        {renameTarget && (
          <RenameForm
            initial={renameTarget.name}
            onCancel={() => setRenameTarget(null)}
            onSubmit={async (name) => {
              setRenameTarget((prev) => (prev ? { ...prev, name } : prev));
              try {
                if (renameTarget.type === 'folder') {
                  await folderApi.rename(renameTarget.id, name);
                } else {
                  await fileApi.rename(renameTarget.id, name);
                }
                message.success('重命名成功');
                setRenameTarget(null);
                load();
              } catch (e) {
                message.error(e instanceof Error ? e.message : '重命名失败');
              }
            }}
          />
        )}
      </Modal>

      {/* 移动 / 复制 / 批量移动 */}
      <MoveModal open={moveTarget !== null} title="移动到…" onOk={handleMove} onCancel={() => setMoveTarget(null)} />
      <MoveModal
        open={copyFile !== null}
        title={`复制「${copyFile?.fileName ?? ''}」到…`}
        onOk={handleCopy}
        onCancel={() => setCopyFile(null)}
      />
      <MoveModal open={batchMoveOpen} title="批量移动到…" onOk={batchMove} onCancel={() => setBatchMoveOpen(false)} />

      {/* 分享 */}
      <ShareModal
        open={shareTarget !== null}
        targetType={'folderName' in (shareTarget ?? {}) ? 2 : 1}
        targetIds={shareTarget ? [shareTarget.id] : []}
        onClose={() => setShareTarget(null)}
        onCreated={() => message.success('分享已创建')}
      />

      {/* 预览 */}
      <PreviewModal file={previewFile} onClose={() => setPreviewFile(null)} onShare={setShareTarget} onChanged={load} />
    </div>
  );
}

function NewFolderForm({ onCancel, onSubmit }: { onCancel: () => void; onSubmit: (name: string) => Promise<void> }) {
  const [name, setName] = useState('');
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
    <div>
      <Input
        placeholder="文件夹名称"
        value={name}
        maxLength={255}
        onChange={(e) => setName(e.target.value)}
        onPressEnter={submit}
        autoFocus
      />
      <div style={{ marginTop: 12, textAlign: 'right' }}>
        <Space>
          <Button onClick={onCancel}>取消</Button>
          <Button type="primary" loading={submitting} onClick={submit}>
            创建
          </Button>
        </Space>
      </div>
    </div>
  );
}

function RenameForm({ initial, onCancel, onSubmit }: { initial: string; onCancel: () => void; onSubmit: (name: string) => Promise<void> }) {
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
    <div>
      <Input
        value={name}
        maxLength={255}
        onChange={(e) => setName(e.target.value)}
        onPressEnter={submit}
        autoFocus
      />
      <div style={{ marginTop: 12, textAlign: 'right' }}>
        <Space>
          <Button onClick={onCancel}>取消</Button>
          <Button type="primary" loading={submitting} onClick={submit}>
            确定
          </Button>
        </Space>
      </div>
    </div>
  );
}