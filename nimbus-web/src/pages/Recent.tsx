import { useEffect, useState } from 'react';
import { fileApi } from '../api';
import type { NimbusFile } from '../api/types';
import { FileTable } from '../components/FileTable';
import type { RowItem } from '../components/FileTable';
import { usePageActions } from '../hooks/usePageActions';

/** 最近: 按修改时间倒序 */
export default function Recent() {
  const [files, setFiles] = useState<NimbusFile[]>([]);
  const [loading, setLoading] = useState(true);
  const actions = usePageActions(loadFiles);

  async function loadFiles() {
    setLoading(true);
    try {
      setFiles(await fileApi.recent(100));
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
          <h2>最近</h2>
          <div className="page-sub">按最近修改时间排列</div>
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