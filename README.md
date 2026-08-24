# 🪑 家具商城系统 (FurnitureSystem)

一个全栈家具电商平台，包含用户端商城和管理后台，集成 AI 智能客服功能。

## ✨ 功能特性

### 用户端

- **游客浏览** — 无需登录即可浏览首页/分类/商品/评论，账号操作（下单/收藏/评价）时才引导登录
- **用户系统** — 邮箱注册、密码登录、**验证码登录（仅邮箱）**、忘记密码重置、个人信息管理（改绑邮箱需新邮箱验证码、**昵称修改经 AI 审核 / 头像变更需管理员人工审核**）
- **家具浏览** — 首页推荐、分类浏览、商品详情、多图展示
- **购物车** — 游客可本地加购，结算需登录；数量修改、SKU 规格选择、抽屉内收货地址选择与添加
- **订单系统** — 下单、支付倒计时（24h）、超时自动取消、订单状态跟踪、**多状态页签筛选**
- **退款/售后** — 已支付/已发货/已完成/已评价订单可申请退款，管理员审核后释放库存
- **收货地址** — 多地址管理（增删改查）
- **收藏功能** — 收藏/取消收藏家具
- **评价系统** — 商品评价、追评、评价评论区（支持多级回复），发表内容经管理员审核后公开展示
- **通知中心** — 站内消息通知、邮件通知（异步推送），用户可删除自己视角的通知（公告/用户状态解耦）
- **AI 智能客服** — 独立对话页，基于 RAG 的家具推荐助手"小智"，支持流式对话、商品卡片推荐

### 管理后台

- **数据仪表盘** — 订单趋势、销售额统计、热门商品排行、**库存预警（抽屉筛选）**
- **用户管理** — 用户列表、状态启禁
- **家具管理** — 商品 CRUD、SKU 规格管理、图片上传（OSS）
- **分类管理** — 家具类型维护
- **订单管理** — 订单列表、状态流转、商品规格展示
- **售后处理** — 独立售后页，退款申请同意/拒绝/审核、库存释放
- **评价审核** — 评价/追评/回复的审核管理（通过/拒绝）
- **昵称/头像审核** — 用户昵称改动的 AI 审核、头像变更的人工审核（拒绝原因可下拉+自定义输入）
- **操作日志** — 管理端 CRUD 与用户端敏感操作留痕（参数脱敏、主键分页）
- **通知管理** — 站内通知发布、编辑、删除
- **通知设置** — 订单/退款/库存告警的邮件通知规则与接收人配置
- **内容配置** — 首页轮播图、服务栏等站点运营位管理
- **菜单分组** — 按商品/交易/用户/内容域分组，待处理角标提示（订单/退款/审核等实时刷新）

## 🛠 技术栈

| 层级        | 技术                                                                  |
|-----------|---------------------------------------------------------------------|
| **后端**    | Spring Boot 3.5 · Java 17 · MyBatis-Plus · Spring Security          |
| **前端**    | Vue 3 · Vite 5 · Element Plus · Tailwind CSS 4 · Pinia · Vue Router |
| **数据库**   | MySQL 8.0 · Redis 7                                                 |
| **分布式锁**  | Redisson（看门狗机制，支持多实例部署）                                              |
| **消息队列**  | RocketMQ 4.9（订单状态流转、库存告警、邮件通知异步解耦）                                   |
| **AI 能力** | LangChain4j · 通义千问 qwen-max（流式对话 + RAG 知识库检索）                       |
| **对象存储**  | 阿里云 OSS（家具图片上传）                                                     |
| **邮件**    | Spring Mail + Thymeleaf 模板（验证码、订单通知、库存告警）                           |
| **接口文档** | Knife4j 4.5 · springdoc 2.8（首页自定义 Markdown 文档）                        |
| **部署**    | Docker Compose · Nginx 反向代理                                         |

## 📁 项目结构

