---
name: my-tickets-cli
description: 工单中心（Ticket Center）命令行工具。用于从本地安装包安装 `my-tickets` CLI，并完成飞书 OAuth 登录、查看登录状态、查询工单列表。当用户需要通过命令行查询工单、检查登录态或批量导出工单数据时使用。
---

# my-tickets CLI 使用指南

`my-tickets` 是工单中心的命令行工具，包名为 `@ticket-center/my-tickets-cli`，可执行命令为 `my-tickets`。本文档指导 Agent 从用户上传的 `.tgz` 安装包完成安装，并掌握登录与查询命令的使用。

> **真实性声明**：本文档中的命令、参数、退出码、默认值均直接提取自 CLI 源码（`apps/cli/src/index.ts`、`commands/*.ts`、`lib/*.ts`）与 `package.json`，不得自行编造。

## 前置条件

| 项 | 要求 | 验证方式 |
| --- | --- | --- |
| Node.js | `>= 22.5.0` | `node --version` |
| npm | `>= 10.0.0` | `npm --version` |
| 安装包 | 用户上传的 `ticket-center-my-tickets-cli-1.0.0.tgz` | 文件存在且可读取 |
| 后端服务 | 工单中心后端 API 可达（默认 `http://localhost:55888`） | `curl <apiBaseUrl>/api/health` 或浏览器可访问 Web 端 |

## 一、安装

安装包通过 `npm pack` 生成，文件名为 `ticket-center-my-tickets-cli-1.0.0.tgz`（包名 `@ticket-center/my-tickets-cli` 的 scope 在打包时转换为文件名前缀 `ticket-center-`）。

### 标准安装：全局安装 tgz 包

```bash
npm install -g ./ticket-center-my-tickets-cli-1.0.0.tgz
```

安装完成后，可执行命令为 `my-tickets`（定义在 `package.json` 的 `bin` 字段：`"my-tickets": "dist/index.js"`）。

### 验证安装

```bash
my-tickets --version
```

**可观察标志**：输出 `1.0.0` 即表示安装成功。

### 兜底方案：npx

若全局安装因权限或环境问题失败（如 `EACCES`、`PATH` 未生效），使用 `npx` 直接执行 tgz 包：

```bash
npx ./ticket-center-my-tickets-cli-1.0.0.tgz --version
```

**注意**：`npx` 方式每次执行都会重新加载包，启动较慢，仅作临时兜底，不推荐长期使用。若需长期使用，请排查 `npm prefix -g` 的 bin 目录是否在 `PATH` 中。

## 二、API 地址配置

CLI 通过三层优先级解析后端 API 地址：

| 优先级 | 来源 | 示例 |
| --- | --- | --- |
| 1（最高） | 全局参数 `--api-base-url <url>` | `my-tickets --api-base-url http://10.0.0.1:55888 status` |
| 2 | 环境变量 `MY_TICKETS_API_URL` | `export MY_TICKETS_API_URL=http://10.0.0.1:55888` |
| 3 | 本地配置文件 `~/.my-tickets/config.json` 中的 `apiBaseUrl` | 首次登录后自动写入 |
| 4（默认） | 硬编码默认值 | `http://localhost:55888` |

> 所有命令均支持在最前面加 `--api-base-url` 全局参数覆盖地址。若后端不在本机，必须显式指定。

## 三、登录流程

`my-tickets` 使用**飞书 OAuth** 登录，流程为「后端回调 + CLI 轮询」，**必须在浏览器中完成飞书授权，Agent 无法自动化此步骤**。

### 登录命令

```bash
my-tickets login
# 指定非默认后端地址：
my-tickets --api-base-url http://<host>:55888 login
```

### 流程说明

1. CLI 调用 `POST /api/auth/cli/session` 创建登录会话，获取 `sessionId` 和 `authorizeUrl`。
2. CLI 自动打开浏览器访问 `authorizeUrl`（飞书授权页）。若浏览器未自动打开，终端会打印该 URL，需人工复制访问。
3. 用户在浏览器中完成飞书登录与授权。
4. CLI 每 1.5 秒轮询 `GET /api/auth/cli/session/{sessionId}`，最多等待 120 秒。
5. 授权完成后，后端返回 `token` 与用户信息，CLI 将其写入 `~/.my-tickets/config.json`。

### 登录成功标志

终端输出 `登录成功，欢迎 <用户名>！`，且 `~/.my-tickets/config.json` 中包含 `token` 和 `user` 字段。

### 超时处理

若 120 秒内未完成授权，终端输出 `登录超时，请重试`，需重新执行 `login`。

> **Agent 规则**：执行 `login` 后，必须将终端打印的 `authorizeUrl` 告知用户，请用户在浏览器中完成授权，再继续后续操作。切勿假设登录会自动完成。

## 四、命令速查（场景 → 命令）

| 场景 | 命令 | 说明 |
| --- | --- | --- |
| 首次登录或重新登录 | `my-tickets login` | 打开浏览器走飞书 OAuth，需用户介入 |
| 检查当前是否已登录 | `my-tickets status` | 输出 `已登录：<用户名>` 或 `未登录` / `登录已过期` |
| 退出登录（清除本地凭证） | `my-tickets logout` | 删除 token，保留 `apiBaseUrl` 配置 |
| 查看所有工单（默认分页） | `my-tickets list` | 默认第 1 页、每页 100 条，表格输出 |
| 按关键词搜索标题/编号 | `my-tickets list --search "登录失败"` | 模糊匹配工单标题或工单编号 |
| 按状态筛选 | `my-tickets list --status 处理中` | 可选：`待处理` / `处理中` / `已解决` / `已关闭` |
| 按优先级筛选 | `my-tickets list --priority 紧急` | 可选：`低` / `普通` / `高` / `紧急` |
| 按问题分类筛选 | `my-tickets list --category "账号问题"` | 分类名称**精确匹配**，不存在则报错 |
| 组合多条件筛选 | `my-tickets list --status 待处理 --priority 高 --category "账号问题"` | 多个条件取交集 |
| 翻页查询 | `my-tickets list --page 2 --page-size 50` | `--page` 默认 1，`--page-size` 默认 100 |
| 获取原始 JSON 数据（供程序处理） | `my-tickets list --json` | 输出后端原始响应，用户 ID 不转换为姓名 |
| 指定非默认后端地址 | `my-tickets --api-base-url http://<host>:55888 list` | 全局参数，适用于所有命令 |

