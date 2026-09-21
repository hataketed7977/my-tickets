import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { rmSync } from 'node:fs';

const tmpDir = '/tmp/my-tickets-cli-status-test';

vi.mock('node:os', () => ({
  homedir: () => tmpDir,
}));

const { status } = await import('./status.js');
const api = await import('../lib/api.js');
const config = await import('../lib/config.js');

describe('status command', () => {
  let exitCode: number | undefined;

  beforeEach(() => {
    vi.restoreAllMocks();
    exitCode = process.exitCode;
    process.exitCode = undefined;
  });

  afterEach(() => {
    process.exitCode = exitCode;
    rmSync(tmpDir, { recursive: true, force: true });
  });

  it('prints 未登录 and exits 1 when no token', async () => {
    config.saveConfig({});
    await status();
    expect(process.exitCode).toBe(1);
  });

  it('prints user name when token is valid', async () => {
    config.saveConfig({ token: 'valid-token' });
    vi.spyOn(api, 'apiRequest').mockResolvedValue({
      id: 'u1',
      name: 'Alice',
    });

    await status();
    expect(process.exitCode).toBeUndefined();
  });

  it('prints 登录已过期 and exits 1 on 401', async () => {
    config.saveConfig({ token: 'expired-token' });
    vi.spyOn(api, 'apiRequest').mockRejectedValue(
      new api.ApiError('请先登录', 401),
    );

    await status();
    expect(process.exitCode).toBe(1);
  });
});