```
FurnitureSystem/
├── furniture-vue/          # 前端 Vue 3 项目
│   ├── src/
│   │   ├── api/            # 接口请求
│   │   ├── components/     # 公共组件（购物车抽屉、通知铃铛等）
│   │   ├── composables/    # 组合式函数（含登录引导、返回导航）
│   │   ├── router/         # 路由配置（游客可浏览页 / 需登录页 / 管理端）
│   │   ├── stores/         # Pinia 状态管理（user/cart/system）
│   │   ├── styles/         # 全局样式
│   │   ├── utils/          # 工具函数（校验、图片 URL）
│   │   └── views/          # 页面
│   │       ├── admin/      # 管理后台（数据概览/用户/家具/订单/售后/评价审核等）
│   │       └── ...         # 用户端页面（首页/详情/购物车/订单/AiChatPage 等）
│   └── vite.config.js
├── src/main/java/gcy/      # 后端 Java 项目
│   ├── ai/                 # AI 智能客服模块（AiService、RAG 检索、工具、对话接口）
│   └── system/             # 业务模块
│       ├── aspect/         # 操作日志切面（含密码脱敏）
│       ├── config/         # 全局配置（Security、Redisson、异常、MyBatis、Knife4j）
│       ├── controller/     # 用户端 Controller
│       ├── controller/admin/ # 管理后台 Controller
│       ├── entity/         # 实体类（POJO / DTO / VO）
│       ├── exception/      # 业务异常
│       ├── integration/    # 外部集成（EmailService、OssService）
│       ├── listener/       # RocketMQ 消息监听器
│       ├── mapper/         # MyBatis-Plus Mapper
│       │   └── admin/      # 管理后台专用 Mapper
│       ├── security/       # Token 认证 + Spring Security
│       ├── service/        # 业务逻辑层（Impl、admin）
│       ├── task/           # 定时任务（订单超时取消、库存预警）
│       └── utils/          # 工具类（Redis 常量、校验、密码加密、JVM 锁）
├── src/main/resources/
│   ├── knife4jDoc/         # Knife4j 首页 Markdown 文档（home.md）
│   └── content/            # AI RAG 知识库文档
├── sql/                    # 数据库脚本
│   └── furniture-system.sql           # 完整数据库结构 + 初始数据
├── docs/                   # 项目文档（退款设计、代码审查记录）
├── docker/                 # Docker 配置（Nginx、Dockerfile）
├── docker-compose.yml      # 一键部署编排
└── pom.xml
```

## 📚 文档

- `docs/refund-feature-design.md` — 退款/售后功能 & 订单页签设计方案
- `docs/code-review-findings.md` — 后端代码审查问题与修复记录（含安全加固、验证要点）

## 🚀 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0
- Redis 7
- Docker & Docker Compose（推荐）

### 方式一：Docker 一键部署（推荐）

```bash
# 1. 克隆项目
git clone <your-repo-url>
cd FurnitureSystem

# 2. 配置环境变量（复制模板并填写）
cp .env.example .env
# 编辑 .env 填入必要的 API Key

# 3. 一键启动
docker-compose up -d

# 4. 访问
# 前端：http://localhost
# 后端 API：http://localhost:8080（容器内部端口 8081）
# 接口文档：http://localhost/doc.html
```

### 方式二：本地开发

```bash
# ---- 后端 ----
# 1. 启动 MySQL、Redis（可借助 Docker）
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=furniture-system mysql:8.0
docker run -d --name redis -p 6379:6379 redis:7-alpine

# 2. 导入数据库（含全部表结构与初始数据）
mysql -u root -proot furniture-system < sql/furniture-system.sql

# 3. 启动后端（默认端口 8081）
mvn spring-boot:run

# ---- 前端 ----
cd furniture-vue
npm install
npm run dev
# 访问 http://localhost:5173

# ---- 接口文档（后端已启动后）----
# 访问 http://localhost:8081/doc.html（Knife4j 在线接口文档，首页含自定义系统介绍）
```

## 📖 接口文档

系统集成 **Knife4j 4.5 + springdoc 2.8**，后端启动后访问：

| 地址 | 说明 |
|------|------|
| `http://localhost:8081/doc.html` | Knife4j 在线接口文档（推荐） |
| `http://localhost:8081/swagger-ui.html` | springdoc 原生 Swagger UI |
| `http://localhost:8081/v3/api-docs` | OpenAPI JSON（分组：家具商城API） |

