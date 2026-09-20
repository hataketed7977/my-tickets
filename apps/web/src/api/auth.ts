import type { AppUser, AuthConfig } from '@/types/api';

import { request } from './request';

export function getConfig(): Promise<AuthConfig> {
  return request<AuthConfig>(
    { url: '/api/auth/config', method: 'GET' },
    '加载登录配置失败',
  );
}

export function getCurrentUser(): Promise<AppUser> {
  return request<AppUser>(
    { url: '/api/auth/me', method: 'GET' },
    '获取当前用户失败',
  );
}

export function logout(): Promise<{ ok: true }> {
  return request<{ ok: true }>(
    { url: '/api/auth/logout', method: 'POST' },
    '退出登录失败',
  );
}
