import axios, { AxiosError, type AxiosRequestConfig } from 'axios';
import type { Result } from './types';

/** Token 存储键 */
export const TOKEN_KEY = 'nimbus_token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

/** 统一响应体解包: code != 200 时抛出带 msg 的错误 */
async function unwrap<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<Result<T>>(config);
  const body = response.data;
  if (body.code !== 200) {
    throw new ApiError(body.code, body.msg);
  }
  return body.data;
}

export class ApiError extends Error {
  code: number;

  constructor(code: number, msg: string) {
    super(msg);
    this.code = code;
  }
}

export const http = axios.create({ timeout: 30000 });

// 请求注入登录态
http.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = token;
  }
  return config;
});

// 登录失效统一跳转
http.interceptors.response.use(undefined, (error: AxiosError<Result<unknown>>) => {
  if (error.response?.status === 401) {
    clearToken();
    if (!location.pathname.startsWith('/login')) {
      location.href = '/login?redirect=' + encodeURIComponent(location.pathname + location.search);
    }
  }
  return Promise.reject(error);
});

/** 后端接口统一带 /api 前缀(与 vite 代理约定一致) */
function applyPrefix(url: string): string {
  return url.startsWith('/api') ? url : `/api${url}`;
}

/** 后端业务方法统一入口 */
export const api = {
  get: <T>(url: string, params?: Record<string, unknown>) =>
    unwrap<T>({ method: 'GET', url: applyPrefix(url), params }),
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    unwrap<T>({ method: 'POST', url: applyPrefix(url), data, ...config }),
  put: <T>(url: string, data?: unknown) => unwrap<T>({ method: 'PUT', url: applyPrefix(url), data }),
  del: <T>(url: string) => unwrap<T>({ method: 'DELETE', url: applyPrefix(url) }),
};

/** 下载类接口: 直接拿二进制流(自动带登录态, 支持自定义方法/请求体, 如批量 zip 的 POST) */
export async function download(url: string, config?: AxiosRequestConfig): Promise<Blob> {
  // 必须走 request 而非 get/post: axios 的 .get()/.post() 会强制覆盖 method
  const response = await http.request<Blob>({
    url,
    method: 'GET',
    responseType: 'blob',
    timeout: 0,
    ...config,
  });
  return response.data;
}

/** 触发浏览器保存 Blob */
export function saveBlob(blob: Blob, fileName: string) {
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(link.href);
}