- **自定义首页**：`doc.html` 首页展示项目简介、功能模块、技术栈、接口约定、订单状态机等，内容来自 `src/main/resources/knife4jDoc/home.md`（Markdown，可直接编辑）
- **调试授权**：右上角 Authorize 填入 `Bearer <token>` 后即可调试需登录接口
- 游客可访问：商品浏览、评论浏览、站点内容；需登录：下单、收藏、评价、地址、通知、AI

## ⚙️ 配置说明

核心配置通过环境变量注入，主要项：

| 变量                    | 说明                           | 必填          |
|-----------------------|------------------------------|-------------|
| `CHAT_AI_API_KEY`     | 通义千问 API Key（AI 聊天功能）        | 否           |
| `EMBEDDING_KEY`       | 通义千问 Embedding API Key      | 否           |
| `OSS_ACCESS_KEY`      | 阿里云 OSS AccessKey            | 是           |
| `OSS_SECRET_KEY`      | 阿里云 OSS SecretKey            | 是           |
| `OSS_BUCKET`          | OSS Bucket 名称                | 是           |
| `OSS_URL`             | OSS 访问 URL                   | 是           |
| `MYSQL_ROOT_PASSWORD` | MySQL 密码                     | 否（默认 root）  |
| `MAIL_HOST`           | SMTP 服务器                     | 否（默认 QQ 邮箱） |
| `MAIL_PORT`           | SMTP 端口                      | 否           |
| `MAIL_USERNAME`       | 邮件发送账号                      | 否           |
| `MAIL_PASSWORD`       | 邮件授权码                       | 否           |
| `ROCKETMQ_NAME_SERVER`| RocketMQ NameServer 地址        | 否           |

## 🏗 架构设计要点

- **分布式锁**：订单操作、缓存重建等关键流程使用 Redisson 分布式锁（看门狗自动续期），支持多实例水平扩展
- **异步解耦**：订单状态变更、库存告警、邮件通知通过 RocketMQ 消息队列异步处理；评论回复通知**事务提交后再发送 + 消费端幂等去重**
- **缓存策略**：家具详情使用 Cache-Aside 模式 + 分布式锁防击穿 + 空值缓存防穿透。家具分类缓存带 TTL 过期
- **订单状态机**：待支付(0) → 已支付(1) → 已发货(2) → 已完成(3)/已评价(5)，支持取消(4)，**退款链路 申请(6) → 审核(7) → 已退款(8)**。状态变更使用乐观锁（CAS）；24 小时未支付自动取消并释放库存
- **Token 认证**：自定义 Token 认证过滤器，Redis Hash 存储用户态，前后端分离无状态鉴权
- **安全加固**：登录/改密码失败 5 次锁定 5 分钟；订单评价/删除等接口校验归属防越权；操作日志密码字段脱敏；完整 token 不入日志；改绑邮箱需新邮箱验证码
- **RAG 智能客服**：基于 LangChain4j + Redis 向量数据库，结合通义千问大模型，实现家具知识库问答，支持流式响应与商品卡片推荐
- **密码安全**：BCrypt 加密，前后端统一密码校验规则（≥6 位，必须包含大小写字母和数字）
- **逻辑删除**：用户、家具、分类、订单、评价、通知等核心数据使用 MyBatis-Plus 逻辑删除，误删可恢复
- **通知系统**：公告与用户状态分离（UserNotification 表），管理员删除公告不影响用户已收到的通知，用户可独立删除自己视角的通知
- **接口文档**：Knife4j 4.5 + springdoc 2.8，首页自定义 Markdown 文档（`resources/doc/home.md`）
- **统一响应格式**：`Result` 类使用 `msg` 统一承载成功/失败消息，`data` 仅承载业务数据，前端统一优先使用后端返回的 `res.msg`；网络层错误由 Axios 拦截器统一处理，避免重复弹窗
- **Docker 编排**：MySQL / Redis / RocketMQ / 后端 / 前端五容器编排，开箱即用

## 📄 License

MIT
