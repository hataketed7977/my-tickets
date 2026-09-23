#!/usr/bin/env node
/**
 * 首次安装脚本：为仓库启用提交前自动检查。
 *   node scripts/install-hooks.js
 * 也会在根目录执行 `npm install` 时通过 prepare 脚本自动运行。
 *
 * 做两件事：
 *   1. git config core.hooksPath .githooks
 *   2. POSIX 环境下确保钩子文件有可执行权限
 */
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function git(args) {
  return execFileSync('git', args, {
    cwd: root,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}

try {
  git(['rev-parse', '--is-inside-work-tree']);
} catch {
  // Docker 构建等无 .git 的场景：静默跳过，不中断 npm install
  console.log('[install-hooks] 未检测到 Git 工作区，跳过 hooks 安装。');
  process.exit(0);
}

try {
  git(['config', 'core.hooksPath', '.githooks']);
} catch (err) {
  console.error('[install-hooks] 设置 core.hooksPath 失败：');
  console.error(String(err.stderr || err.message).trim());
  process.exit(1);
}

if (process.platform !== 'win32') {
  for (const name of ['pre-commit', 'commit-msg', 'pre-push']) {
    fs.chmodSync(path.join(root, '.githooks', name), 0o755);
  }
}

console.log('✓ Git hooks 已安装（core.hooksPath=.githooks）');
console.log('  - pre-commit : 仅对本次改动涉及的模块跑测试，未改到的模块不跑');
console.log('  - commit-msg : 校验提交信息符合 Conventional Commits 规范');
console.log('  - pre-push   : 推送含 services/api 改动时执行 JaCoCo 覆盖率门禁');
