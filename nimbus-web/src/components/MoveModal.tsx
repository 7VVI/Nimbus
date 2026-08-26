import { Modal, Tree, Empty, Spin } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { folderApi } from '../api';
import type { FolderTreeVO } from '../api/types';

interface Props {
  open: boolean;
  title?: string;
  /** 禁止选择的文件夹(移动时排除自身/子树, 传 ids) */
  disabledIds?: string[];
  onOk: (folderId: string) => void;
  onCancel: () => void;
}

/** 移动/复制目标选择弹窗: 目录树单选, 根目录代表 folderId=0 */
export function MoveModal({ open, title = '移动到…', disabledIds = [], onOk, onCancel }: Props) {
  const [tree, setTree] = useState<FolderTreeVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<string>('');
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    folderApi
      .tree()
      .then(setTree)
      .finally(() => setLoading(false));
  }, [open]);

  const treeData = useMemo(() => buildTreeData(tree, disabledIds), [tree, disabledIds]);

  const handleOk = async () => {
    setConfirming(true);
    try {
      await onOk(selected);
    } finally {
      setConfirming(false);
      setSelected('');
    }
  };

  return (
    <Modal
      title={title}
      open={open}
      onOk={handleOk}
      onCancel={() => {
        setSelected('');
        onCancel();
      }}
      okText="确定"
      okButtonProps={{ disabled: selected === '' }}
      confirmLoading={confirming}
    >
      <Spin spinning={loading}>
        <div style={{ maxHeight: 380, overflow: 'auto', padding: 8 }}>
          <div
            style={{
              padding: '8px 10px',
              borderRadius: 6,
              marginBottom: 6,
              cursor: 'pointer',
              fontWeight: selected === '' ? 600 : 400,
              background: selected === '' ? '#EDF1FE' : 'transparent',
            }}
            onClick={() => setSelected('')}
          >
            📁 我的文件(根目录)
          </div>
          {treeData.length === 0 ? (
            <Empty description="暂无文件夹" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <Tree
              treeData={treeData}
              defaultExpandAll
              selectedKeys={selected ? [selected] : []}
              onSelect={(keys) => {
                if (keys.length > 0) setSelected(String(keys[0]));
              }}
            />
          )}
        </div>
      </Spin>
    </Modal>
  );
}

function buildTreeData(nodes: FolderTreeVO[], disabledIds: string[]): { title: string; key: string; children: unknown[]; disabled: boolean }[] {
  return nodes.map((node) => ({
    title: node.folderName,
    key: node.id,
    disabled: disabledIds.includes(node.id),
    children: buildTreeData(node.children, disabledIds),
  }));
}