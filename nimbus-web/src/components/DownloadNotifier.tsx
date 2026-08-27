import { App, Button } from 'antd';
import { useEffect, useRef } from 'react';
import { downloader } from '../store/downloader';
import { formatSize } from '../store/uploader';

/**
 * 全局下载完成通知: 右下角弹窗, 点击「打开」跳转内联预览地址快速查看文件
 * (不用下载 blob —— 下载接口是 octet-stream, 跳转只会再次触发下载)
 * 挂在 App 根节点(覆盖所有页面, 含免登录分享页)
 */
export default function DownloadNotifier() {
  const { notification } = App.useApp();
  const notified = useRef(new Set<string>());

  useEffect(() => {
    const unsub = downloader.subscribe(() => {
      for (const task of downloader.tasks) {
        if (task.status !== 'done' || !task.openUrl || notified.current.has(task.id)) {
          continue;
        }
        notified.current.add(task.id);
        const url = task.openUrl;
        const open = () => {
          // 打开内联预览地址(正确 MIME, 浏览器直接渲染, 不会重复下载)
          console.log('[nimbus-open]', url);
          window.open(url, '_blank');
        };
        notification.open({
          message: task.fileName,
          description: `${formatSize(task.fileSize ?? task.loaded)} · 下载完成`,
          placement: 'bottomLeft',
          duration: 6,
          onClick: open,
          btn: (
            <Button size="small" type="primary" onClick={(e) => { e.stopPropagation(); open(); }}>
              打开
            </Button>
          ),
        });
      }
    });
    return unsub;
  }, [notification]);

  return null;
}