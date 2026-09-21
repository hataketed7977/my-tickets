import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { rmSync } from 'node:fs';

const tmpDir = '/tmp/my-tickets-cli-list-test';

vi.mock('node:os', () => ({
  homedir: () => tmpDir,
}));

const listModule = await import('./list.js');
const { list, buildTicketPath, mapStatus, mapPriority } = listModule;
const api = await import('../lib/api.js');
const config = await import('../lib/config.js');

describe('mapStatus', () => {
  it('maps 待处理 to open', () => {
    expect(mapStatus('待处理')).toBe('open');
  });

  it('maps 处理中 to in_progress', () => {
    expect(mapStatus('处理中')).toBe('in_progress');
  });

  it('maps 已解决 to resolved', () => {
    expect(mapStatus('已解决')).toBe('resolved');
  });

  it('maps 已关闭 to closed', () => {
    expect(mapStatus('已关闭')).toBe('closed');
  });
});

describe('mapPriority', () => {
  it('maps 低 to low', () => {
    expect(mapPriority('低')).toBe('low');
  });

  it('maps 普通 to medium', () => {
    expect(mapPriority('普通')).toBe('medium');
  });

  it('maps 高 to high', () => {
    expect(mapPriority('高')).toBe('high');
  });

  it('maps 紧急 to urgent', () => {
    expect(mapPriority('紧急')).toBe('urgent');
  });
});

describe('buildTicketPath', () => {
  it('uses default page=1 and pageSize=100', () => {
    expect(buildTicketPath({})).toBe('/api/tickets?page=1&pageSize=100');
  });

  it('includes search, status, priority, and categoryId', () => {
    const path = buildTicketPath(
      {
        search: '登录',
        status: '待处理',
        priority: '紧急',
      },
      'cat-123',
    );
    expect(path).toContain('search=%E7%99%BB%E5%BD%95');
    expect(path).toContain('status=open');
    expect(path).toContain('priority=urgent');
    expect(path).toContain('categoryId=cat-123');
  });

  it('respects explicit page and pageSize', () => {
    const path = buildTicketPath({ page: 3, pageSize: 50 });
    expect(path).toContain('page=3');
    expect(path).toContain('pageSize=50');
  });
});

