import open from 'open';
import { apiRequest } from './api.js';

export interface CliSession {
  sessionId: string;
  authorizeUrl: string;
}

export interface CliSessionReady {
  status: 'ready';
  token: string;
  user: { id: string; name: string };
}

export interface CliSessionPending {
  status: 'pending';
}

export type CliSessionStatus = CliSessionReady | CliSessionPending;

const POLL_INTERVAL_MS = 1500;
const POLL_TIMEOUT_MS = 120_000;

export async function createSession(baseUrl: string): Promise<CliSession> {
  return apiRequest<CliSession>(baseUrl, '/api/auth/cli/session', {
    method: 'POST',
  });
}

export async function pollSession(
  baseUrl: string,
  sessionId: string,
): Promise<CliSessionReady> {
  const deadline = Date.now() + POLL_TIMEOUT_MS;
  while (Date.now() < deadline) {
    const result = await apiRequest<CliSessionStatus>(
      baseUrl,
      `/api/auth/cli/session/${encodeURIComponent(sessionId)}`,
    );
    if (result.status === 'ready') {
      return result;
    }
    await sleep(POLL_INTERVAL_MS);
  }
  throw new Error('登录超时，请重试');
}

export async function openBrowser(url: string): Promise<void> {
  await open(url);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
