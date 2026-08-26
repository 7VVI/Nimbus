import { App, Button, Form, Input } from 'antd';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../api';

export default function Register() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (values: { username: string; password: string; nickname?: string }) => {
    setLoading(true);
    try {
      await authApi.register(values);
      message.success('注册成功, 请登录');
      navigate('/login');
    } catch (e) {
      message.error(e instanceof Error ? e.message : '注册失败');
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
            <div className="name">创建账号</div>
            <div className="slogan">注册即送 128GB 免费存储空间</div>
          </div>
        </div>
        <Form layout="vertical" onFinish={handleSubmit}>
          <Form.Item
            name="username"
            label="账号"
            rules={[
              { required: true, message: '请输入账号' },
              { min: 3, max: 30, message: '账号长度需在3-30之间' },
            ]}
          >
            <Input placeholder="3-30 位字母或数字" size="large" autoComplete="username" />
          </Form.Item>
          <Form.Item
            name="nickname"
            label="昵称"
            rules={[{ max: 30, message: '昵称长度不能超过30' }]}
          >
            <Input placeholder="选填" size="large" />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, max: 64, message: '密码长度需在6-64之间' },
            ]}
          >
            <Input.Password placeholder="6 位以上" size="large" autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirm"
            label="确认密码"
            dependencies={['password']}
            rules={[
              { required: true, message: '请再次输入密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) return Promise.resolve();
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password placeholder="再次输入密码" size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large" loading={loading} style={{ marginTop: 6 }}>
            注册
          </Button>
        </Form>
        <div style={{ marginTop: 16, textAlign: 'center', fontSize: 13, color: 'var(--text3)' }}>
          已有账号? <Link to="/login">直接登录</Link>
        </div>
      </div>
    </div>
  );
}