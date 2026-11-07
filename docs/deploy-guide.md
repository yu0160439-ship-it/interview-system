# 上线部署指南（Render + Aiven MySQL + Cloudflare Pages）

本项目上线拓扑：

```
浏览器
  └─ Cloudflare Pages（静态前端 dist + Functions 反代 /api/*）
        └─ Render Docker Web Service（Spring Boot 后端 jar）
              └─ Aiven 免费 MySQL（外部数据库）
```

- 前端：Vue3 构建产物 `frontend/dist`，部署到 Cloudflare Pages；`frontend/functions` 把 `/api/*` 同源反代到后端，前端保持相对路径、无需跨域。
- 后端：Spring Boot 以 Docker 容器运行在 Render（Render 原生运行时不支持 Java，必须走 Docker）。
- 数据库：Aiven 免费 MySQL 实例；业务表由后端启动时自动创建（`IF NOT EXISTS`），无需手动导入 DDL。

> 仓库地址：https://github.com/yu0160439-ship-it/interview-system
> 所有敏感值只在各平台控制台以环境变量填写，不写入仓库。

---

## 前置确认

- 后端 Docker 构建在 Render 云端完成，**不需要**本地上传 jar；本地 `backend/target` 仅用于自测。
- 前端产物由本地 `npm run build` 生成到 `frontend/dist`，随 wrangler 直传 Pages。

---

## 第一步：创建外部 MySQL（Aiven 免费）

1. 打开 [aiven.io](https://aiven.io) 注册/登录，免费套餐无需信用卡。
2. **Create service** → 选 **MySQL** → 选 **Free** 套餐；区域尽量与后端靠近（Render 常用 Frankfurt / Oregon）。
3. 等待服务状态变为 Running，进入 **Overview / Connection information**，记录：
   - Host（主机）
   - Port（端口）
   - Database（库名，默认 `defaultdb`）
   - User（用户名）
   - Password（密码）
4. 按下述模板拼接 JDBC 连接串（Aiven 强制 SSL）：

```
jdbc:mysql://<HOST>:<PORT>/<DATABASE>?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=true&requireSSL=true&verifyServerCertificate=false
```

5. 建表无需手动操作：后端首次启动会自动创建 `candidates`、`interviewer_scores`、`staff` 三张表并写入工作人员种子账号。

> 免费限制：单节点、1GB 存储、最大 76 连接；长时间无活动可能被关机，可在控制台手动重启。

---

## 第二步：Render 部署后端（Docker）

1. 打开 [render.com](https://render.com) 注册/登录，并授权连接 GitHub。
2. 新建服务，二选一：
   - **Blueprint（推荐）**：New → Blueprint → 选择仓库 `interview-system`，Render 自动读取根目录 `render.yaml`。
   - **手动 Docker 服务**：New → Web Service → 选仓库；Runtime 选 **Docker**；Dockerfile path 填 `backend/Dockerfile`，Docker context 填 `backend`。
3. 进入 **Environment**，按下表添加环境变量（`sync: false` 的项在控制台填写）：

| Key | Value 说明 |
| --- | --- |
| `DB_URL` | 第一步拼好的 Aiven JDBC 连接串 |
| `DB_USERNAME` | Aiven 用户名 |
| `DB_PASSWORD` | Aiven 密码 |
| `JWT_SECRET` | 随机字符串，长度至少 32 字符（上线务必覆盖默认占位密钥） |
| `ADMIN_PASSWORD` | 自定义管理员 `admin` 登录口令 |
| `DELETE_ALL_PASSWORD` | 清空全部考生的二次确认口令 |
| `CORS_ORIGIN` | 同源反代阶段可先填 `*`；绑自定义域名后收敛为前端域名 |

4. 保存并触发部署，等待 Docker 构建镜像、启动容器。健康检查路径已在蓝图配置为 `/api/health`。
5. 部署完成后访问：

```
https://<你的服务名>.onrender.com/api/health
```

返回 `{"status":"ok"}` 即后端就绪。**记录该后端域名**（不带尾斜杠），第三步要用。

> 免费层限制：实例约 15 分钟无流量会休眠，冷启动约 30–60 秒；内存 512MB，JVM 堆已在 Dockerfile 限制为 `-Xmx256m`。

---

## 第三步：Cloudflare Pages 部署前端

### 3.1 本地构建

```bash
cd frontend
npm install
npm run build      # 产物输出到 frontend/dist
```

### 3.2 登录 Cloudflare

```bash
npx wrangler login
```

浏览器会弹出 Cloudflare 授权页，完成授权后回到终端。

### 3.3 创建 Pages 项目（仅首次）

```bash
npx wrangler pages project create interview-system --production-branch main
```

> 项目名只能用小写字母、数字、连字符；若提示已存在可跳过本步。

### 3.4 部署静态产物与 Functions

在 `frontend` 目录执行（`functions/` 与 `dist/` 同级，会被一并上传）：

```bash
npx wrangler pages deploy dist --project-name interview-system
```

### 3.5 配置后端地址 `BACKEND_URL`

把第二步拿到的 Render 域名配给 Pages（Functions 反代目标）：

- 控制台：Pages → 项目 → Settings → Environment variables → 新增 `BACKEND_URL`，值为 `https://<你的服务名>.onrender.com`（不带尾斜杠），生产环境生效。
- 或 CLI：

```bash
npx wrangler pages secret put BACKEND_URL --project-name interview-system
```

保存后重新部署一次使变量生效。

### 3.6 访问验证

打开 Pages 分配的域名（形如 `https://interview-system.pages.dev`），依次走查：报名页、工作人员登录、分流台队列、大屏实时刷新、结果管理。

---

## 接线与实时通信说明

- 前端所有接口与 SSE 均为相对路径 `/api/...`，由 Pages Functions（`frontend/functions/api/[[path]].js`）同源转发到 Render 后端，浏览器侧不触发跨域。
- SSE 端点：`/api/candidates/display/stream`，Functions 流式透传、不缓冲；长连接若被边缘层限时断开，前端 EventSource 自动重连，并有定时轮询兜底。
- 后续绑定自定义域名时：在 Pages 添加域名，再把 Render 的 `CORS_ORIGIN` 改为该前端域名（逗号可填多个来源）。

## 环境变量速查

| 位置 | 变量 | 说明 |
| --- | --- | --- |
| Render | `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 外部 MySQL 连接 |
| Render | `JWT_SECRET` | JWT 签名密钥，≥32 字符 |
| Render | `ADMIN_PASSWORD` | 管理员口令 |
| Render | `DELETE_ALL_PASSWORD` | 清空数据二次确认口令 |
| Render | `CORS_ORIGIN` | 允许的前端来源 |
| Pages | `BACKEND_URL` | Render 后端域名，反代目标 |

## 常见问题

- **后端首次打开很慢**：Render 免费实例休眠后冷启动，等待 30–60 秒再刷新。
- **数据库连不上**：核对 `DB_URL` 端口/库名/账号/密码，SSL 参数是否带齐；Aiven 服务是否处于 Running。
- **页面 404 / 深链接刷新空白**：已通过 `frontend/public/_redirects` 配置 SPA 回退到 `index.html`；若仍异常，检查 Pages 项目是否开启了 SPA 回退。
- **大屏不实时刷新**：先确认后端 `/api/health` 正常；SSE 经反代断线后会自动重连，轮询兜底仍会刷新列表。
- **更换数据库**：后续迁移到付费 MySQL 时，只需在 Render 修改 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 三个变量并重新部署。
