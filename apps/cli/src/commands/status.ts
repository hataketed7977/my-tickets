import { apiRequest } from '../lib/api.js';
import { loadConfig, resolveApiBaseUrl } from '../lib/config.js';

interface AppUser {
  id: string;
  name: string;
}

export async function status(apiBaseUrl?: string): Promise<void> {
  const config = loadConfig();
  if (!config.token) {
    console.log('未登录');
    process.exitCode = 1;
    return;
  }

  const baseUrl = resolveApiBaseUrl(
    apiBaseUrl,
    process.env.MY_TICKETS_API_URL,
    config.apiBaseUrl,
  );

  try {
    const user = await apiRequest<AppUser>(baseUrl, '/api/auth/me', {
      token: config.token,
    });
    console.log(`已登录：${user.name}`);
  } catch (error) {
    if (
      error instanceof Error &&
      error.name === 'ApiError' &&
      (error as { status?: number }).status === 401
    ) {
      console.log('登录已过期，请重新登录');
      process.exitCode = 1;
      return;
    }
    const message =
      error instanceof Error ? error.message : '状态检查失败';
    console.error(`错误：${message}`);
    process.exitCode = 1;
  }
}
