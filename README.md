# 工单中心

前后端分离的工单 Demo：

- `apps/web`：Node.js 22 + React 19 + Vite + shadcn/ui
- `services/api`：Java 21 + Spring Boot 3 + H2
- 飞书 OAuth 2.0 是唯一登录方式

系统提供工单管理和问题分类两个模块，支持工单创建、查询、编辑、删除、筛选和状态管理。

后端采用简单三层结构：

```text
controller -> service -> repository
                 \            \
                  ------> model
```

`config`、`security` 和 `exception` 存放横切基础设施，业务 SQL 仅位于
`repository`。

## 本地运行

环境要求：

- Node.js 22.5+
- Java 21–24（Java 25 已通过当前测试，但超出依赖栈官方支持范围）
- 不需要全局安装 Gradle，仓库包含 Gradle Wrapper

```bash
cp .env.example .env
npm ci --prefix apps/web
./scripts/dev.sh
```

Windows 使用 `.\scripts\dev.ps1` 启动。

启动后：

- Web：`http://localhost:51888`
- API：`http://localhost:55888`

默认数据库是 H2 内存库，Java 服务重启后业务数据会重置。

### Java 版本策略

后端可以直接使用本机 JDK 21–24 构建，不要求额外安装精确的
JDK 21。Gradle 始终通过 `--release 21` 生成 Java 21 字节码，因此
构建机使用更高版本 JDK 时不会改变产物的最低运行版本。

Java 25 在当前测试矩阵中可以通过，但 Spring Boot 3.4 和 Gradle
8.14.3 的官方兼容上限是 Java 24，因此不作为正式支持版本。

## 飞书登录

1. 在飞书开放平台创建企业自建应用。
2. 添加回调地址：
   `http://localhost:55888/api/auth/feishu/callback`
3. 在根目录 `.env` 中配置：

```dotenv
FEISHU_APP_ID=cli_xxx
FEISHU_APP_SECRET=xxx
FEISHU_REDIRECT_URI=http://localhost:55888/api/auth/feishu/callback
```

前端不持有 App Secret。Java API 负责 OAuth 授权码交换、飞书用户同步和 HttpOnly Cookie 会话。系统内所有登录用户采用同一种身份。

## 独立运行

前端：

```bash
npm ci --prefix apps/web
npm --prefix apps/web run dev
```

后端：

```bash
./services/api/gradlew -p services/api bootRun
```

## 验证

```bash
npm run typecheck
npm run lint
npm run build
npm test
```

## 容器运行

```bash
docker compose up --build
```

容器模式同样分别构建 Web 与 API，不会把 Java 服务合并进前端进程。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PORT` | `55888` | Java API 端口 |
| `WEB_PORT` | `51888` | Vite 开发服务器端口 |
| `VITE_API_BASE_URL` | 空 | 浏览器访问的 API 地址；开发代理模式可留空 |
| `WEB_BASE_URL` | `http://localhost:51888` | OAuth 完成后的前端跳转地址 |
| `CLIENT_ORIGIN` | `http://localhost:51888` | Java API 允许携带 Cookie 的前端 Origin |
| `DATABASE_URL` | H2 内存库 | JDBC 数据库地址 |
| `COOKIE_SECURE` | `false` | HTTPS 环境设为 `true` |
| `FEISHU_APP_ID` | 空 | 飞书应用 ID |
| `FEISHU_APP_SECRET` | 空 | 飞书应用密钥，仅供 Java API 使用 |
| `FEISHU_REDIRECT_URI` | 本地 API 回调地址 | 飞书 OAuth 回调地址 |

`.env` 已加入 Git 忽略列表，禁止提交真实应用密钥。
