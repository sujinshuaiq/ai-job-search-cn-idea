# 国内求职 Agent 架构契约

## 1. 改造目标

在原项目“采集职位 → 匹配评分 → 生成求职材料 → 跟踪结果”的工作流上，新增可在 IntelliJ IDEA 直接运行的 Java 工程，并完成以下替换：

- Claude Code 工作流 → Spring Boot 应用内 Agent 工作流。
- Anthropic 模型依赖 → DeepSeek OpenAI 兼容 `/chat/completions` 接口。
- 丹麦招聘站 → 国内职位数据与 BOSS 直聘解析 Skill。
- 命令行与 LaTeX 强依赖 → 中文 Web 页面；无需 Node、Python 或 LaTeX 即可启动。
- 个人文件状态 → H2 持久化，支持职位、候选人档案和投递记录。

## 2. 技术栈

- Java 17、Spring Boot 3、Spring MVC、Spring Data JPA、H2。
- Java HttpClient / Jackson：DeepSeek OpenAI 兼容接口。
- Jsoup：离线 HTML、当前页卡片 DOM 解析与公开页面采集。
- Playwright Java：本机 Chrome/Edge 持久化浏览器协同，不下载或携带浏览器二进制。
- 自实现 MCP 2024-11-05 STDIO：逐行 JSON-RPC，五个 BOSS 浏览器工具。
- 原生 HTML/CSS/JavaScript：前端免构建，由 Spring Boot 直接托管。
- JUnit 5、MockMvc：核心算法、解析器、MCP 协议与 API 回归测试。

## 3. 模块划分

```text
src/main/java/cn/jobsearch/
├── JobSearchApplication.java  # Web 默认入口；--mcp-stdio 进入 MCP
├── config/        # AI、采集器、浏览器配置
├── controller/    # REST API
├── domain/        # 候选人、职位、投递记录实体
├── dto/           # 请求、响应及 Agent 结构化输出
├── mcp/           # MCP STDIO、JSON-RPC 与五个工具
├── repository/    # Spring Data JPA Repository
├── service/
│   ├── agent/     # DeepSeek 客户端、提示词、本地降级、匹配服务
│   ├── browser/   # 单线程 Playwright、持久会话、自然语言意图
│   ├── crawler/   # BOSS 解析、robots 检查、在线采集、HTML 导入
│   └── ...        # 档案、职位、投递与仪表盘服务
└── resources/
    ├── static/    # 中文单页应用
    └── application.yml

.agents/skills/boss-zhipin-search/  # 浏览器、MCP、离线解析 Skill
mcp/                              # Claude / Cursor 配置示例
docs/MCP_BROWSER.md                # 浏览器与 MCP 信任边界
```

## 4. 核心业务流程

### 4.1 职位采集

1. 在线模式只接受 `www.zhipin.com` HTTPS 地址；请求前读取 robots.txt，disallow 时不请求目标搜索页。
2. 浏览器模式仅自动导航无 query 的首页或 `/web/geek/job`，真正导航前同样检查 robots.txt。已有 BOSS 页面时打开操作幂等，不重新导航；站外页面不会被覆盖。
3. 用户手动扫码、验证、输入关键词/城市和翻页；只有明确确认后才读取当前页，单次最多 20 个卡片。状态和采集都会重新识别活动标签，优先可见 BOSS 标签；采集最多等待卡片出现 8 秒，状态同步不刷新网页。
4. 不导出 Cookie/localStorage，不使用代理池、设备指纹、stealth、验证码识别、滑块绕过或隐藏 API。持久会话不视为授权。
5. 遇 401/403/429/验证页立即停止，不自动重试绕过；持续导航返回 `PAGE_NOT_READY`，离线模式接收用户自行保存的 HTML，在本机 Jsoup 解析。
6. 以规范化 URL 去重；URL 缺失时使用“公司 + 职位 + 城市”的 SHA-256 外键。
7. 页面文本均为不可信数据，不执行其中的脚本或指令，不追踪正文链接。旧 class 未命中时使用 `/job_detail/` 链接定位卡片，但仍须通过职位名和公司必填校验，不虚构字段。

