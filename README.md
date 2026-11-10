# 面试叫号系统（Vue3 + Spring Boot 版）

面向社团/组织招新场景的面试叫号与评分管理系统：考生扫码报名、签到候场，工作人员分组叫号，三位面试官同组打分，管理员审批与录取决策；大屏实时展示各组队列，业务变更通过 SSE 推送到各在线页面即时刷新。

## 功能特性

- **报名与签到**：考生公开报名/查询签到，工作人员后台签到、快速分组与改派，支持 CSV 批量导入、评分与名单导出。
- **一面流程**：按 A/B/C/D 分组叫号，三席打分，打满自动完成一面；管理员审批一面结果。
- **二面流程**：一面通过后推进二面，二面签到、分组、叫号、三席打分，与一面成绩按轮次隔离。
- **录取决策**：二面完成后由管理员做录取/淘汰决策，可录入录取部门。
- **候场大屏**：独立大屏页面展示各组正在面试与候场队列、待面试人数、扫码签到二维码，支持一面/二面场次切换。
- **实时刷新**：后端在业务变更落库后通过 SSE 广播 `data` 事件，分流台、工作台、大屏、结果管理页静默刷新；定时轮询保留作为断线兜底。
- **角色权限**：管理员、流动人员、面试官三类角色，按角色隔离菜单、入口与操作权限。

## 技术栈

| 端 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.5、Java 21、MyBatis-Plus、PostgreSQL、JJWT、Apache Commons CSV、Lombok |
| 前端 | Vue 3、Vite 6、Element Plus、Vue Router、qrcode |
| 实时通信 | Server-Sent Events（SSE） |
| 测试 | Python 回归脚本（`tests/`） |

## 目录结构

```
.
├── backend/                # Spring Boot 后端
│   ├── src/main/java/...    # 认证、业务服务、SSE 推送、配置
│   ├── src/main/resources/  # application.yml
│   ├── db/schema-postgres.sql  # PostgreSQL 建表脚本（参考/兜底）
│   └── pom.xml
├── frontend/               # Vue3 + Vite 前端
│   ├── src/pages/          # 报名、分流台、工作台、二面、大屏、结果管理等页面
│   ├── src/components/     # 布局与公共组件
│   └── package.json
├── docs/                   # 工作人员账号清单与权限说明
└── tests/                  # 回归与边界测试脚本
```

## 环境要求

- JDK 21 及以上
- Node.js 18+（推荐 20+）与 npm
- PostgreSQL 14+（线上使用 Supabase Postgres）

## 快速开始

### 1. 初始化数据库

业务表（`candidates`、`interviewer_scores`、`staff`）由后端首次启动时自动创建（`IF NOT EXISTS`），无需手动导入 DDL；如需手动建表可参考 [`backend/db/schema-postgres.sql`](backend/db/schema-postgres.sql)。

### 2. 启动后端

```bash
cd backend
# Windows
mvnw.cmd spring-boot:run
# Linux / macOS
./mvnw spring-boot:run
```

后端默认监听 `http://localhost:8080`，健康检查：`GET /api/health`。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev      # 开发模式，Vite 代理 /api 到后端 8080
npm run build    # 生产构建，产物输出到 frontend/dist
```

开发模式下前端通过 Vite 代理把 `/api` 请求转发到 `http://127.0.0.1:8080`。

## 配置项与环境变量

后端配置位于 `backend/src/main/resources/application.yml`。敏感项均支持通过环境变量覆盖，未设置时使用仅适用于本地开发的默认值：

| 环境变量 | 作用 | 开发默认值 |
| --- | --- | --- |
| `DB_URL` | JDBC 连接串 | 本地 `jdbc:postgresql://127.0.0.1:5432/interview_system` |
| `DB_USERNAME` | 数据库用户名 | `postgres` |
| `DB_PASSWORD` | 数据库密码 | `123456` |
| `JWT_SECRET` | JWT 签名密钥（长度至少 32 字符） | 内置开发占位密钥 |
| `ADMIN_PASSWORD` | 管理员登录口令 | `change-me` |
| `DELETE_ALL_PASSWORD` | 清空全部考生的二次确认口令 | `change-me` |
| `CORS_ORIGIN` | 允许的前端来源，逗号分隔；`*` 为全部（仅开发用） | `*` |