## 五、命令详细说明

### 1. `login` — 飞书 OAuth 登录

```bash
my-tickets [--api-base-url <url>] login
```

- 打开浏览器进行飞书授权，需用户手动完成。
- 成功后将 `token`、`user`、`apiBaseUrl` 写入 `~/.my-tickets/config.json`。
- 若本地已有 token，仍会重新创建会话并覆盖旧凭证。

### 2. `status` — 查看登录状态

```bash
my-tickets [--api-base-url <url>] status
```

| 情况 | 输出 | 退出码 |
| --- | --- | --- |
| 已登录且有效 | `已登录：<用户名>` | 0 |
| 未登录（无 token） | `未登录` | 1 |
| token 过期（401） | `登录已过期，请重新登录` | 1 |
| 请求失败 | `错误：<错误信息>` | 1 |

**Agent 规则**：在执行任何需要鉴权的命令前，**必须先运行 `status` 确认登录态**。退出码为 0 才继续，否则引导用户登录。

### 3. `logout` — 退出登录

```bash
my-tickets logout
```

- 清除 `~/.my-tickets/config.json` 中的 `token` 与 `user`，**保留 `apiBaseUrl`**。
- 始终输出 `已退出登录`，退出码 0。

### 4. `list` — 查询工单列表

```bash
my-tickets [--api-base-url <url>] list [选项]
```

#### 选项

| 选项 | 类型 | 可选值 / 默认值 | 说明 |
| --- | --- | --- | --- |
| `--search <关键词>` | string | 无 | 模糊匹配工单标题或工单编号 |
| `--status <状态>` | enum | `待处理` / `处理中` / `已解决` / `已关闭` | 按状态筛选 |
| `--priority <优先级>` | enum | `低` / `普通` / `高` / `紧急` | 按优先级筛选 |
| `--category <分类名>` | string | 无 | 按问题分类名称筛选，**精确匹配**，分类不存在时报错 |
| `--page <n>` | int | `1` | 页码，从 1 开始 |
| `--page-size <n>` | int | `100` | 每页条数 |
| `--json` | flag | false | 输出原始 JSON，不转换用户 ID 为姓名 |

#### 输出格式

- **默认（表格）**：通过 `console.table` 输出，列包括 `ticketNo`、`title`、`status`、`priority`、`categoryName`、`reporter`、`assignee`、`createdAt`、`updatedAt`、`resolvedAt`。其中 `reporter` 和 `assignee` 会自动调用 `/api/users` 将用户 ID 转换为姓名。
- **`--json`**：直接输出后端原始响应 `{ items, total, page, pageSize }`，`items` 中保留用户 ID 字段（`reporterUserId`、`assigneeUserId`），适合程序解析。

#### 异常处理

| 情况 | 输出 | 退出码 |
| --- | --- | --- |
| 未登录 | `未登录，请先运行 my-tickets login` | 1 |
| token 过期（401） | `登录已过期，请重新登录` | 1 |
| 分类不存在 | `错误：未找到分类：<分类名>` | 1 |
| 其他请求失败 | `错误：<后端返回的错误信息>` | 1 |

> **Agent 规则**：当 `--category` 指定的分类不存在时，应先执行 `my-tickets list --json`（或不带 `--category` 的查询）以获取现有分类列表，再提示用户选择正确的分类名称，而不是直接报错终止。

## 六、本地配置文件

| 项 | 值 |
| --- | --- |
| 路径 | `~/.my-tickets/config.json` |
| 内容 | `{ "apiBaseUrl": "...", "token": "...", "user": { "id": "...", "name": "..." } }` |
| `logout` 行为 | 清除 `token` 和 `user`，保留 `apiBaseUrl` |

## 七、退出码速查

| 退出码 | 含义 |
| --- | --- |
| `0` | 执行成功 |
| `1` | 未登录 / token 过期 / 请求失败 / 参数错误等通用失败 |

> CLI 使用 `process.exitCode` 设置退出码，不调用 `process.exit()`，因此可通过 `$?`（Bash）或 `$LASTEXITCODE`（PowerShell）读取。

## 八、Agent 执行规则

1. **先装后验**：安装后必须执行 `my-tickets --version` 确认输出版本号，再进行后续操作。若失败，切换 `npx` 兜底。
2. **先态后用**：执行 `list` 等鉴权命令前，必须先 `status` 检查登录态；退出码非 0 时引导用户执行 `login`。
3. **登录需用户介入**：`login` 必须由用户在浏览器完成飞书授权。Agent 执行 `login` 后，将终端输出的 `authorizeUrl` 告知用户，等待用户确认授权完成后再继续。
4. **取数用 `--json`**：当 Agent 需要解析工单数据（而非直接展示给用户）时，始终加 `--json` 获取原始结构化数据。
5. **分类先查后用**：使用 `--category` 前，先通过 `list --json`（无 `--category`）或已知分类列表确认分类名称存在，避免精确匹配失败。
6. **地址非默认时显式指定**：若后端不在 `http://localhost:55888`，每次命令都需带 `--api-base-url`，或设置 `MY_TICKETS_API_URL` 环境变量。
