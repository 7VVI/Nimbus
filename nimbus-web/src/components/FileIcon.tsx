import {
  FileExcelOutlined,
  FileImageOutlined,
  FileMarkdownOutlined,
  FilePdfOutlined,
  FilePptOutlined,
  FileTextOutlined,
  FileWordOutlined,
  FileZipOutlined,
  FolderFilled,
  VideoCameraOutlined,
  AudioOutlined,
  CodeOutlined,
  FileOutlined,
} from '@ant-design/icons';
import type { ReactNode } from 'react';

/** 文件分类(与后端 NetdiskConstants 对应) */
export type FileCategory = 'IMAGE' | 'VIDEO' | 'AUDIO' | 'DOCUMENT' | 'ARCHIVE' | 'CODE' | 'OTHER';

const EXT_CATEGORY: Record<string, FileCategory> = {
  jpg: 'IMAGE', jpeg: 'IMAGE', png: 'IMAGE', gif: 'IMAGE', bmp: 'IMAGE', webp: 'IMAGE', svg: 'IMAGE', ico: 'IMAGE',
  mp4: 'VIDEO', mkv: 'VIDEO', avi: 'VIDEO', mov: 'VIDEO', wmv: 'VIDEO', flv: 'VIDEO', webm: 'VIDEO', m4v: 'VIDEO',
  mp3: 'AUDIO', wav: 'AUDIO', flac: 'AUDIO', aac: 'AUDIO', ogg: 'AUDIO', m4a: 'AUDIO', wma: 'AUDIO',
  doc: 'DOCUMENT', docx: 'DOCUMENT', xls: 'DOCUMENT', xlsx: 'DOCUMENT', ppt: 'DOCUMENT', pptx: 'DOCUMENT',
  pdf: 'DOCUMENT', txt: 'DOCUMENT', md: 'DOCUMENT', csv: 'DOCUMENT',
  zip: 'ARCHIVE', rar: 'ARCHIVE', '7z': 'ARCHIVE', tar: 'ARCHIVE', gz: 'ARCHIVE',
  java: 'CODE', py: 'CODE', js: 'CODE', ts: 'CODE', c: 'CODE', cpp: 'CODE', go: 'CODE',
  html: 'CODE', css: 'CODE', json: 'CODE', xml: 'CODE', sql: 'CODE', yml: 'CODE', yaml: 'CODE', sh: 'CODE',
};

export function categoryOf(ext?: string): FileCategory {
  if (!ext) return 'OTHER';
  return EXT_CATEGORY[ext.toLowerCase()] ?? 'OTHER';
}

const CATEGORY_COLOR: Record<FileCategory, string> = {
  IMAGE: '#0EA5A5',
  VIDEO: '#8E4EC6',
  AUDIO: '#C2255C',
  DOCUMENT: '#3E63DD',
  ARCHIVE: '#DE911D',
  CODE: '#299764',
  OTHER: '#8B909A',
};

const CATEGORY_ICON: Record<FileCategory, ReactNode> = {
  IMAGE: <FileImageOutlined />,
  VIDEO: <VideoCameraOutlined />,
  AUDIO: <AudioOutlined />,
  DOCUMENT: <FileTextOutlined />,
  ARCHIVE: <FileZipOutlined />,
  CODE: <CodeOutlined />,
  OTHER: <FileOutlined />,
};

/** 文档类文件细分图标 */
function docIcon(ext: string): ReactNode {
  switch (ext) {
    case 'pdf': return <FilePdfOutlined />;
    case 'doc': case 'docx': return <FileWordOutlined />;
    case 'xls': case 'xlsx': return <FileExcelOutlined />;
    case 'ppt': case 'pptx': return <FilePptOutlined />;
    case 'md': return <FileMarkdownOutlined />;
    default: return <FileTextOutlined />;
  }
}

/** 文件类型图标(带分类色底) */
export function FileIcon({ fileName, ext, size = 36 }: { fileName?: string; ext?: string; size?: number }) {
  if (fileName === undefined && ext === undefined) {
    return <FolderFilled style={{ fontSize: size, color: '#3E63DD' }} />;
  }
  const category = categoryOf(ext);
  const color = CATEGORY_COLOR[category];
  const icon = category === 'DOCUMENT' && ext ? docIcon(ext) : CATEGORY_ICON[category];
  return (
    <span
      style={{
        width: size,
        height: size,
        borderRadius: 8,
        background: `${color}18`,
        color,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: size * 0.55,
        flexShrink: 0,
      }}
    >
      {icon}
    </span>
  );
}

export { CATEGORY_COLOR };