import { Alert, Spin, Tabs } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { download } from '../api/client';
import { previewApi } from '../api';
import type { NimbusFile } from '../api/types';
import { categoryOf } from './FileIcon';
import DOMPurify from 'dompurify';
import { marked } from 'marked';

/** 文本类扩展名(直接按文本展示) */
const TEXT_EXTS = new Set([
  'txt', 'log', 'json', 'xml', 'yml', 'yaml', 'sql', 'sh', 'bat', 'ini', 'conf', 'properties',
  'js', 'ts', 'jsx', 'tsx', 'css', 'scss', 'less', 'html', 'htm', 'vue', 'java', 'py', 'c', 'h', 'cpp', 'go', 'rs', 'php', 'rb',
]);
/** Markdown */
const MD_EXTS = new Set(['md', 'markdown']);
/** 电子表格(SheetJS 解析) */
const SHEET_EXTS = new Set(['xlsx', 'xls', 'csv']);
/** Word 文档(docx-preview 渲染; 旧版 doc 二进制无法前端解析) */
const DOCX_EXTS = new Set(['docx']);
/** PDF(浏览器内嵌) */
const PDF_EXTS = new Set(['pdf']);

/** 超过该大小(bytes)的文本类文件不再在线加载, 提示下载 */
const MAX_TEXT_SIZE = 2 * 1024 * 1024;
/** 超过该大小的 docx/xlsx 不再在线渲染 */
const MAX_DOC_SIZE = 10 * 1024 * 1024;

interface Props {
  file: NimbusFile;
}

/**
 * 文件在线预览组件: 按扩展名分发渲染主流格式
 * 图片 / 视频 / 音频(流式) / 文本 / Markdown / PDF(内嵌) / Word(docx) / Excel(xlsx·xls·csv)
 * 其余格式(压缩包/旧版 Office 等)给出可下载提示
 */
export function FilePreview({ file }: Props) {
  const ext = (file.fileExt ?? '').toLowerCase();
  const size = Number(file.fileSize ?? 0);

  if (categoryOf(ext) === 'IMAGE') {
    return <ImagePreview file={file} />;
  }
  if (categoryOf(ext) === 'VIDEO') {
    return (
      <div style={{ textAlign: 'center' }}>
        <video src={previewApi.contentUrl(file.id)} controls style={{ width: '100%', maxHeight: 360, borderRadius: 8 }} />
      </div>
    );
  }
  if (categoryOf(ext) === 'AUDIO') {
    return (
      <div style={{ textAlign: 'center', padding: '18px 0' }}>
        <audio src={previewApi.contentUrl(file.id)} controls style={{ width: '100%' }} />
      </div>
    );
  }
  if (PDF_EXTS.has(ext)) {
    return <PdfPreview file={file} />;
  }
  if (DOCX_EXTS.has(ext)) {
    if (size > MAX_DOC_SIZE) return <TooLarge name="Word 文档" size={size} />;
    return <DocxPreview file={file} />;
  }
  if (SHEET_EXTS.has(ext)) {
    if (size > MAX_DOC_SIZE) return <TooLarge name="表格" size={size} />;
    return <SheetPreview file={file} />;
  }
  if (MD_EXTS.has(ext)) {
    if (size > MAX_TEXT_SIZE) return <TooLarge name="Markdown" size={size} />;
    return <MarkdownPreview file={file} />;
  }
  if (TEXT_EXTS.has(ext)) {
    if (size > MAX_TEXT_SIZE) return <TooLarge name="文本" size={size} />;
    return <TextPreview file={file} />;
  }
  // 压缩包 / 旧版 Office(doc·ppt) 等
  return (
    <Alert
      type="info"
      showIcon
      message="该格式暂不支持在线预览"
      description="可下载后查看; 支持在线预览的格式: 图片 / 音视频 / PDF / Word(docx) / Excel(xlsx·xls·csv) / Markdown / 文本代码"
    />
  );
}

function TooLarge({ name, size }: { name: string; size: number }) {
  return (
    <Alert
      type="warning"
      showIcon
      message={`${name}较大(${(size / 1024 / 1024).toFixed(1)} MB), 在线预览可能卡顿`}
      description="建议下载后查看"
    />
  );
}

/** 通用加载态 */
function Loading({ text = '加载预览内容…' }: { text?: string }) {
  return (
    <div style={{ textAlign: 'center', padding: '36px 0', color: 'var(--text3)' }}>
      <Spin /> <span style={{ marginLeft: 8, fontSize: 13 }}>{text}</span>
    </div>
  );
}

function usePreviewError() {
  const [error, setError] = useState<string | null>(null);
  const node = error ? (
    <Alert type="error" showIcon message="预览加载失败" description={error} style={{ marginBottom: 12 }} />
  ) : null;
  return { error, setError, node };
}

