import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { rmSync } from 'node:fs';

const tmpDir = '/tmp/my-tickets-cli-login-test';

vi.mock('node:os', () => ({
  homedir: () => tmpDir,
}));

const { login } = await import('./login.js');
const oauth = await import('../lib/oauth.js');
const config = await import('../lib/config.js');

describe('login command', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    rmSync(tmpDir, { recursive: true, force: true });
  });

  it('writes token and user to config on success', async () => {
    vi.spyOn(oauth, 'createSession').mockResolvedValue({
      sessionId: 'state-1',
      authorizeUrl: 'https://feishu.example/auth',
    });
    vi.spyOn(oauth, 'openBrowser').mockResolvedValue(undefined);
    vi.spyOn(oauth, 'pollSession').mockResolvedValue({
      status: 'ready',
      token: 'token-123',
      user: { id: 'u1', name: 'Alice' },
    });

    await login({});

    const saved = config.loadConfig();
    expect(saved.token).toBe('token-123');
    expect(saved.user).toEqual({ id: 'u1', name: 'Alice' });
    expect(saved.apiBaseUrl).toBe('http://localhost:55888');
  });

  it('does not write config on timeout', async () => {
    vi.spyOn(oauth, 'createSession').mockResolvedValue({
      sessionId: 'state-1',
      authorizeUrl: 'https://feishu.example/auth',
    });
    vi.spyOn(oauth, 'openBrowser').mockResolvedValue(undefined);
    vi.spyOn(oauth, 'pollSession').mockRejectedValue(
      new Error('登录超时，请重试'),
    );

    await expect(login({})).rejects.toThrow('登录超时');

    const saved = config.loadConfig();
    expect(saved.token).toBeUndefined();
  });
});
