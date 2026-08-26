import { api } from './client';
import type {
  FolderContentVO,
  FolderTreeVO,
  BreadcrumbVO,
  PageResult,
  PreviewVO,
  NimbusFile,
  NimbusFileVersion,
  NimbusShare,
  QuotaVO,
  RecycleItemVO,
  ShareAccessVO,
  ShareItemVO,
  UploadInitVO,
  LoginUserVO,
  LoginVO,
} from './types';

/** 认证 */
export const authApi = {
  login: (username: string, password: string) =>
    api.post<LoginVO>('/auth/login', { username, password }),
  register: (data: { username: string; password: string; nickname?: string; email?: string }) =>
    api.post<number>('/auth/register', data),
  me: () => api.get<LoginUserVO>('/auth/me'),
  logout: () => api.post<void>('/auth/logout'),
};

/** 配额 */
export const quotaApi = {
  get: () => api.get<QuotaVO>('/quota'),
  /** 升级扩容: 调整总容量(只增不减) */
  upgrade: (totalSize: number) => api.put<QuotaVO>('/quota/upgrade', { totalSize }),
};

/** 文件夹 */
export const folderApi = {
  create: (parentId: string, folderName: string) =>
    api.post<number>('/netdisk/folder', { parentId, folderName }),
  rename: (id: string, folderName: string) =>
    api.put<void>('/netdisk/folder', { id, folderName }),
  move: (id: string, targetParentId: string) =>
    api.put<void>('/netdisk/folder/move', { id, targetParentId }),
  tree: () => api.get<FolderTreeVO[]>('/netdisk/folder/tree'),
  breadcrumb: (folderId: string) => api.get<BreadcrumbVO[]>(`/netdisk/folder/${folderId}/breadcrumb`),
  content: (folderId: string, pageNum = 1, pageSize = 20, sortKey?: string, order?: string) =>
    api.get<FolderContentVO>(`/netdisk/folder/${folderId}/content`, { pageNum, pageSize, sortKey, order }),
  delete: (id: string) => api.del<void>(`/netdisk/folder/${id}`),
};

/** 文件 */
export const fileApi = {
  page: (params: {
    folderId?: string;
    keyword?: string;
    fileType?: string;
    isStarred?: number;
    sortKey?: string;
    order?: string;
    pageNum?: number;
    pageSize?: number;
  }) => api.get<PageResult<NimbusFile>>('/netdisk/file/page', params),
  recent: (limit = 20) => api.get<NimbusFile[]>('/netdisk/file/recent', { limit }),
  starred: () => api.get<NimbusFile[]>('/netdisk/file/starred'),
  detail: (id: string) => api.get<NimbusFile>(`/netdisk/file/${id}`),
  rename: (id: string, fileName: string) => api.put<void>(`/netdisk/file/${id}`, { fileName }),
  move: (id: string, folderId: string) => api.put<void>(`/netdisk/file/${id}/move`, { folderId }),
  copy: (id: string, folderId: string) => api.post<NimbusFile>(`/netdisk/file/${id}/copy`, { folderId }),
  star: (id: string, starred: boolean) => api.put<void>(`/netdisk/file/${id}/star?starred=${starred}`),
  delete: (id: string) => api.del<void>(`/netdisk/file/${id}`),
  versions: (id: string) => api.get<NimbusFileVersion[]>(`/netdisk/file/${id}/versions`),
  rollback: (id: string, versionId: string) =>
    api.put<NimbusFile>(`/netdisk/file/${id}/rollback/${versionId}`),
  search: (keyword: string, pageNum = 1, pageSize = 20) =>
    api.get<PageResult<NimbusFile>>('/search/file', { keyword, pageNum, pageSize }),
};

/** 上传(分片) */
export const uploadApi = {
  init: (data: { fileName: string; fileSize: number; fileHash: string; folderId: string; fileId?: string }) =>
    api.post<UploadInitVO>('/upload/init', data),
  chunk: (uploadId: string, chunkIndex: number, blob: Blob) => {
    const form = new FormData();
    form.append('uploadId', uploadId);
    form.append('chunkIndex', String(chunkIndex));
    form.append('file', blob);
    return api.post<void>('/upload/chunk', form);
  },
  merge: (uploadId: string) => api.post<NimbusFile>('/upload/merge', { uploadId }),
  cancel: (uploadId: string) => api.del<void>(`/upload/${uploadId}`),
};

/** 分享 */
export const shareApi = {
  create: (data: {
    targetType: number;
    targetIds: string[];
    shareType: number;
    password?: string;
    permission: number;
    expireType: number;
    expireDays?: number;
  }) => api.post<NimbusShare>('/share', data),
  access: (code: string, password?: string) =>
    api.post<ShareAccessVO>('/share/access', { code, password }),
  items: (code: string, folderId?: string, password?: string) =>
    api.get<ShareItemVO[]>(`/share/${code}/items`, { folderId, password }),
  my: (pageNum = 1, pageSize = 10) => api.get<PageResult<NimbusShare>>('/share/my', { pageNum, pageSize }),
  cancel: (id: string) => api.del<void>(`/share/${id}`),
  save: (code: string, password: string | undefined, folderId: string) =>
    api.post<number>('/share/save', { code, password, folderId }),
};

/** 回收站 */
export const recycleApi = {
  page: (pageNum = 1, pageSize = 20) =>
    api.get<PageResult<RecycleItemVO>>('/recycle/page', { pageNum, pageSize }),
  restore: (targetType: number, id: string) =>
    api.put<void>(`/recycle/restore?targetType=${targetType}&id=${id}`),
  purge: (targetType: number, id: string) => api.del<void>(`/recycle/${targetType}/${id}`),
  clean: () => api.del<number>('/recycle/clean'),
};

/** 预览 */
export const previewApi = {
  info: (fileId: string) => api.get<PreviewVO>(`/netdisk/preview/${fileId}`),
  /** 内容流式地址(内联预览, 支持 Range) */
  contentUrl: (fileId: string) => `/api/netdisk/preview/${fileId}/content`,
};