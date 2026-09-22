import type { AppUser } from '@/types/api';

import { request } from '@api/request';

export function listUsers(): Promise<AppUser[]> {
  return request<AppUser[]>(
    { url: '/api/users', method: 'GET' },
    '加载用户列表失败',
  );
}
