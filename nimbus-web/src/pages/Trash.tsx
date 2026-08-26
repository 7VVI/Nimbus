import { App, Button, Popconfirm, Skeleton, Table, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { recycleApi } from '../api';
import type { RecycleItemVO } from '../api/types';
import { FileIcon } from '../components/FileIcon';
import dayjs from 'dayjs';
import { formatSize } from '../store/uploader';
import { DeleteOutlined, InboxOutlined, ReloadOutlined } from '@ant-design/icons';

const PAGE_SIZE = 20;

/** 回收站: 恢复 / 彻底删除 / 清空 */
export default function Trash() {
  const { message } = App.useApp();
  const [items, setItems] = useState<RecycleItemVO[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await recycleApi.page(pageNum, PAGE_SIZE);
      setItems(result.records);
      setTotal(result.total);
    } finally {
      setLoading(false);
    }
  }, [pageNum]);

  useEffect(() => {
    load();
  }, [load]);

  const restore = async (item: RecycleItemVO) => {
    try {
      await recycleApi.restore(item.targetType, item.id);
      message.success('已恢复');
      load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '恢复失败');
    }
  };

  const purge = async (item: RecycleItemVO) => {
    try {
      await recycleApi.purge(item.targetType, item.id);
      message.success('已彻底删除');
      load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '删除失败');
    }
  };

  const clean = async () => {
    try {
      const count = await recycleApi.clean();
      message.success(`已清空回收站, 共删除 ${count} 项`);
      load();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '清空失败');
    }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>回收站</h2>
          <div className="page-sub">{total} 个项目 · 回收站中的文件仍占用存储空间, 彻底删除后释放容量</div>
        </div>
        <Button danger icon={<InboxOutlined />} onClick={clean} disabled={total === 0}>
          清空回收站
        </Button>
      </div>
      <div className="page-card">
        {loading && items.length === 0 ? (
          <div style={{ padding: '10px 6px' }}>
            <Skeleton active title={false} paragraph={{ rows: 7 }} />
          </div>
        ) : (
          <Table<RecycleItemVO>
          rowKey={(item) => `${item.targetType}-${item.id}`}
          size="middle"
          loading={loading}
          dataSource={items}
          pagination={{
            current: pageNum,
            pageSize: PAGE_SIZE,
            total,
            showTotal: (t) => `共 ${t} 项`,
            onChange: setPageNum,
          }}
          columns={[
            {
              title: '名称',
              key: 'name',
              ellipsis: true,
              render: (_, item) => (
                <span style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  {item.targetType === 2 ? (
                    <FileIcon size={32} />
                  ) : (
                    <FileIcon fileName={item.name} ext={item.fileExt} size={32} />
                  )}
                  <span style={{ fontSize: 13.5 }}>{item.name}</span>
                  {item.targetType === 2 && <Tag bordered={false}>文件夹</Tag>}
                </span>
              ),
            },
            {
              title: '大小',
              key: 'size',
              width: 120,
              render: (_, item) => (
                <span style={{ color: 'var(--text2)' }}>
                  {item.targetType === 1 ? formatSize(Number(item.fileSize ?? 0)) : '—'}
                </span>
              ),
            },
            {
              title: '删除时间',
              key: 'deleteTime',
              width: 160,
              render: (_, item) => (
                <span style={{ fontSize: 12.5, color: 'var(--text3)' }}>
                  {item.deleteTime ? dayjs(item.deleteTime).format('YYYY-MM-DD HH:mm') : '—'}
                </span>
              ),
            },
            {
              title: '操作',
              key: 'actions',
              width: 170,
              render: (_, item) => (
                <>
                  <Button size="small" type="link" icon={<ReloadOutlined />} onClick={() => restore(item)}>
                    恢复
                  </Button>
                  <Popconfirm
                    title="彻底删除后不可恢复, 确定?"
                    onConfirm={() => purge(item)}
                    okText="彻底删除"
                    okButtonProps={{ danger: true }}
                  >
                    <Button size="small" type="link" danger icon={<DeleteOutlined />}>
                      彻底删除
                    </Button>
                  </Popconfirm>
                </>
              ),
            },
          ]}
        />
        )}
      </div>
    </div>
  );
}