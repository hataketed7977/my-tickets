#!/usr/bin/env node
import { Command } from 'commander';
import { login } from './commands/login.js';
import { logout } from './commands/logout.js';
import { status } from './commands/status.js';

const program = new Command();

program
  .name('my-tickets')
  .description('工单中心 CLI 工具')
  .version('1.0.0')
  .option(
    '--api-base-url <url>',
    '后端 API 地址（默认 http://localhost:55888）',
  );

program
  .command('login')
  .description('通过飞书 OAuth 登录')
  .action(async () => {
    const opts = program.opts<{ apiBaseUrl?: string }>();
    try {
      await login({ apiBaseUrl: opts.apiBaseUrl });
    } catch (error) {
      const message =
        error instanceof Error ? error.message : '登录失败';
      console.error(`错误：${message}`);
      process.exitCode = 1;
    }
  });

program
  .command('status')
  .description('查看当前登录状态')
  .action(async () => {
    const opts = program.opts<{ apiBaseUrl?: string }>();
    await status(opts.apiBaseUrl);
  });

program
  .command('logout')
  .description('退出登录')
  .action(async () => {
    await logout();
  });

program.parse();