describe('list command', () => {
  let exitCode: number | undefined;
  let consoleLogSpy: ReturnType<typeof vi.spyOn>;
  let consoleTableSpy: ReturnType<typeof vi.spyOn>;
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.restoreAllMocks();
    exitCode = process.exitCode;
    process.exitCode = undefined;
    consoleLogSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    consoleTableSpy = vi
      .spyOn(console, 'table')
      .mockImplementation(() => {});
    consoleErrorSpy = vi
      .spyOn(console, 'error')
      .mockImplementation(() => {});
  });

  afterEach(() => {
    process.exitCode = exitCode;
    rmSync(tmpDir, { recursive: true, force: true });
  });

  it('prints login hint and exits 1 when no token', async () => {
    config.saveConfig({});
    await list({});
    expect(process.exitCode).toBe(1);
    expect(consoleLogSpy).toHaveBeenCalledWith(
      '未登录，请先运行 my-tickets login',
    );
  });

  it('resolves category id and requests tickets', async () => {
    config.saveConfig({ token: 'valid-token' });

    vi.spyOn(api, 'apiRequest')
      .mockResolvedValueOnce([{ id: 'cat-1', name: '网络问题' }])
      .mockResolvedValueOnce({ items: [], total: 0, page: 1, pageSize: 100 })
      .mockResolvedValueOnce([]);

    await list({ category: '网络问题' });

    expect(process.exitCode).toBeUndefined();
  });

  it('throws category not found error when name does not match', async () => {
    config.saveConfig({ token: 'valid-token' });

    vi.spyOn(api, 'apiRequest')
      .mockResolvedValueOnce([{ id: 'cat-1', name: '网络问题' }]);

    await list({ category: '不存在' });

    expect(process.exitCode).toBe(1);
    expect(consoleErrorSpy).toHaveBeenCalledWith(
      expect.stringContaining('未找到分类：不存在'),
    );
  });

  it('resolves reporter and assignee names in table output', async () => {
    config.saveConfig({ token: 'valid-token' });

    vi.spyOn(api, 'apiRequest')
      .mockResolvedValueOnce({
        items: [
          {
            id: 't1',
            ticketNo: 'TK-001',
            title: '测试工单',
            description: '描述',
            status: 'open',
            priority: 'high',
            categoryId: 'cat-1',
            categoryName: '网络问题',
            reporterUserId: 'u1',
            assigneeUserId: 'u2',
            createdAt: '2026-01-01T00:00:00Z',
            updatedAt: '2026-01-02T00:00:00Z',
            resolvedAt: null,
          },
        ],
        total: 1,
        page: 1,
        pageSize: 100,
      })
      .mockResolvedValueOnce([
        { id: 'u1', name: '张三' },
        { id: 'u2', name: '李四' },
      ]);

    await list({});

    expect(consoleTableSpy).toHaveBeenCalledWith([
      expect.objectContaining({
        ticketNo: 'TK-001',
        reporter: '张三',
        assignee: '李四',
        categoryName: '网络问题',
        resolvedAt: '',
      }),
    ]);
  });

  it('falls back to raw user id when user not found', async () => {
    config.saveConfig({ token: 'valid-token' });

    vi.spyOn(api, 'apiRequest')
      .mockResolvedValueOnce({
        items: [
          {
            id: 't1',
            ticketNo: 'TK-001',
            title: '测试工单',
            description: '描述',
            status: 'open',
            priority: 'high',
            categoryId: 'cat-1',
            categoryName: '网络问题',
            reporterUserId: 'unknown-id',
            assigneeUserId: null,
            createdAt: '2026-01-01T00:00:00Z',
            updatedAt: '2026-01-02T00:00:00Z',
            resolvedAt: null,
          },
        ],
        total: 1,
        page: 1,
        pageSize: 100,
      })
      .mockResolvedValueOnce([]);

    await list({});

    expect(consoleTableSpy).toHaveBeenCalledWith([
      expect.objectContaining({
        reporter: 'unknown-id',
        assignee: '',
      }),
    ]);
  });

  it('prints raw JSON with --json and skips user lookup', async () => {
    config.saveConfig({ token: 'valid-token' });

    const response = {
      items: [
        {
          id: 't1',
          ticketNo: 'TK-001',
          title: '测试',
          description: '描述',
          status: 'open',
          priority: 'high',
          categoryId: 'cat-1',
          categoryName: '网络问题',
          reporterUserId: 'u1',
          assigneeUserId: null,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-02T00:00:00Z',
          resolvedAt: null,
        },
      ],
      total: 1,
      page: 1,
      pageSize: 100,
    };

    const apiRequestSpy = vi
      .spyOn(api, 'apiRequest')
      .mockResolvedValueOnce(response);

    await list({ json: true });

    expect(consoleLogSpy).toHaveBeenCalledWith(
      JSON.stringify(response, null, 2),
    );
    expect(apiRequestSpy).toHaveBeenCalledTimes(1);
    expect(consoleTableSpy).not.toHaveBeenCalled();
  });

  it('prints expired message and exits 1 on 401', async () => {
    config.saveConfig({ token: 'expired-token' });

    vi.spyOn(api, 'apiRequest').mockRejectedValue(
      new api.ApiError('请先登录', 401),
    );

    await list({});

    expect(process.exitCode).toBe(1);
    expect(consoleLogSpy).toHaveBeenCalledWith('登录已过期，请重新登录');
  });

  it('prints backend error message and exits 1 on 500', async () => {
    config.saveConfig({ token: 'valid-token' });

    vi.spyOn(api, 'apiRequest').mockRejectedValue(
      new api.ApiError('服务器错误', 500),
    );

    await list({});

    expect(process.exitCode).toBe(1);
    expect(consoleErrorSpy).toHaveBeenCalledWith(
      expect.stringContaining('服务器错误'),
    );
  });
});
