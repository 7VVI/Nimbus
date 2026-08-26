/** 后端接口类型定义(与 nimbus-server JSON 结构对应, Long 均序列化为字符串) */

export interface PageResult<T> {
  total: number;
  records: T[];
}

export interface Result<T> {
  code: number;
  msg: string;
  data: T;
}

export interface LoginVO {
  userId: string;
  token: string;
}

export interface LoginUserVO {
  userId: string;
  username: string;
  nickname: string;
  roleKeys?: string[];
  permissions?: string[];
}

export interface QuotaVO {
  totalSize: number;
  usedSize: number;
  remainSize: number;
}

/** 文件(1 正常 2 回收站 3 已删除) */
export interface NimbusFile {
  id: string;
  userId: string;
  folderId: string;
  fileName: string;
  fileExt?: string;
  fileSize: string;
  fileHash: string;
  mimeType?: string;
  status: number;
  isStarred: number;
  version: number;
  createTime: string;
  updateTime: string;
  deleteTime?: string;
}

/** 文件夹 */
export interface NimbusFolder {
  id: string;
  userId: string;
  parentId: string;
  folderName: string;
  folderPath: string;
  depth: number;
  status: number;
  createTime: string;
  updateTime: string;
  deleteTime?: string;
}

/** 目录树节点 */
export interface FolderTreeVO {
  id: string;
  parentId: string;
  folderName: string;
  children: FolderTreeVO[];
}

/** 面包屑节点 */
export interface BreadcrumbVO {
  id: string;
  name: string;
}

/** 文件夹内容 */
export interface FolderContentVO {
  folders: NimbusFolder[];
  files: PageResult<NimbusFile>;
}

/** 上传初始化 */
export interface UploadInitVO {
  instant: boolean;
  file?: NimbusFile;
  uploadId?: string;
  chunkCount?: number;
  chunkSize?: number;
  existChunks: number[];
}

export interface PreviewVO {
  category: string;
  fileName: string;
  mimeType?: string;
  fileSize: number;
  url?: string;
  message?: string;
}

export interface NimbusFileVersion {
  id: string;
  fileId: string;
  versionNo: number;
  fileSize: string;
  fileHash: string;
  operatorId: string;
  remark?: string;
  createTime: string;
}

export interface NimbusShare {
  id: string;
  userId: string;
  shortCode: string;
  shareType: number;
  password?: string;
  permission: number;
  expireType: number;
  expireTime?: string;
  viewCount: number;
  saveCount: number;
  status: number;
  createTime: string;
}

export interface ShareItemVO {
  targetType: number;
  id: string;
  name: string;
  fileExt?: string;
  fileSize?: string;
  updateTime?: string;
}

export interface ShareAccessVO {
  share: NimbusShare;
  items: ShareItemVO[];
}

/** 回收站条目(1 文件 2 文件夹) */
export interface RecycleItemVO {
  targetType: number;
  id: string;
  name: string;
  fileExt?: string;
  fileSize?: string;
  deleteTime?: string;
}