import { clearCredentials } from '../lib/config.js';

export async function logout(): Promise<void> {
  clearCredentials();
  console.log('已退出登录');
}
