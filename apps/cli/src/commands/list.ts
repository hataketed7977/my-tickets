import { ApiError, apiRequest } from '../lib/api.js';
import { loadConfig, resolveApiBaseUrl } from '../lib/config.js';

export interface ListOptions {
  apiBaseUrl?: string;
  search?: string;
  status?: string;
  priority?: string;
  category?: string;
  page?: number;
  pageSize?: number;
  json?: boolean;
}

interface IssueCategoryItem {
  id: string;
  name: string;
}

interface AppUser {
  id: string;
  name: string;
}

interface TicketItem {
  id: string;
  ticketNo: string;
  title: string;
  description: string;
  status: string;
  priority: string;
  categoryId: string;
  categoryName: string;
  reporterUserId: string;
  assigneeUserId: string | null;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
}

interface TicketListResponse {
  items: TicketItem[];
  total: number;
  page: number;
  pageSize: number;
}

export const STATUS_MAP: Record<string, string> = {
  '待处理': 'open',
  '处理中': 'in_progress',
  '已解决': 'resolved',
  '已关闭': 'closed',
};

export const PRIORITY_MAP: Record<string, string> = {
  低: 'low',
  普通: 'medium',
  高: 'high',
  紧急: 'urgent',
};

export function mapStatus(chinese: string): string {
  return STATUS_MAP[chinese] ?? chinese;
}

export function mapPriority(chinese: string): string {
  return PRIORITY_MAP[chinese] ?? chinese;
}

async function resolveCategoryId(
  baseUrl: string,
  token: string,
  name: string,
): Promise<string> {
  const categories = await apiRequest<IssueCategoryItem[]>(
    baseUrl,
    '/api/categories',
    { token },
  );
  const match = categories.find((category) => category.name === name);
  if (!match) {
    throw new Error(`未找到分类：${name}`);
  }
  return match.id;
}

export function buildTicketPath(
  options: ListOptions,
  categoryId?: string,
): string {
  const params = new URLSearchParams();
  params.set('page', String(options.page ?? 1));
  params.set('pageSize', String(options.pageSize ?? 100));
  if (options.search) params.set('search', options.search);
  if (options.status) params.set('status', mapStatus(options.status));
  if (options.priority) params.set('priority', mapPriority(options.priority));
  if (categoryId) params.set('categoryId', categoryId);
  return `/api/tickets?${params.toString()}`;
}

export async function list(options: ListOptions): Promise<void> {
  const config = loadConfig();
  if (!config.token) {
    console.log('未登录，请先运行 my-tickets login');
    process.exitCode = 1;
    return;
  }

  const baseUrl = resolveApiBaseUrl(
    options.apiBaseUrl,
    process.env.MY_TICKETS_API_URL,
    config.apiBaseUrl,
  );
  const token = config.token;

  try {
    let categoryId: string | undefined;
    if (options.category) {
      categoryId = await resolveCategoryId(baseUrl, token, options.category);
    }

    const path = buildTicketPath(options, categoryId);
    const response = await apiRequest<TicketListResponse>(baseUrl, path, {
      token,
    });

    if (options.json) {
      console.log(JSON.stringify(response, null, 2));
      return;
    }

    const users = await apiRequest<AppUser[]>(baseUrl, '/api/users', {
      token,
    });
    const userMap = new Map(users.map((user) => [user.id, user.name]));

    const rows = response.items.map((ticket) => ({
      ticketNo: ticket.ticketNo,
      title: ticket.title,
      description: ticket.description,
      status: ticket.status,
      priority: ticket.priority,
      categoryName: ticket.categoryName,
      reporter: userMap.get(ticket.reporterUserId) ?? ticket.reporterUserId,
      assignee: ticket.assigneeUserId
        ? userMap.get(ticket.assigneeUserId) ?? ticket.assigneeUserId
        : '',
      createdAt: ticket.createdAt,
      updatedAt: ticket.updatedAt,
      resolvedAt: ticket.resolvedAt ?? '',
    }));

    console.table(rows);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      console.log('登录已过期，请重新登录');
      process.exitCode = 1;
      return;
    }
    const message = error instanceof Error ? error.message : '查询失败';
    console.error(`错误：${message}`);
    process.exitCode = 1;
  }
}
