import { useEffect, useState } from 'react';
import { fileApi } from '../api';
import type { NimbusFile } from '../api/types';
import { FileTable } from '../components/FileTable';
import type { RowItem } from '../components/FileTable';
import { usePageActions } from '../hooks/usePageActions';

/** 收藏: 全部收藏文件 */
export default function Starred() {
  const [files, setFiles] = useState<NimbusFile[]>([]);
  const [loading, setLoading] = useState(true);
  const actions = usePageActions(loadFiles);

  async function loadFiles() {
    setLoading(true);
    try {
      setFiles(await fileApi.starred());
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadFiles();
  }, []);

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>收藏</h2>
          <div className="page-sub">收藏的文件会同步到所有设备, 取消收藏不影响原文件</div>
        </div>
      </div>
      <div className="page-card">
        <FileTable
          loading={loading}
          files={files}
          onOpen={(item: RowItem) => actions.open(item)}
          onStar={actions.star}
          onShare={actions.share}
          onDownload={actions.download}
          onRename={actions.rename}
          onMove={actions.move}
          onCopy={actions.copy}
          onDelete={actions.remove}
        />
      </div>
      {actions.renderModals()}
    </div>
  );
}