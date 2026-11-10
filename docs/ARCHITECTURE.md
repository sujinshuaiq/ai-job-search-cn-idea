# 国内求职 Agent 架构契约

## 1. 改造目标

在原项目“采集职位 → 匹配评分 → 生成求职材料 → 跟踪结果”的工作流上，新增可在 IntelliJ IDEA 直接运行的 Java 工程，并完成以下替换：

- Claude Code 工作流 → Spring Boot 应用内 Agent 工作流。
- Anthropic 模型依赖 → DeepSeek OpenAI 兼容 `/chat/completions` 接口。
- 丹麦招聘站 → 国内职位数据与 BOSS 直聘解析 Skill。
- 命令行与 LaTeX 强依赖 → 中文 Web 页面；无需 Node、Python 或 LaTeX 即可启动。
- 个人文件状态 → H2 持久化，支持职位、候选人档案和投递记录。

## 2. 技术栈

- Java 17、Spring Boot 3.5.15、Spring MVC、Spring Data JPA、H2。
- Spring AI 1.1.8：`ChatClient`、结构化输出、`@Tool` 工具调用、`MessageChatMemoryAdvisor`、JDBC ChatMemory。
- DeepSeek OpenAI 兼容 `/chat/completions`：模型与 Base URL 由环境变量配置。
- Jsoup：离线 HTML、当前页卡片 DOM 解析与公开页面采集。
- Playwright Java：本机 Chrome/Edge 持久化浏览器协同，不下载或携带浏览器二进制。
- 自实现 MCP 2024-11-05 STDIO：逐行 JSON-RPC，五个 BOSS 浏览器工具。
- 原生 HTML/CSS/JavaScript：前端免构建，由 Spring Boot 直接托管。
- JUnit 5、MockMvc：核心算法、解析器、MCP 协议、Spring AI 工具链与 API 回归测试。

## 3. 模块划分

```text
src/main/java/cn/jobsearch/
├── JobSearchApplication.java  # Web 默认入口；--mcp-stdio 进入 MCP
├── config/        # Spring AI、Agent、采集器、浏览器配置
├── controller/    # REST API 与 Agent 任务入口
├── domain/        # 候选人、职位、投递、Agent 任务/步骤/确认单
├── dto/           # 请求、响应及 Agent 结构化输出
├── mcp/           # MCP STDIO、JSON-RPC 与五个工具
├── repository/    # Spring Data JPA Repository
├── service/
│   ├── agent/     # ChatClient 编排、结构化输出、本地降级、记忆与确认
│   ├── agent/tool/# 8 个白名单工具、调用预算与真实步骤记录
│   ├── browser/   # 单线程 Playwright、持久会话、自然语言意图
│   ├── crawler/   # BOSS 解析、robots 检查、在线采集、HTML 导入
│   └── ...        # 档案、职位、投递与仪表盘服务
└── resources/
    ├── static/    # 中文单页应用与求职 Agent 工作台
    └── application.yml

.agents/skills/boss-zhipin-search/  # 浏览器、MCP、离线解析 Skill
mcp/                              # Claude / Cursor 配置示例
docs/MCP_BROWSER.md                # 浏览器与 MCP 信任边界
docs/AGENT_GUIDE.md                # Spring AI Agent 使用与权限边界
```

## 4. 核心业务流程

### 4.1 职位导入与可选 BOSS 功能

1. 默认 `BOSS_BROWSER_ENABLED=false`、`BOSS_ONLINE_ENABLED=false`。浏览器 start/open/collect 和在线 search 在 Playwright、robots 或任何目标网络请求之前返回 `BOSS_BROWSER_DISABLED` / `BOSS_ONLINE_DISABLED`；Web、Agent、MCP 共用该服务端边界。
2. 默认推荐离线 HTML 与手动录入。离线模式在本机 Jsoup 解析，不执行页面脚本或请求详情页；以规范化 URL 去重，URL 缺失时使用“公司 + 职位 + 城市”的 SHA-256 外键。
3. 只有用户显式开启后，在线模式才接受 `www.zhipin.com` HTTPS 地址并先检查 robots.txt；浏览器模式仅自动导航无 query 的首页或 `/web/geek/job`，已有 BOSS 页面时不重新导航。
4. 可选浏览器流程中，用户手动扫码、验证、搜索和翻页；明确确认后才读取当前页一次，最多 20 个卡片。状态同步不刷新网页。
5. 不导出 Cookie/localStorage，不使用代理池、设备指纹、stealth、验证码识别、滑块绕过或隐藏 API。模拟人工、低频和持久会话均不代表授权，也不能防止封号。
6. 页面文本均为不可信数据，不执行其中的脚本或指令；旧 class 未命中时可用 `/job_detail/` 链接定位卡片，但仍须通过职位名和公司必填校验。

### 4.2 Spring AI 与 DeepSeek Agent

