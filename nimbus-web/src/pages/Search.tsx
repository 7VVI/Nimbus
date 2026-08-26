import { Button, Empty, Select, Space, Tag } from 'antd';
import { useCallback, useContext, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { fileApi } from '../api';
import type { NimbusFile } from '../api/types';
import { FileTable } from '../components/FileTable';
import type { RowItem } from '../components/FileTable';
import { SearchBarContext } from '../components/AppLayout';
import { usePageActions } from '../hooks/usePageActions';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';

const PAGE_SIZE = 20;

/**
 * 搜索: 关键字由顶栏搜索框输入(URL ?keyword= 传入),
 * 页面内仅提供类型过滤与重置条件, 避免重复搜索框
 */
export default function Search() {
  const [params] = useSearchParams();
  const keywordFromUrl = params.get('keyword') ?? '';
  const navigate = useNavigate();
  /** 顶栏搜索框联动: 重置条件时同步清空 */
  const { setKeyword: setHeaderKeyword } = useContext(SearchBarContext);
  const [keyword, setKeyword] = useState(keywordFromUrl);
  const [fileType, setFileType] = useState<string>('');
  const [files, setFiles] = useState<NimbusFile[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [loading, setLoading] = useState(false);

  const reload = useCallback(async () => {
    if (!keyword) {
      setFiles([]);
      setTotal(0);
      return;
    }
    setLoading(true);
    try {
      const result = await fileApi.page({
        keyword,
        fileType: fileType || undefined,
        pageNum,
        pageSize: PAGE_SIZE,
      });
      setFiles(result.records);
      setTotal(result.total);
    } finally {
      setLoading(false);
    }
  }, [keyword, fileType, pageNum]);

  const actions = usePageActions(reload);

  // 顶栏搜索跳转带来的关键词语境
  useEffect(() => {
    setKeyword(keywordFromUrl);
  }, [keywordFromUrl]);

  useEffect(() => {
    setPageNum(1);
  }, [keyword, fileType]);

  useEffect(() => {
    reload();
  }, [reload]);

  /** 重置全部条件并返回「我的文件」: 清空顶栏搜索框后跳转 */
  const resetAll = () => {
    setHeaderKeyword('');
    navigate('/files', { replace: true });
  };

  const hasCondition = keyword !== '' || fileType !== '';

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>搜索</h2>
          <div className="page-sub">在顶部搜索框输入文件名关键字, 支持类型过滤</div>
        </div>
      </div>
      {/* 条件栏: 当前关键词 + 类型过滤 + 重置(关键字输入由顶栏搜索框承担) */}
      <div className="page-card" style={{ padding: '10px 16px', marginBottom: 12 }}>
        {keyword ? (
          <Space wrap>
            <Tag icon={<SearchOutlined />} color="blue" style={{ padding: '3px 10px', borderRadius: 6 }}>
              关键字: {keyword}
            </Tag>
            <Select
              value={fileType}
              style={{ width: 130 }}
              onChange={setFileType}
              options={[
                { value: '', label: '全部类型' },
                { value: 'IMAGE', label: '图片' },
                { value: 'VIDEO', label: '视频' },
                { value: 'AUDIO', label: '音频' },
                { value: 'DOCUMENT', label: '文档' },
                { value: 'ARCHIVE', label: '压缩包' },
                { value: 'CODE', label: '代码' },
                { value: 'OTHER', label: '其他' },
              ]}
            />
            {hasCondition && (
              <Button icon={<ReloadOutlined />} onClick={resetAll}>
                重置条件
              </Button>
            )}
          </Space>
        ) : (
          <span style={{ fontSize: 13, color: 'var(--text3)' }}>
            <SearchOutlined style={{ marginRight: 6 }} />
            在顶部搜索框输入文件名关键字开始搜索
          </span>
        )}
      </div>
      <div className="page-card">
        {keyword ? (
          <FileTable
            loading={loading}
            files={files}
            total={total}
            pageNum={pageNum}
            pageSize={PAGE_SIZE}
            onPageChange={(page) => setPageNum(page)}
            onOpen={(item: RowItem) => actions.open(item)}
            onStar={actions.star}
            onShare={actions.share}
            onDownload={actions.download}
            onRename={actions.rename}
            onMove={actions.move}
            onCopy={actions.copy}
            onDelete={actions.remove}
            emptyText={<Empty description={`未找到与「${keyword}」相关的文件`} />}
          />
        ) : (
          <Empty description="暂无搜索条件" style={{ padding: '48px 0' }} />
        )}
      </div>
      {actions.renderModals()}
    </div>
  );
}