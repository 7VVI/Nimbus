import { Table, Tag, Tooltip, Dropdown, Space, Typography, Skeleton } from 'antd';
import type { TableProps, MenuProps } from 'antd';
import { useState, type DragEvent } from 'react';
import type { NimbusFile, NimbusFolder } from '../api/types';
import { FileIcon } from './FileIcon';
import { formatSize } from '../store/uploader';
import dayjs from 'dayjs';
import {
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  FolderOpenOutlined,
  LinkOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons';

export type RowItem = NimbusFile | NimbusFolder;

export interface FileTableProps {
  loading?: boolean;
  /** 混合行: 文件夹在前 */
  folders?: NimbusFolder[];
  files?: NimbusFile[];
  /** 文件分页 */
  total?: number;
  pageNum?: number;
  pageSize?: number;
  onPageChange?: (page: number, pageSize: number) => void;
  /** 打开: 文件夹进入 / 文件预览 */
  onOpen?: (item: RowItem) => void;
  onStar?: (file: NimbusFile, starred: boolean) => void;
  onShare?: (item: RowItem) => void;
  onDownload?: (file: NimbusFile) => void;
  onRename?: (item: RowItem) => void;
  onMove?: (item: RowItem) => void;
  onCopy?: (file: NimbusFile) => void;
  onDelete?: (item: RowItem) => void;
  onSelectionChange?: (files: NimbusFile[], folders: NimbusFolder[]) => void;
  /** 空态提示 */
  emptyText?: React.ReactNode;
  /** 拖拽移动: 行可拖到文件夹行(由本组件回调), 拖到面包屑等外部区域请自行监听(使用相同 data type) */
  onMoveToFolder?: (rowId: string, targetFolderId: string) => void;
}

/** 拖拽数据 MIME 类型 */
export const DRAG_ROW = 'application/x-nimbus-row';

/** 行 id 编码: 与 rowKey 一致, 供拖拽传递/外部分解 */
export function encodeRowId(item: RowItem): string {
  return rowKeyOf(item);
}

function isFile(item: RowItem): item is NimbusFile {
  return (item as NimbusFile).fileName !== undefined;
}

function rowKeyOf(item: RowItem): string {
  return `${isFile(item) ? 'f' : 'd'}-${item.id}`;
}

/** 首次加载骨架屏, 避免空白表格→内容突现的闪烁 */
function LoadingSkeleton() {
  return (
    <div style={{ padding: '10px 6px' }}>
      <Skeleton active title={false} paragraph={{ rows: 7 }} />
    </div>
  );
}

export function FileTable({
  loading,
  folders = [],
  files = [],
  total,
  pageNum = 1,
  pageSize = 20,
  onPageChange,
  onOpen,
  onStar,
  onShare,
  onDownload,
  onRename,
  onMove,
  onCopy,
  onDelete,
  onSelectionChange,
  emptyText,
  onMoveToFolder,
}: FileTableProps) {
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  const rows: RowItem[] = [...folders, ...files];
  /** 拖拽中的目标文件夹高亮 */
  const [dropTarget, setDropTarget] = useState<string | null>(null);

  // 首次加载(无任何数据)时展示骨架屏, 有数据后的刷新由 Table 自带 loading 承接
  if (loading && rows.length === 0) {
    return <LoadingSkeleton />;
  }

  const rowActions = (item: RowItem): MenuProps['items'] => {
    const isF = isFile(item);
    const items: MenuProps['items'] = [];
    if (isF && onDownload) {
      items.push({ key: 'download', icon: <DownloadOutlined />, label: '下载' });
    }
    if (onRename) items.push({ key: 'rename', icon: <EditOutlined />, label: '重命名' });
    if (onMove) items.push({ key: 'move', icon: <FolderOpenOutlined />, label: '移动到…' });
    if (isF && onCopy) items.push({ key: 'copy', icon: <CopyOutlined />, label: '复制到…' });
    if (isF && onShare) items.push({ key: 'share', icon: <LinkOutlined />, label: '分享' });
    if (onDelete) items.push({ type: 'divider' }, { key: 'delete', icon: <DeleteOutlined />, label: '移入回收站', danger: true });
    return items;
  };

  const onMenuClick = (item: RowItem) => (info: { key: string }) => {
    const isF = isFile(item);
    switch (info.key) {
      case 'download': isF && onDownload?.(item); break;
      case 'rename': onRename?.(item); break;
      case 'move': onMove?.(item); break;
      case 'copy': isF && onCopy?.(item); break;
      case 'share': isF && onShare?.(item); break;
      case 'delete': onDelete?.(item); break;
    }
  };

  const columns: TableProps<RowItem>['columns'] = [
    {
      title: '名称',
      key: 'name',
      ellipsis: true,
      render: (_, item) => (
        <Space
          style={{ cursor: 'pointer' }}
          onClick={() => onOpen?.(item)}
        >
          {isFile(item) ? (
            <FileIcon fileName={item.fileName} ext={item.fileExt} size={34} />
          ) : (
            <FileIcon size={34} />
          )}
          <Typography.Text
            strong={!isFile(item)}
            style={{ fontSize: 13.5 }}
            ellipsis={{ tooltip: isFile(item) ? item.fileName : item.folderName }}
          >
            {isFile(item) ? item.fileName : item.folderName}
          </Typography.Text>
          {isFile(item) && item.isStarred === 1 && (
            <StarFilled style={{ color: '#F5A623', fontSize: 13 }} />
          )}
        </Space>
      ),
    },
    {
      title: '大小',
      key: 'size',
      width: 110,
      render: (_, item) => (
        <span style={{ color: 'var(--text2)' }}>
          {isFile(item) ? formatSize(Number(item.fileSize)) : '—'}
        </span>
      ),
    },
    {
      title: '修改时间',
      key: 'updateTime',
      width: 170,
      render: (_, item) => (
        <span style={{ color: 'var(--text3)', fontSize: 12.5 }}>
          {dayjs(isFile(item) ? item.updateTime : item.updateTime).format('YYYY-MM-DD HH:mm')}
        </span>
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 90,
      render: (_, item) => (
        <Space size={2}>
          {isFile(item) && onStar && (
            <Tooltip title={item.isStarred === 1 ? '取消收藏' : '收藏'}>
              <span
                style={{ cursor: 'pointer', padding: '4px 6px', color: item.isStarred === 1 ? '#F5A623' : 'var(--text3)' }}
                onClick={(e) => {
                  e.stopPropagation();
                  onStar(item, item.isStarred !== 1);
                }}
              >
                {item.isStarred === 1 ? <StarFilled /> : <StarOutlined />}
              </span>
            </Tooltip>
          )}
          <Dropdown menu={{ items: rowActions(item), onClick: onMenuClick(item) }} trigger={['click']}>
            <Tag style={{ cursor: 'pointer', color: 'var(--text2)' }} bordered={false}>
              操作
            </Tag>
          </Dropdown>
        </Space>
      ),
    },
  ];

  return (
    <Table<RowItem>
      rowKey={(item) => rowKeyOf(item)}
      size="middle"
      loading={loading}
      columns={columns}
      dataSource={rows}
      pagination={
        total === undefined
          ? false
          : {
              current: pageNum,
              pageSize,
              total,
              showSizeChanger: true,
              pageSizeOptions: [10, 20, 50, 100],
              showTotal: (t) => `共 ${t} 项`,
              onChange: (page, size) => onPageChange?.(page, size),
            }
      }
      rowSelection={
        onSelectionChange
          ? {
              selectedRowKeys: selectedKeys,
              preserveSelectedRowKeys: true,
              onChange: (keys) => {
                setSelectedKeys(keys);
                onSelectionChange(
                  files.filter((r) => keys.includes(`f-${r.id}`)),
                  folders.filter((r) => keys.includes(`d-${r.id}`)),
                );
              },
            }
          : undefined
      }
      onRow={(item) => {
        const isF = isFile(item);
        const canDrag = onMoveToFolder !== undefined;
        const isDropTarget = !isF && dropTarget === item.id;
        return {
          style: {
            cursor: 'pointer',
            background: isDropTarget ? 'var(--accent-lt)' : undefined,
          },
          draggable: canDrag,
          className: canDrag ? 'nimbus-drag-row' : undefined,
          // 行拖拽源
          onDragStart: (e: DragEvent) => {
            if (!canDrag) return;
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData(DRAG_ROW, rowKeyOf(item));
          },
          onDragEnd: () => {
            setDropTarget(null);
          },
          // 文件夹行作为放置目标(仅响应本组件行拖拽; types 为 DOMStringList, 需转数组判断)
          onDragOver: (e: DragEvent) => {
            if (!canDrag || isF || !Array.from(e.dataTransfer.types).includes(DRAG_ROW)) return;
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            setDropTarget(item.id);
          },
          onDragLeave: () => {
            if (dropTarget === item.id) {
              setDropTarget(null);
            }
          },
          onDrop: (e: DragEvent) => {
            if (!canDrag || isF) return;
            e.preventDefault();
            const rowId = e.dataTransfer.getData(DRAG_ROW);
            setDropTarget(null);
            if (rowId && rowId !== rowKeyOf(item)) {
              onMoveToFolder?.(rowId, item.id);
            }
          },
        };
      }}
      locale={{ emptyText }}
    />
  );
}