import { Button, Checkbox, Form, Input, Modal, Radio, InputNumber, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { shareApi } from '../api';
import type { NimbusShare } from '../api/types';
import { message } from 'antd';

interface Props {
  open: boolean;
  /** 分享目标: 文件/文件夹 */
  targetType: number;
  targetIds: string[];
  onClose: () => void;
  onCreated: (share: NimbusShare) => void;
}

interface FormValues {
  shareType: number;
  password?: string;
  /** 权限位集合(位掩码元素): 1预览 2下载 4转存 */
  permissions: number[];
  expireType: number;
  expireDays?: number;
}

/** 权限位: 与后端掩码一致 */
const PERM_VIEW = 1;
const PERM_DOWNLOAD = 2;
const PERM_SAVE = 4;

/** 权限集合转位掩码 */
const maskOf = (values: number[]) => (values ?? []).reduce((acc, v) => acc | v, 0);

/** 提取码字符集: 去掉易混淆的 0/O/1/I */
const CODE_CHARS = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
/** 提取码长度 */
const CODE_LENGTH = 6;

/** 生成随机提取码(密码学安全随机数) */
function generateCode(): string {
  const bytes = new Uint32Array(CODE_LENGTH);
  crypto.getRandomValues(bytes);
  let code = '';
  for (let i = 0; i < CODE_LENGTH; i++) {
    code += CODE_CHARS[bytes[i] % CODE_CHARS.length];
  }
  return code;
}

/** 新建分享弹窗: 类型(公开/密码) + 权限(预览/下载/转存) + 有效期; 加密分享提取码自动生成 */
export function ShareModal({ open, targetType, targetIds, onClose, onCreated }: Props) {
  const [form] = Form.useForm<FormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<NimbusShare | null>(null);

  // 切到「加密分享」时自动生成提取码(未填过才生成, 用户可修改)
  const shareType = Form.useWatch('shareType', form);
  useEffect(() => {
    if (shareType === 2) {
      const current = form.getFieldValue('password');
      if (!current) {
        form.setFieldValue('password', generateCode());
      }
    }
  }, [shareType, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    const permission = maskOf(values.permissions);
    if (permission === 0) {
      message.warning('请至少选择一项分享权限');
      return;
    }
    setSubmitting(true);
    try {
      const share = await shareApi.create({
        targetType,
        targetIds,
        shareType: values.shareType,
        password: values.shareType === 2 ? values.password : undefined,
        permission,
        expireType: values.expireType,
        expireDays: values.expireType === 2 ? values.expireDays : undefined,
      });
      setResult(share);
      onCreated(share);
    } finally {
      setSubmitting(false);
    }
  };

  const shareUrl = (code: string) => `${location.origin}/s/${code}`;

  /** 复制用链接: 加密分享自动附带提取码(?code=), 接收方粘贴即用 */
  const shareLinkWithCode = (share: NimbusShare) =>
    share.shareType === 2 && share.password
      ? `${shareUrl(share.shortCode)}?code=${encodeURIComponent(share.password)}`
      : shareUrl(share.shortCode);

  const handleCopy = async (text: string, tip = '已复制') => {
    await navigator.clipboard.writeText(text);
    message.success(tip);
  };

  const close = () => {
    setResult(null);
    form.resetFields();
    onClose();
  };

  return (
    <Modal
      title={<>{result ? '分享已创建' : '新建分享'}</>}
      open={open}
      // 结果态: 点「完成/取消」只关闭; 配置态才提交创建
      onOk={result ? close : handleOk}
      onCancel={close}
      okText={result ? '完成' : '创建分享'}
      confirmLoading={submitting}
      destroyOnHidden
      width={460}
    >
      {result ? (
        <div>
          <div
            style={{
              background: '#E9F5EE',
              color: '#299764',
              borderRadius: 8,
              padding: '10px 14px',
              marginBottom: 12,
              fontSize: 13,
            }}
          >
            分享已创建{result.shareType === 2 ? ', 链接已附带提取码, 粘贴到浏览器即可访问' : ', 任何持有链接的人都可以访问'}
          </div>
          {/* 一键复制: 加密分享复制完整链接(含提取码), 粘贴浏览器即用 */}
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <Input readOnly value={shareLinkWithCode(result)} />
            <Button
              type="primary"
              onClick={() =>
                handleCopy(
                  shareLinkWithCode(result),
                  result.shareType === 2 ? '已复制, 粘贴到浏览器即可访问' : '链接已复制',
                )
              }
            >
              一键复制
            </Button>
          </div>
          {result.shareType === 2 && (
            <div style={{ color: 'var(--text2)', fontSize: 12.5, marginTop: 8 }}>
              提取码: <Typography.Text strong style={{ letterSpacing: 1 }}>{result.password}</Typography.Text>
              <span style={{ color: 'var(--text3)' }}> (已包含在链接中, 对方无需手动输入)</span>
            </div>
          )}
        </div>
      ) : (
        <Form form={form} layout="vertical" initialValues={{ shareType: 1, permissions: [PERM_VIEW, PERM_DOWNLOAD, PERM_SAVE], expireType: 1 }} preserve={false}>
          <Form.Item name="shareType" label="分享方式">
            <Radio.Group>
              <Radio value={1}>公开分享</Radio>
              <Radio value={2}>加密分享(提取码)</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, c) => p.shareType !== c.shareType}>
            {({ getFieldValue }) =>
              getFieldValue('shareType') === 2 ? (
                <Form.Item
                  name="password"
                  label="提取码"
                  extra="已自动生成, 可直接修改"
                  rules={[{ required: true, message: '提取码不能为空' }, { max: 32, message: '长度不能超过32' }]}
                >
                  <Input placeholder="6 位数字或字母(自动生成)" />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item
            name="permissions"
            label="分享权限"
            rules={[{ required: true, message: '请至少选择一项权限' }]}
            initialValue={[PERM_VIEW, PERM_DOWNLOAD, PERM_SAVE]}
          >
            <Checkbox.Group
              options={[
                { label: '可预览', value: PERM_VIEW },
                { label: '可下载', value: PERM_DOWNLOAD },
                { label: '可转存', value: PERM_SAVE },
              ]}
            />
          </Form.Item>
          <Form.Item name="expireType" label="有效期">
            <Radio.Group>
              <Radio value={1}>永久有效</Radio>
              <Radio value={2}>限时有效</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(p, c) => p.expireType !== c.expireType}>
            {({ getFieldValue }) =>
              getFieldValue('expireType') === 2 ? (
                <Form.Item name="expireDays" label="有效天数" rules={[{ required: true, message: '请输入有效天数' }]}>
                  <InputNumber min={1} max={365} style={{ width: 160 }} placeholder="7" />
                </Form.Item>
              ) : null
            }
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
}