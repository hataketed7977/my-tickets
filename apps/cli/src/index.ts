#!/usr/bin/env node
import { Command, Option } from 'commander';
import { list } from './commands/list.js';
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

program
  .command('list')
  .description('查询工单列表')
  .option('--search <关键词>', '搜索标题或工单编号')
  .addOption(
    new Option('--status <状态>', '状态：待处理|处理中|已解决|已关闭').choices([
      '待处理',
      '处理中',
      '已解决',
      '已关闭',
    ]),
  )
  .addOption(
    new Option('--priority <优先级>', '优先级：低|普通|高|紧急').choices([
      '低',
      '普通',
      '高',
      '紧急',
    ]),
  )
  .option('--category <分类名>', '问题分类名称（精确匹配）')
  .option('--page <n>', '页码（默认 1）', '1')
  .option('--page-size <n>', '每页条数（默认 100）', '100')
  .option('--json', '输出 JSON 格式')
  .action(async (opts) => {
    const globalOpts = program.opts<{ apiBaseUrl?: string }>();
    try {
      await list({
        apiBaseUrl: globalOpts.apiBaseUrl,
        search: opts.search,
        status: opts.status,
        priority: opts.priority,
        category: opts.category,
        page: Number.parseInt(opts.page, 10),
        pageSize: Number.parseInt(opts.pageSize, 10),
        json: opts.json,
      });
    } catch (error) {
      const message =
        error instanceof Error ? error.message : '查询失败';
      console.error(`错误：${message}`);
      process.exitCode = 1;
    }
  });

program.parse();
