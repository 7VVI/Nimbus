import { App, Button, Form, Input } from 'antd';
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../store/auth';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      await login(values.username, values.password);
      message.success('登录成功');
      navigate(params.get('redirect') ?? '/files', { replace: true });
    } catch (e) {
      message.error(e instanceof Error ? e.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-brand">
          <div className="logo">N</div>
          <div>
            <div className="name">Nimbus 云盘</div>
            <div className="slogan">你的文件, 随时可用</div>
          </div>
        </div>
        <Form layout="vertical" onFinish={handleSubmit} initialValues={{ username: 'admin', password: 'admin123' }}>
          <Form.Item name="username" label="账号" rules={[{ required: true, message: '请输入账号' }]}>
            <Input placeholder="请输入账号" size="large" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password placeholder="请输入密码" size="large" autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large" loading={loading} style={{ marginTop: 6 }}>
            登录
          </Button>
        </Form>
        <div style={{ marginTop: 16, textAlign: 'center', fontSize: 13, color: 'var(--text3)' }}>
          还没有账号? <Link to="/register">立即注册</Link>
        </div>
      </div>
    </div>
  );
}