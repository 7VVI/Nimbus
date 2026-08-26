import { App, Avatar, Button, Card, Descriptions, Progress, Space, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { quotaApi } from '../api';
import type { QuotaVO } from '../api/types';
import { useAuth } from '../store/auth';
import { formatSize } from '../store/uploader';
import { LogoutOutlined, RocketOutlined, UserOutlined } from '@ant-design/icons';

/** 设置: 账户信息 + 存储空间 + 退出登录 */
export default function Settings() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [quota, setQuota] = useState<QuotaVO | null>(null);
  const { message } = App.useApp();

  useEffect(() => {
    quotaApi.get().then(setQuota).catch(() => setQuota(null));
  }, []);

  const handleLogout = async () => {
    await logout();
    message.success('已退出登录');
    navigate('/login');
  };

  const percent = quota ? Math.round((quota.usedSize / quota.totalSize) * 100) : 0;

  return (
    <div>
      <div className="page-head">
        <div>
          <h2>设置</h2>
          <div className="page-sub">账户与存储管理</div>
        </div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        {/* 账户信息 */}
        <Card title="账户信息" size="small">
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 16 }}>
            <Avatar size={56} style={{ background: 'var(--accent)' }} icon={<UserOutlined />} />
            <div>
              <div style={{ fontSize: 16, fontWeight: 700 }}>{user?.nickname ?? user?.username}</div>
              <div style={{ fontSize: 12.5, color: 'var(--text3)' }}>@{user?.username}</div>
            </div>
          </div>
          <Descriptions
            column={1}
            size="small"
            items={[
              { key: 'id', label: '用户 ID', children: user?.userId ?? '—' },
              {
                key: 'role',
                label: '角色',
                children: (user?.roleKeys ?? ['netdisk']).map((r) => (
                  <Tag key={r} color={r === 'admin' ? 'gold' : 'blue'}>
                    {r}
                  </Tag>
                )),
              },
            ]}
          />
          <Button danger icon={<LogoutOutlined />} style={{ marginTop: 18 }} onClick={handleLogout}>
            退出登录
          </Button>
        </Card>

        {/* 存储空间 */}
        <Card title="存储空间" size="small">
          <div style={{ textAlign: 'center', padding: '14px 0 6px' }}>
            <div style={{ fontSize: 30, fontWeight: 700, color: 'var(--accent)' }}>
              {quota ? formatSize(quota.usedSize) : '—'}
            </div>
            <div style={{ fontSize: 13, color: 'var(--text3)', marginTop: 2 }}>
              已使用 · 总容量 {quota ? formatSize(quota.totalSize) : '—'}
            </div>
            <Progress
              percent={percent}
              strokeColor="#3E63DD"
              trailColor="#E6E8EB"
              style={{ marginTop: 14 }}
            />
            <div style={{ fontSize: 12.5, color: 'var(--text2)', marginTop: 6 }}>
              剩余 {quota ? formatSize(quota.remainSize) : '—'}
            </div>
          </div>
          <div style={{ display: 'flex', justifyContent: 'center', gap: 10, marginTop: 14 }}>
            <Space>
              <Button type="primary" icon={<RocketOutlined />} onClick={() => message.info('扩容功能升级中, 敬请期待')}>
                升级扩容
              </Button>
            </Space>
          </div>
        </Card>
      </div>
    </div>
  );
}