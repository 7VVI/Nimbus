import { ConfigProvider, App as AntdApp, Skeleton } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import DownloadNotifier from './components/DownloadNotifier';
import { AuthProvider, useAuth } from './store/auth';
import Login from './pages/Login';
import Register from './pages/Register';
import MyFiles from './pages/MyFiles';
import Recent from './pages/Recent';
import Starred from './pages/Starred';
import Shares from './pages/Shares';
import Trash from './pages/Trash';
import Transfers from './pages/Transfers';
import Search from './pages/Search';
import Settings from './pages/Settings';
import ShareAccess from './pages/ShareAccess';
import './styles.css';

/** 需要登录的页面守卫 */
function RequireAuth({ children }: { children: React.ReactNode }) {
  const { loading, user } = useAuth();
  // 登录态校验/恢复期间展示骨架屏, 避免整页空白一闪
  if (loading) {
    return (
      <div
        style={{
          height: '100vh',
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'center',
          background: 'var(--bg-app)',
          paddingTop: 120,
        }}
      >
        <Skeleton active title={false} paragraph={{ rows: 6 }} style={{ width: 560 }} />
      </div>
    );
  }
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#3E63DD',
          borderRadius: 8,
          fontSize: 13.5,
        },
      }}
    >
      <AntdApp>
        <AuthProvider>
          {/* 下载完成右下角通知(全局, 含免登录分享页) */}
          <DownloadNotifier />
          <BrowserRouter>
            <Routes>
              {/* 认证页(无布局) */}
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />
              {/* 分享访问(免登录) */}
              <Route path="/s/:code" element={<ShareAccess />} />
              {/* 主应用 */}
              <Route
                element={
                  <RequireAuth>
                    <AppLayout />
                  </RequireAuth>
                }
              >
                <Route path="/" element={<Navigate to="/files" replace />} />
                <Route path="/files" element={<MyFiles />} />
                <Route path="/recent" element={<Recent />} />
                <Route path="/starred" element={<Starred />} />
                <Route path="/shares" element={<Shares />} />
                <Route path="/trash" element={<Trash />} />
                <Route path="/transfers" element={<Transfers />} />
                <Route path="/search" element={<Search />} />
                <Route path="/settings" element={<Settings />} />
              </Route>
              <Route path="*" element={<Navigate to="/files" replace />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </AntdApp>
    </ConfigProvider>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(<App />);