外部 PostgreSQL 实例通过 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` 覆盖；业务表由后端启动时自动创建（`IF NOT EXISTS`），无需手动导入 DDL。线上部署步骤见 [`docs/deploy-guide.md`](docs/deploy-guide.md)。

## 默认账号与角色权限

> 种子账号由后端首次启动时自动写入 `staff` 表，口令以加盐哈希存储，不明文落库。

| 角色 | 账号 | 初始口令 | 主要权限 |
| --- | --- | --- | --- |
| 管理员 | `admin` | 环境变量 `ADMIN_PASSWORD`（默认 `change-me`） | 全部操作：签到、分组改派、推进二面、审批、录取淘汰、叫号打分、场次切换、CSV 导入、结果管理与导出、删除 |
| 流动人员 | `流动人员1` ~ `流动人员9` | `123456` | 后台签到、一面分组/改派、二面分组/改派、查看分流台队列；不可推进二面、审批、录取淘汰、进入打分工作台、叫号打分、切换场次、导入与结果管理 |
| 面试官 | `面试官A1~A3`、`B1~B3`、`C1~C3`、`D1~D3` | `123456` | 本组一面/二面叫号与打分，跨组不可见 |

完整账号清单见 [`docs/工作人员账号清单.md`](docs/工作人员账号清单.md)。

工作人员接口必须携带登录接口返回的 `Authorization: Bearer <token>`；缺失、无效或过期令牌返回 HTTP 401。操作权限以令牌对应的工作人员身份为准，请求体中的 `operatorName`、`interviewerName` 不能代替登录或提升权限。公开报名、学生签到、公开详情、大屏数据及当前场次读取保持匿名可用，切换场次仅限管理员。

## 测试

无需启动后端或连接数据库的隔离回归检查：

```powershell
cd backend
.\mvnw.cmd "-Dtest=AuthInterceptorTests,CandidateServiceTests,SecondRoundServiceTests,CandidateMapperSqlTests" test
cd ../frontend
npm test
npm run build
cd ..
python -B -m unittest discover -s tests -p "test_api_client.py"
```

Linux / macOS 使用 `./mvnw` 替换 `mvnw.cmd`。默认的后端 `contextLoads` 测试仍需可连接的 PostgreSQL，因此上述命令显式选择隔离测试。

`tests/` 下的 Python 接口回归需先启动后端与 PostgreSQL，脚本会创建或修改考生数据，请使用独立测试数据库。它们通过共享 `api_client.py` 登录并缓存各账号 JWT：工作人员写请求按顶层操作人字段选择真实账号，读请求默认使用管理员，公开学生接口不携带令牌；流动人员权限测试使用流动人员自己的令牌。密码可通过脚本进程的 `ADMIN_PASSWORD`（默认 `change-me`）和 `STAFF_PASSWORD`（默认 `123456`）覆盖，须与数据库中的当前账号口令一致。

- `regress-30-flow.py`：30 名考生全链路回归（报名 → 签到 → 分组 → 一面叫号打分 → 审批 → 推进二面 → 二面签到/分组/叫号/打分 → 录取/淘汰）。
- `regress-edge.py`：权限与状态边界（重复报名/签到、非法打分、非法状态流转、越权操作拦截等）。
- `regress-edge-scores.py`：评分轮次隔离与边界校验。
- `verify-second-flow.py`：单名考生的两轮三席评分与二面大屏复验。

`regress-edge.py` 和 `regress-edge-scores.py` 依赖前一个全链路脚本留下的状态样本；`verify-second-flow.py` 请在独立的空测试库运行，避免固定测试手机号重复。历史 PowerShell 脚本保留旧接口流程，工作人员请求须显式携带登录令牌，不能直接用于验证当前 A-D 二面流程。

## 安全须知（上线前必读）

- 务必通过环境变量覆盖 `JWT_SECRET`、`ADMIN_PASSWORD`、`DELETE_ALL_PASSWORD`、`DB_PASSWORD`，不要使用仓库内的开发默认值。
- 首次使用后统一更换面试官/流动人员的 `123456` 初始口令。
- 生产环境将 `app.cors.allowed-origins` 从 `*` 收敛为实际前端域名。
- 仓库内不包含真实考生数据；本地库中的测试/脏数据不会随源码提交。

## 许可证

本项目按 MIT 许可证开源。
