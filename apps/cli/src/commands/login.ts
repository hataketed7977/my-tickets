import {
  createSession,
  openBrowser,
  pollSession,
} from '../lib/oauth.js';
import { loadConfig, resolveApiBaseUrl, saveConfig } from '../lib/config.js';

export interface LoginOptions {
  apiBaseUrl?: string;
}

export async function login(options: LoginOptions): Promise<void> {
  const existing = loadConfig();
  const baseUrl = resolveApiBaseUrl(
    options.apiBaseUrl,
    process.env.MY_TICKETS_API_URL,
    existing.apiBaseUrl,
  );

  console.log('正在创建登录会话...');
  const session = await createSession(baseUrl);

  console.log('正在打开浏览器进行飞书登录...');
  await openBrowser(session.authorizeUrl);
  console.log('如果浏览器未自动打开，请手动访问：');
  console.log(session.authorizeUrl);
  console.log('等待登录完成...');

  const result = await pollSession(baseUrl, session.sessionId);

  saveConfig({
    apiBaseUrl: baseUrl,
    token: result.token,
    user: result.user,
  });

  console.log(`登录成功，欢迎 ${result.user.name}！`);
}