/** 图片: blob 加载后展示 */
function ImagePreview({ file }: { file: NimbusFile }) {
  const [url, setUrl] = useState<string | null>(null);
  const { setError, node: errorNode } = usePreviewError();
  useEffect(() => {
    let objectUrl: string | null = null;
    download(previewApi.contentUrl(file.id))
      .then((blob) => {
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch((e) => setError(e instanceof Error ? e.message : '加载失败'));
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [file.id]);
  if (errorNode) return errorNode;
  if (!url) return <Loading text="加载图片…" />;
  return (
    <div style={{ textAlign: 'center' }}>
      <img src={url} alt={file.fileName} style={{ maxWidth: '100%', maxHeight: 340, borderRadius: 8 }} />
    </div>
  );
}

/** 文本/代码 */
function TextPreview({ file }: { file: NimbusFile }) {
  const [text, setText] = useState<string | null>(null);
  const { setError, node: errorNode } = usePreviewError();
  useEffect(() => {
    download(previewApi.contentUrl(file.id))
      .then(async (blob) => setText(await blob.text()))
      .catch((e) => setError(e instanceof Error ? e.message : '加载失败'));
  }, [file.id, setError]);
  if (errorNode) return errorNode;
  if (text === null) return <Loading />;
  return (
    <pre className="nimbus-preview-text">{text}</pre>
  );
}

/** Markdown: marked 渲染 + DOMPurify 净化 */
function MarkdownPreview({ file }: { file: NimbusFile }) {
  const [html, setHtml] = useState<string | null>(null);
  const { setError, node: errorNode } = usePreviewError();
  useEffect(() => {
    download(previewApi.contentUrl(file.id))
      .then(async (blob) => {
        const raw = await blob.text();
        const rendered = await marked.parse(raw);
        setHtml(DOMPurify.sanitize(rendered));
      })
      .catch((e) => setError(e instanceof Error ? e.message : '加载失败'));
  }, [file.id, setError]);
  if (errorNode) return errorNode;
  if (html === null) return <Loading text="渲染 Markdown…" />;
  // 内容已净化
  return <div className="nimbus-preview-md" dangerouslySetInnerHTML={{ __html: html }} />;
}

/** PDF: 内嵌 iframe(blob 地址, 带 MIME 保证浏览器原生渲染) */
function PdfPreview({ file }: { file: NimbusFile }) {
  const [url, setUrl] = useState<string | null>(null);
  const { setError, node: errorNode } = usePreviewError();
  useEffect(() => {
    let objectUrl: string | null = null;
    download(previewApi.contentUrl(file.id))
      .then((blob) => {
        const pdfBlob = blob.type === 'application/pdf' ? blob : new Blob([blob], { type: 'application/pdf' });
        objectUrl = URL.createObjectURL(pdfBlob);
        setUrl(objectUrl);
      })
      .catch((e) => setError(e instanceof Error ? e.message : '加载失败'));
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [file.id]);
  if (errorNode) return errorNode;
  if (!url) return <Loading text="加载 PDF…" />;
  return (
    <iframe
      src={url}
      title={file.fileName}
      style={{ width: '100%', height: 420, border: '1px solid var(--border)', borderRadius: 8, background: '#fff' }}
    />
  );
}

/** Word(docx): docx-preview 渲染 */
function DocxPreview({ file }: { file: NimbusFile }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [status, setStatus] = useState<'loading' | 'done' | 'error'>('loading');
  const [error, setError] = useState('');
  useEffect(() => {
    let cancelled = false;
    setStatus('loading');
    download(previewApi.contentUrl(file.id))
      .then(async (blob) => {
        if (cancelled) return;
        const buffer = await blob.arrayBuffer();
        const { renderAsync } = await import('docx-preview');
        if (cancelled || !containerRef.current) return;
        containerRef.current.innerHTML = '';
        await renderAsync(buffer, containerRef.current, undefined, { inWrapper: true, ignoreWidth: false });
        setStatus('done');
      })
      .catch((e) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : '渲染失败');
          setStatus('error');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [file.id]);
  if (status === 'error') {
    return <Alert type="error" showIcon message="Word 文档渲染失败" description={error} />;
  }
  return (
    <div>
      {status === 'loading' && <Loading text="渲染 Word 文档…" />}
      <div ref={containerRef} className="nimbus-preview-docx" style={{ display: status === 'done' ? 'block' : 'none' }} />
    </div>
  );
}

/** Excel(xlsx/xls/csv): SheetJS 解析, 多 Sheet 切换, 限制行列 */
function SheetPreview({ file }: { file: NimbusFile }) {
  const [sheets, setSheets] = useState<{ name: string; html: string }[]>([]);
  const { setError, node: errorNode } = usePreviewError();
  useEffect(() => {
    let cancelled = false;
    download(previewApi.contentUrl(file.id))
      .then(async (blob) => {
        if (cancelled) return;
        const XLSX = await import('xlsx');
        const data = await blob.arrayBuffer();
        const wb = XLSX.read(data, { type: 'array' });
        const list = wb.SheetNames.slice(0, 10).map((name) => {
          const ws = wb.Sheets[name];
          // 限制 200 行, 防止超大表卡死
          const range = ws['!ref'] ? XLSX.utils.decode_range(ws['!ref']) : null;
          if (range && range.e.r > 200) range.e.r = 200;
          const html = XLSX.utils.sheet_to_html(range ? { ...ws, '!ref': XLSX.utils.encode_range(range) } : ws, { editable: false });
          return { name, html: DOMPurify.sanitize(html) };
        });
        if (!cancelled) setSheets(list);
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : '解析失败');
      });
    return () => {
      cancelled = true;
    };
  }, [file.id, setError]);
  if (errorNode) return errorNode;
  if (sheets.length === 0) return <Loading text="解析表格…" />;
  if (sheets.length === 1) {
    return <div className="nimbus-preview-sheet" dangerouslySetInnerHTML={{ __html: sheets[0].html }} />;
  }
  return (
    <Tabs
      size="small"
      items={sheets.map((s) => ({
        key: s.name,
        label: s.name,
        children: <div className="nimbus-preview-sheet" dangerouslySetInnerHTML={{ __html: s.html }} />,
      }))}
    />
  );
}

export default FilePreview;