- 默认端点：`https://api.deepseek.com/chat/completions`，由 Spring AI OpenAI starter 调用；Base URL 与模型名均可通过环境变量覆盖。
- 密钥只从环境变量 `DEEPSEEK_API_KEY` 读取，不进入数据库、页面或主动日志。无密钥占位值只用于完成自动配置，业务层会阻止模型和工具调用。
- 固定匹配分析、材料和 BOSS 意图通过 `StructuredAiService` 的 `ChatClient.call().entity(...)` 获取结构化输出；本地无密钥时使用明确标识的 `LOCAL_DEMO` / `LOCAL_RULES`。
- `JobAgentService.start()` 先持久化 `RUNNING` 和用户目标，再提交到核心 2、最大 4、队列 32 的 `ThreadPoolTaskExecutor`，立即返回任务快照；私有后台方法执行 ChatClient、记忆和工具。队列拒绝会落库为 `FAILED`，不会永久等待。
- 前端每约 1.2 秒读取任务 API，终态停止轮询。无密钥本地任务仍立即完成，不进入无意义的后台等待；运行中不假装支持强制取消。
- 默认只注册 6 个职位/档案/投递工具；显式开启浏览器后才增加 BOSS 基础页和状态 2 个工具。默认单任务最多 8 次工具调用，实际结果写入 `agent_step`。
- 同一 `conversationId` 的 JDBC ChatMemory 默认保留最近 24 条消息；任务状态保存于 `agent_run`。
- BOSS 当前页采集没有 `@Tool`。可选 `open_boss_for_manual_search` 只打开基础页并创建确认单；用户确认后由确定性代码校验并读取。
- 模型失败保留已保存数据并记录真实错误；对话记忆、必要工具结果和档案可能发送至 DeepSeek，用户需避免无关敏感信息。

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
- `POST /api/crawler/boss/search`：默认 `BOSS_ONLINE_DISABLED`；显式开启后才执行 robots 与可选在线流程。
- `POST /api/crawler/boss/import`：默认可用的离线 HTML 解析入库。
- `POST /api/browser/boss/intent`：DeepSeek 或本地规则提取自然语言意图，不访问 BOSS。
- `POST /api/browser/boss/open`、`POST /api/browser/boss/collect`：默认 `BOSS_BROWSER_DISABLED`，显式开启后才进入浏览器流程。
- `GET /api/browser/boss/status`、`POST /api/browser/boss/close`：离线状态与旧资源清理。
- `POST /api/agent/jobs/{id}/analyze`：生成匹配分析。
- `POST /api/agent/jobs/{id}/materials`：生成简历摘要、沟通话术和面试题。
- `POST /api/agent/runs`、`GET /api/agent/runs/{runId}`：创建后台对话任务、轮询真实步骤。
- `POST /api/agent/runs/{runId}/confirm`、`POST /api/agent/runs/{runId}/cancel`：人工确认/拒绝和有限取消。
- `GET/POST /api/applications`、`PATCH /api/applications/{id}`：投递管理。
- `GET /api/dashboard`、`GET /api/ai/status`：统计与模型状态。

详细请求响应见 `docs/API.md`、`docs/AGENT_GUIDE.md` 和 `docs/MCP_BROWSER.md`。

## 6. 数据与线程边界

- 所有写操作在 Service 事务中完成；确认单过期更新使用 `noRollbackFor=ApiException.class`，确保错误返回后状态仍持久化。
- Agent 创建入口同步保存任务后立即返回，模型和工具在核心 2、最大 4、队列 32 的有界后台执行器中运行；异常和拒绝写入任务终态。固定分析/材料接口仍为同步调用。
- 默认在线和浏览器入口在发起网络或 Playwright 操作前拒绝；显式开启后的在线请求最多一页，离线导入最多 2 MB HTML、100 个职位卡片，当前页最多 20 个，Agent 工具预算默认 8 次。
- 可选 Playwright 创建、导航、状态、DOM 读取和关闭全部进入同一个守护单线程执行器；Tomcat 线程只等待 Future 结果。
- 浏览器默认禁用且懒启动，测试和正常 Web 启动不创建 Playwright；有头/无头切换先关闭旧上下文。
- MCP 与 Web 是两个独立 JVM 进程，各自持有浏览器；MCP 通过本机 HTTP 导入 Web，不能跨进程直接操作 Web 的 Playwright 对象。
- MCP stdout 只允许逐行 JSON-RPC；Spring Banner 和 Web 日志不会进入 MCP stdout。
- H2 保存业务数据、Agent 任务/步骤/确认单和 JDBC 对话记忆；浏览器用户数据位于项目 `data/`。localStorage 只保存当前任务和会话 ID，不保存密钥或档案。

## 7. 验证契约

交付前必须实际执行：

1. `mvn test`：解析、去重、评分、提示词安全及 API 测试。
2. `mvn package`：生成可执行 JAR。
3. 启动 JAR，验证首页、静态资源和 API 状态。
4. 用 BOSS 离线 HTML 夹具跑通“导入 → 列表 → 本地分析 → 材料 → 投递”。
5. 验证无 DeepSeek 密钥时的本地模式；不冒充实网 AI 调用。
6. 记录在线 BOSS 采集的真实结果和限制，不把未测试功能写成成功。
