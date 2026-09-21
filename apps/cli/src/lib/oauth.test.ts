import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createSession, pollSession } from './oauth.js';

describe('oauth', () => {
  const baseUrl = 'http://localhost:55888';

  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('createSession posts to /api/auth/cli/session', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve({
          sessionId: 'state-123',
          authorizeUrl: 'https://feishu.example/auth',
        }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const session = await createSession(baseUrl);
    expect(session.sessionId).toBe('state-123');
    expect(session.authorizeUrl).toBe('https://feishu.example/auth');
    expect(fetchMock).toHaveBeenCalledWith(
      `${baseUrl}/api/auth/cli/session`,
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('pollSession returns ready result', async () => {
    let callCount = 0;
    const fetchMock = vi.fn().mockImplementation(() => {
      callCount++;
      if (callCount < 3) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ status: 'pending' }),
        });
      }
      return Promise.resolve({
        ok: true,
        json: () =>
          Promise.resolve({
            status: 'ready',
            token: 'issued-token',
            user: { id: 'u1', name: 'Test' },
          }),
      });
    });
    vi.stubGlobal('fetch', fetchMock);

    const pollPromise = pollSession(baseUrl, 'state-123');

    await vi.advanceTimersByTimeAsync(4500);

    const result = await pollPromise;
    expect(result.status).toBe('ready');
    expect(result.token).toBe('issued-token');
    expect(callCount).toBe(3);
  });

  it('pollSession throws on timeout', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ status: 'pending' }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const pollPromise = pollSession(baseUrl, 'state-123');
    pollPromise.catch(() => undefined);

    await vi.advanceTimersByTimeAsync(120_000);

    await expect(pollPromise).rejects.toThrow('登录超时');
  });
});