### 4.2 DeepSeek Agent

- 默认端点：`https://api.deepseek.com/chat/completions`。
- 密钥只从环境变量 `DEEPSEEK_API_KEY` 读取，不进入数据库、页面或日志。
- Base URL 与模型名均可通过环境变量覆盖。
- 匹配分析与材料生成分开调用，要求模型返回 JSON。
- 无密钥时启用明确标识的 `LOCAL_DEMO` 规则引擎，保证项目离线可演示；界面不得把本地模板称为 AI 结果。
- 模型失败保留已保存数据，返回可读错误；不伪造分析成功。

### 4.3 匹配评分

- 本地评分：技能覆盖、目标岗位、城市、工作年限四个维度。
- AI 评分：依据真实候选人档案和职位文本生成优势、缺口、摘要。
- 简历与沟通话术只重组档案已有事实，禁止补造学历、经历、指标和技能。
- 所有生成结果需用户人工检查，系统不自动投递、不自动发送 BOSS 沟通消息。

### 4.4 投递跟踪

状态：收藏、已投递、沟通中、面试中、Offer、已拒绝、已结束。每次状态变更记录更新时间；提供仪表盘统计。

## 5. API 契约概览

- `GET/PUT /api/profile`：读取/更新候选人档案。
- `GET /api/jobs`、`GET /api/jobs/{id}`：职位列表/详情。
- `POST /api/jobs/manual`：手动录入职位。
- `POST /api/crawler/boss/search`：合规在线采集（robots 拒绝时失败可见）。
- `POST /api/crawler/boss/import`：离线 HTML 解析入库。
- `POST /api/browser/boss/intent`：DeepSeek 或本地规则提取自然语言意图。
- `POST /api/browser/boss/open`、`GET /api/browser/boss/status`：基础页导航与状态。
- `POST /api/browser/boss/collect`、`POST /api/browser/boss/close`：人工确认当前页采集与关闭。
- `POST /api/agent/jobs/{id}/analyze`：生成匹配分析。
- `POST /api/agent/jobs/{id}/materials`：生成简历摘要、沟通话术和面试题。
- `GET/POST /api/applications`、`PATCH /api/applications/{id}`：投递管理。
- `GET /api/dashboard`、`GET /api/ai/status`：统计与模型状态。

详细请求响应见 `docs/API.md` 和 `docs/MCP_BROWSER.md`。

## 6. 数据与线程边界

- 所有写操作在 Service 事务中完成。
- 采集与 AI 请求为有界同步 HTTP 调用，设置连接和读取超时。
- 单次在线请求最多一页；批量导入最多 2 MB HTML、100 个职位卡片；浏览器当前页最多 20 个。
- Playwright 创建、导航、状态、DOM 读取和关闭全部进入同一个守护单线程执行器；Tomcat 线程只等待 Future 结果。
- 浏览器懒启动，测试和正常 Web 启动不创建 Playwright；有头/无头切换先关闭旧上下文。
- MCP 与 Web 是两个独立 JVM 进程，各自持有浏览器；MCP 通过本机 HTTP 导入 Web，不能跨进程直接操作 Web 的 Playwright 对象。
- MCP stdout 只允许逐行 JSON-RPC；Spring Banner 和 Web 日志不会进入 MCP stdout。
- H2 与浏览器用户数据位于项目 `data/`，删除后可重新初始化；会话删除由用户手动进行。

## 7. 验证契约

交付前必须实际执行：

1. `mvn test`：解析、去重、评分、提示词安全及 API 测试。
2. `mvn package`：生成可执行 JAR。
3. 启动 JAR，验证首页、静态资源和 API 状态。
4. 用 BOSS 离线 HTML 夹具跑通“导入 → 列表 → 本地分析 → 材料 → 投递”。
5. 验证无 DeepSeek 密钥时的本地模式；不冒充实网 AI 调用。
6. 记录在线 BOSS 采集的真实结果和限制，不把未测试功能写成成功。
