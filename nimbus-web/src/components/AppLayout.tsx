import { Layout, Menu, Avatar, Dropdown, Input, Button, Badge, Progress, App as AntdApp } from 'antd';
import { createContext, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  DeleteOutlined,
  FolderOutlined,
  HistoryOutlined,
  LogoutOutlined,
  SearchOutlined,
  SettingOutlined,
  StarOutlined,
  SwapOutlined,
  TeamOutlined,
  UploadOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { quotaApi } from '../api';
import type { QuotaVO } from '../api/types';
import { UpgradeModal } from './UpgradeModal';
import FloatingTransfer from './FloatingTransfer';
import { useAuth } from '../store/auth';
import { uploader, formatSize } from '../store/uploader';

/** 当前浏览文件夹上下文: 顶栏「上传」上传到当前文件夹 */
export const CurrentFolderContext = createContext<{ folderId: string; setFolderId: (id: string) => void }>({
  folderId: '0',
  setFolderId: () => {},
});

/** 顶栏搜索框上下文: 受控关键字, 供搜索页「重置条件」时同步清空 */
export const SearchBarContext = createContext<{ keyword: string; setKeyword: (k: string) => void }>({
  keyword: '',
  setKeyword: () => {},
});

/** 升级扩容弹窗上下文: 侧边栏与设置页共用入口 */
export const UpgradeContext = createContext<{ openUpgrade: () => void }>({
  openUpgrade: () => {},
});

const MENUS = [
  { key: '/files', icon: <FolderOutlined />, label: '我的文件' },
  { key: '/recent', icon: <HistoryOutlined />, label: '最近' },
  { key: '/starred', icon: <StarOutlined />, label: '收藏' },
  { key: '/shares', icon: <TeamOutlined />, label: '共享协作' },
  { key: '/trash', icon: <DeleteOutlined />, label: '回收站' },
  { key: '/transfers', icon: <SwapOutlined />, label: '传输管理' },
];

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuth();
  const { message } = AntdApp.useApp();
  const [quota, setQuota] = useState<QuotaVO | null>(null);
  const [folderId, setFolderId] = useState('0');
  /** 顶栏搜索框受控关键字 */
  const [searchKeyword, setSearchKeyword] = useState('');
  const [uploadCount, setUploadCount] = useState(0);
  /** 升级扩容弹窗开关(侧边栏与设置页共用) */
  const [upgradeOpen, setUpgradeOpen] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const refreshQuota = useCallback(() => {
    quotaApi
      .get()
      .then(setQuota)
      .catch(() => setQuota(null));
  }, []);

  const openUpgrade = useCallback(() => setUpgradeOpen(true), []);

  const selectedKey = useMemo(() => {
    const match = MENUS.find((m) => location.pathname.startsWith(m.key));
    return match?.key ?? '';
  }, [location.pathname]);

  useEffect(() => {
    refreshQuota();
  }, [location.pathname, refreshQuota]);

  useEffect(() => {
    const refresh = () => {
      const running = uploader.tasks.filter((t) => t.status === 'uploading' || t.status === 'waiting').length;
      setUploadCount(running);
    };
    refresh();
    const unsub = uploader.subscribe(refresh);
    return unsub;
  }, []);

  const handleUploadClick = () => fileInput.current?.click();

  const handleFilesChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    if (files.length > 0) {
      uploader.enqueue(files, folderId);
      message.success(`${files.length} 个文件已加入上传队列`);
      navigate('/transfers');
    }
    e.target.value = '';
  };

  const quotaPercent = quota ? Math.round((quota.usedSize / quota.totalSize) * 100) : 0;

  return (
    <CurrentFolderContext.Provider value={{ folderId, setFolderId }}>
      <SearchBarContext.Provider value={{ keyword: searchKeyword, setKeyword: setSearchKeyword }}>
      <UpgradeContext.Provider value={{ openUpgrade }}>
        <Layout style={{ height: '100vh', overflow: 'hidden' }}>
        <Layout.Sider width={220} theme="light" style={{ borderRight: '1px solid var(--border)', background: '#fff' }}>
          <div
            style={{
              height: 52,
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              padding: '0 18px',
              fontWeight: 700,
              fontSize: 16,
            }}
          >
            <span
              style={{
                width: 26,
                height: 26,
                borderRadius: 7,
                background: 'var(--accent)',
                color: '#fff',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 14,
              }}
            >
              N
            </span>
            Nimbus 云盘
          </div>
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            items={MENUS}
            onClick={(info) => navigate(info.key)}
            style={{ borderInlineEnd: 'none', padding: '4px 8px' }}
          />
          {/* 存储空间卡片 */}
          <div style={{ padding: '0 16px', position: 'absolute', bottom: 16, width: '100%' }}>
            <div
              style={{
                background: 'var(--bg-app)',
                borderRadius: 12,
                padding: '12px 14px',
              }}
            >
              <div style={{ fontSize: 12.5, fontWeight: 600, marginBottom: 4 }}>存储空间</div>
              <Progress
                percent={quotaPercent}
                showInfo={false}
                strokeColor="#3E63DD"
                trailColor="#DFE3EA"
                size="small"
              />
              <div style={{ fontSize: 12, color: 'var(--text3)', marginTop: 4 }}>
                已用 {quota ? formatSize(quota.usedSize) : '—'} / 共 {quota ? formatSize(quota.totalSize) : '—'}
              </div>
              <div
                style={{
                  marginTop: 8,
                  fontSize: 12,
                  fontWeight: 600,
                  color: 'var(--accent)',
                  border: '1px solid var(--accent-lt2)',
                  borderRadius: 8,
                  textAlign: 'center',
                  padding: '3px 0',
                  cursor: 'pointer',
                }}
                onClick={openUpgrade}
              >
                升级扩容
              </div>
            </div>
          </div>
        </Layout.Sider>

        <Layout>
          <Layout.Header
            style={{
              background: '#fff',
              borderBottom: '1px solid var(--border)',
              padding: '0 20px',
              display: 'flex',
              alignItems: 'center',
              gap: 14,
              height: 56,
              flexShrink: 0,
            }}
          >
            <Input
              prefix={<SearchOutlined style={{ color: 'var(--text3)' }} />}
              placeholder="搜索你的文件…"
              style={{ maxWidth: 360, borderRadius: 8 }}
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              allowClear
              onPressEnter={() => {
                const keyword = searchKeyword.trim();
                if (keyword) navigate(`/search?keyword=${encodeURIComponent(keyword)}`);
              }}
            />
            <div style={{ flex: 1 }} />
            <Badge count={uploadCount} size="small" offset={[-2, 2]}>
              <Button icon={<SwapOutlined />} onClick={() => navigate('/transfers')}>
                传输列表
              </Button>
            </Badge>
            <Button type="primary" icon={<UploadOutlined />} onClick={handleUploadClick}>
              上传
            </Button>
            <input
              ref={fileInput}
              type="file"
              multiple
              hidden
              onChange={handleFilesChange}
            />
            <Dropdown
              menu={{
                items: [
                  { key: 'settings', icon: <SettingOutlined />, label: '设置' },
                  { type: 'divider' },
                  { key: 'logout', icon: <LogoutOutlined />, label: '退出登录' },
                ],
                onClick: async (info) => {
                  if (info.key === 'settings') navigate('/settings');
                  if (info.key === 'logout') {
                    await logout();
                    navigate('/login');
                  }
                },
              }}
            >
              <span style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
                <Avatar size={30} style={{ background: 'var(--accent)' }} icon={<UserOutlined />} />
                <span style={{ fontSize: 13.5, fontWeight: 500 }}>{user?.nickname ?? user?.username ?? '未登录'}</span>
              </span>
            </Dropdown>
          </Layout.Header>
          <Layout.Content style={{ padding: 20, overflow: 'auto', background: 'var(--bg-app)' }}>
            <Outlet />
          </Layout.Content>
        </Layout>
        </Layout>
      </UpgradeContext.Provider>
      </SearchBarContext.Provider>
      <UpgradeModal
        open={upgradeOpen}
        onClose={() => setUpgradeOpen(false)}
        onUpgraded={() => refreshQuota()}
      />
      {/* 右下角悬浮传输面板(下载/上传实时进度, 不自动关闭) */}
      <FloatingTransfer />
    </CurrentFolderContext.Provider>
  );
}