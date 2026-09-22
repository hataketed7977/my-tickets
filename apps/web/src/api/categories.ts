import type {
  CreateIssueCategoryRequest,
  IssueCategoryItem,
  UpdateIssueCategoryRequest,
} from '@/types/api';

import { request } from '@api/request';

const CACHE_TTL_MS = 30_000;

interface CachedRequest<T> {
  expiresAt: number;
  promise: Promise<T>;
}

let categoriesCache: CachedRequest<IssueCategoryItem[]> | null = null;

function cachedRequest<T>(
  current: CachedRequest<T> | null,
  loader: () => Promise<T>,
  update: (next: CachedRequest<T> | null) => void,
): Promise<T> {
  if (current && current.expiresAt > Date.now()) return current.promise;

  const promise: Promise<T> = loader().catch((error: unknown) => {
    update(null);
    throw error;
  });
  update({ expiresAt: Date.now() + CACHE_TTL_MS, promise });
  return promise;
}

export function listCategories(): Promise<IssueCategoryItem[]> {
  return cachedRequest(
    categoriesCache,
    () =>
      request<IssueCategoryItem[]>(
        { url: '/api/categories', method: 'GET' },
        '加载问题分类失败',
      ),
    (next: CachedRequest<IssueCategoryItem[]> | null) => {
      categoriesCache = next;
    },
  );
}

export function createCategory(
  body: CreateIssueCategoryRequest,
): Promise<IssueCategoryItem> {
  return request<IssueCategoryItem>(
    { url: '/api/categories', method: 'POST', data: body },
    '创建问题分类失败',
  ).then((category: IssueCategoryItem) => {
    categoriesCache = null;
    return category;
  });
}

export function updateCategory(
  id: string,
  body: UpdateIssueCategoryRequest,
): Promise<IssueCategoryItem> {
  return request<IssueCategoryItem>(
    {
      url: `/api/categories/${id}`,
      method: 'PATCH',
      data: body,
    },
    '更新问题分类失败',
  ).then((category: IssueCategoryItem) => {
    categoriesCache = null;
    return category;
  });
}

export function deleteCategory(id: string): Promise<void> {
  return request<void>(
    { url: `/api/categories/${id}`, method: 'DELETE' },
    '删除问题分类失败',
  ).then(() => {
    categoriesCache = null;
  });
}
