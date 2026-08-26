import { App, InputNumber, Modal, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { quotaApi } from '../api';
import type { QuotaVO } from '../api/types';
import { formatSize } from '../store/uploader';

interface Props {
  open: boolean;
  onClose: () => void;
  onUpgraded: (quota: QuotaVO) => void;
}

/** 套餐: 容量 GB */
const PLANS = [
  { gb: 256, name: '个人进阶' },
  { gb: 512, name: '团队协作' },
  { gb: 1024, name: '企业海量' },
  { gb: 2048, name: '旗舰尊享' },
];

const GB = 1024 * 1024 * 1024;

/**
 * 升级扩容弹窗: 套餐选择(只增不减), 确认后立即生效
 * 通过 UpgradeContext 在侧边栏与设置页共用
 */
export function UpgradeModal({ open, onClose, onUpgraded }: Props) {
  const { message } = App.useApp();
  const [current, setCurrent] = useState<QuotaVO | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  /** 自定义容量(GB), 填写后优先于套餐 */
  const [customGb, setCustomGb] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    setSelected(null);
    setCustomGb(null);
    quotaApi
      .get()
      .then(setCurrent)
      .catch(() => setCurrent(null));
  }, [open]);

  /** 目标容量: 自定义优先, 否则取所选套餐 */
  const targetGb = customGb ?? (selected !== null ? selected / GB : null);

  const handleOk = async () => {
    if (targetGb === null || targetGb <= 0) {
      message.warning('请先选择容量套餐或输入自定义容量');
      return;
    }
    setSubmitting(true);
    try {
      const quota = await quotaApi.upgrade(Math.round(targetGb * GB));
      message.success(`扩容成功, 总容量已调整为 ${formatSize(quota.totalSize)}`);
      onUpgraded(quota);
      onClose();
    } catch (e) {
      message.error(e instanceof Error ? e.message : '扩容失败');
    } finally {
      setSubmitting(false);
    }
  };

  const currentGb = current ? Math.round(current.totalSize / GB) : 0;

  return (
    <Modal
      title="升级扩容"
      open={open}
      onCancel={onClose}
      onOk={handleOk}
      okText="确认升级"
      confirmLoading={submitting}
      okButtonProps={{ disabled: targetGb === null || targetGb <= 0 }}
      width={520}
      destroyOnHidden
    >
      {current && (
        <div style={{ fontSize: 12.5, color: 'var(--text2)', marginBottom: 14 }}>
          当前总容量 <Tag color="blue">{formatSize(current.totalSize)}</Tag>
          已用 <Tag color="orange">{formatSize(current.usedSize)}</Tag>
          <span style={{ color: 'var(--text3)' }}>(扩容立即生效, 容量只增不减)</span>
        </div>
      )}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        {PLANS.map((plan) => {
          const active = customGb === null && selected === plan.gb * GB;
          const disabled = plan.gb <= currentGb;
          return (
            <div
              key={plan.gb}
              data-size={plan.gb}
              onClick={() => {
                if (!disabled) {
                  setSelected(plan.gb * GB);
                  setCustomGb(null);
                }
              }}
              style={{
                border: `1px solid ${active ? 'var(--accent)' : 'var(--border)'}`,
                background: active ? 'var(--accent-lt)' : '#fff',
                borderRadius: 10,
                padding: '12px 14px',
                cursor: disabled ? 'not-allowed' : 'pointer',
                opacity: disabled ? 0.45 : 1,
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: 15, fontWeight: 700 }}>{plan.gb >= 1024 ? `${plan.gb / 1024} TB` : `${plan.gb} GB`}</span>
                {plan.gb === currentGb && <Tag color="blue">当前</Tag>}
                {disabled && plan.gb !== currentGb && <Tag>不可选</Tag>}
              </div>
              <div style={{ fontSize: 12, color: 'var(--text3)', marginTop: 2 }}>{plan.name}</div>
            </div>
          );
        })}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 12 }}>
        <span style={{ fontSize: 12.5, color: 'var(--text2)' }}>自定义容量(GB):</span>
        <InputNumber
          min={1}
          max={1024 * 100}
          style={{ width: 160 }}
          placeholder="输入 GB 数"
          value={customGb}
          onChange={(v) => {
            setCustomGb(v ?? null);
            if (v) setSelected(null);
          }}
        />
      </div>
      <div style={{ marginTop: 14, fontSize: 12, color: 'var(--text3)' }}>
        升级后总容量立即生效且不会影响已有文件; 如需更大容量可重复升级叠加。
      </div>
    </Modal>
  );
}