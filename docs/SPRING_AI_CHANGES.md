# Spring AI Agent 变更文件清单

本清单对应当前 IDEA 工程。完整可运行源码见交付的 `ai-job-search-cn-idea.zip`；变更文件包只用于审阅和覆盖旧版，覆盖前请备份旧工程与 `data/`，不要直接覆盖正在运行的目录。

## 2026-11-10 后台 Agent 与默认离线修正

### 新增文件

- `src/main/java/cn/jobsearch/config/AgentExecutorConfig.java`：核心 2、最大 4、队列 32 的有界 Agent 后台执行器。
- `src/test/java/cn/jobsearch/service/crawler/BossCrawlerServiceTest.java`：验证默认在线拒绝发生在 robots、HTTP 和入库之前。
- `src/test/java/cn/jobsearch/mcp/BossMcpServerTest.java`：验证 MCP 独立 JVM 默认禁用及显式环境/系统属性开关。

### 后端修改文件

- `src/main/java/cn/jobsearch/service/agent/JobAgentService.java`：POST 先保存并返回 `RUNNING`，模型和工具移到后台；队列拒绝落库失败；运行中不伪报取消；浏览器关闭时不注册 BOSS 工具。
- `src/main/java/cn/jobsearch/config/BrowserProperties.java`：新增 `enabled=false`。
- `src/main/java/cn/jobsearch/config/CrawlerProperties.java`：新增 `onlineEnabled=false`。
- `src/main/java/cn/jobsearch/service/browser/BrowserAutomationService.java`：start/open/collect 统一默认禁用，status 返回离线说明，close 保留清理能力。
- `src/main/java/cn/jobsearch/service/crawler/BossCrawlerService.java`：在线搜索在 robots 和网络请求前返回 `BOSS_ONLINE_DISABLED`，离线导入不变。
- `src/main/java/cn/jobsearch/mcp/BossMcpServer.java`：独立 MCP 进程读取 `BOSS_BROWSER_ENABLED`，保持默认 false，显式开启时与 Web 契约一致。
- `src/main/resources/application.yml`、`env.example`：新增 `BOSS_BROWSER_ENABLED=false`、`BOSS_ONLINE_ENABLED=false`。

### 前端修改文件

- `src/main/resources/static/js/agent.js`：增加单实例定时器、1.2 秒状态轮询、终态停止、404/失败清理和新对话清理；运行中不显示伪取消。
- `src/main/resources/static/js/app.js`：默认选择离线 HTML 导入。
- `src/main/resources/static/index.html`：Agent 后台执行说明、离线示例、BOSS 默认关闭提示、默认离线面板和 CSS/JS 版本参数。

### 测试与文档修改文件

- `src/test/java/cn/jobsearch/service/agent/SpringAiToolCallingIntegrationTest.java`：断言先返回 RUNNING，再有界等待 COMPLETED，保留真实工具链和两次模型请求断言，并检查默认无 BOSS 工具。
- `src/test/java/cn/jobsearch/AgentFrontendBrowserIT.java`：真实 Chrome 从首页点击 Agent；增加后台 RUNNING → 轮询 → COMPLETED 的前端交互测试。
- `src/test/java/cn/jobsearch/service/browser/BrowserAutomationServiceTest.java`、`src/test/java/cn/jobsearch/BrowserApiIntegrationTest.java`：覆盖默认禁用与显式开启后的原有行为。
- `AGENTS.md`、`README.md`、`docs/AGENT_GUIDE.md`、`docs/BOSS_GUIDE.md`、`docs/MCP_BROWSER.md`、`docs/ARCHITECTURE.md`、`docs/API.md`、`docs/VERIFICATION.md`：同步新契约和实测结果。

## 构建与配置

- `pom.xml`：Spring Boot 3.5.15；引入 Spring AI 1.1.8 BOM、OpenAI starter、JDBC ChatMemory starter。
- `src/main/resources/application.yml`：Spring AI OpenAI、ChatMemory、Agent 工具预算/记忆窗口/确认过期配置。
- `env.example`：Agent 可选环境变量示例。
- `AGENTS.md`：Spring AI 工具、记忆、确认和验证约束。
- `README.md`：更新技术栈、Agent 使用、测试、项目结构和隐私说明。

交付源码不再包含旧手工 HTTP 客户端及其旧测试。若用变更文件包覆盖上一版，请在停止应用后备份并删除：
- `src/main/java/cn/jobsearch/service/agent/DeepSeekClient.java`
- `src/test/java/cn/jobsearch/service/agent/DeepSeekClientTest.java`

## 模型调用与固定功能迁移

- `src/main/java/cn/jobsearch/config/AgentProperties.java`：新增 Agent 配置。
- `src/main/java/cn/jobsearch/config/AgentAiConfig.java`：新增 `ChatClient` Bean。
- `src/main/java/cn/jobsearch/config/AgentMemoryConfig.java`：新增消息窗口记忆和 Advisor。
- `src/main/java/cn/jobsearch/service/agent/StructuredAiService.java`：新增 Spring AI `.call().entity(...)` 结构化输出。
- `src/main/java/cn/jobsearch/service/agent/AgentService.java`：固定分析/材料功能改用 `StructuredAiService`。
- `src/main/java/cn/jobsearch/service/browser/BossSearchIntentService.java`：自然语言意图改用 `StructuredAiService`，步骤文案与当前两步采集一致。

## Agent 任务、持久化与确认

- `src/main/java/cn/jobsearch/domain/agent/AgentRun.java`
- `src/main/java/cn/jobsearch/domain/agent/AgentStep.java`
- `src/main/java/cn/jobsearch/domain/agent/AgentConfirmation.java`
- `src/main/java/cn/jobsearch/domain/agent/AgentRunStatus.java`
- `src/main/java/cn/jobsearch/domain/agent/ConfirmationStatus.java`
- `src/main/java/cn/jobsearch/repository/agent/AgentRunRepository.java`
- `src/main/java/cn/jobsearch/repository/agent/AgentStepRepository.java`
- `src/main/java/cn/jobsearch/repository/agent/AgentConfirmationRepository.java`
- `src/main/java/cn/jobsearch/dto/agent/AgentRunRequest.java`
- `src/main/java/cn/jobsearch/dto/agent/AgentRunResponse.java`
- `src/main/java/cn/jobsearch/dto/agent/AgentStepView.java`
- `src/main/java/cn/jobsearch/dto/agent/AgentConfirmationRequest.java`
- `src/main/java/cn/jobsearch/service/agent/JobAgentService.java`
- `src/main/java/cn/jobsearch/service/agent/AgentRunRecorder.java`
- `src/main/java/cn/jobsearch/service/agent/AgentExecutionGuard.java`
- `src/main/java/cn/jobsearch/service/agent/AgentConfirmationService.java`
- `src/main/java/cn/jobsearch/controller/AgentRunController.java`

以上均为新增。任务、步骤和确认单持久化在本机 H2；BOSS 当前页采集不暴露给模型直接调用。

## 模型工具

- `src/main/java/cn/jobsearch/service/agent/tool/AgentToolSupport.java`
- `src/main/java/cn/jobsearch/service/agent/tool/JobAgentTools.java`
- `src/main/java/cn/jobsearch/service/agent/tool/ProfileAgentTools.java`
- `src/main/java/cn/jobsearch/service/agent/tool/ApplicationAgentTools.java`
- `src/main/java/cn/jobsearch/service/agent/tool/BossBrowserAgentTools.java`

以上为原 Spring AI 升级新增工具。当前默认注册其中 6 个职位/档案/投递工具；只有显式开启浏览器后才增加 2 个 BOSS 基础页和状态工具。投递和档案只读，当前页采集始终不直接暴露给模型。

## 前端

- `src/main/resources/static/index.html`：新增 Agent 导航、目标输入、任务状态、确认卡、轨迹与说明。
- `src/main/resources/static/js/app.js`：注册 Agent 视图标题。
- `src/main/resources/static/js/agent.js`：新增任务、恢复、确认、拒绝、取消和新对话交互。
- `src/main/resources/static/css/app.css`：新增 Agent 桌面与移动端样式。

## 测试

- `src/test/java/cn/jobsearch/service/browser/BossSearchIntentServiceTest.java`：Mock 改为 `StructuredAiService`。
- `src/test/java/cn/jobsearch/service/agent/StructuredAiServiceTest.java`：新增结构化输出、无密钥和鉴权错误测试。
- `src/test/java/cn/jobsearch/service/agent/SpringAiToolCallingIntegrationTest.java`：新增真实 Spring AI HTTP + 工具回调 + 第二次模型请求集成测试（本地模拟服务）。
- `src/test/java/cn/jobsearch/service/agent/AgentExecutionGuardTest.java`：新增工具预算测试。
- `src/test/java/cn/jobsearch/service/agent/AgentToolSafetyTest.java`：新增工具白名单与禁止直接采集测试。
- `src/test/java/cn/jobsearch/service/agent/AgentConfirmationServiceIntegrationTest.java`：新增错配、拒绝、一次性、过期和条数上限测试。
- `src/test/java/cn/jobsearch/AgentRunApiIntegrationTest.java`：新增本地任务、步骤、前端资源和参数校验测试。
- `src/test/java/cn/jobsearch/AgentFrontendBrowserIT.java`：新增可选本机 Chrome 交互、截图和移动端溢出验证，不计入默认测试数。

## 文档

- `docs/AGENT_GUIDE.md`：新增完整使用、权限、隐私和排错说明。
- `docs/agent-workbench.png`、`docs/agent-workbench-mobile.png`：新增真实 Chrome 交互后的桌面与移动端截图。
- `docs/ARCHITECTURE.md`：更新 Spring AI 编排、记忆、任务和确认边界。
- `docs/API.md`：新增 Agent Run API 和静态资源。
- `docs/VERIFICATION.md`：记录本次实际测试、JAR、ZIP 与未实网验收项。
- `design/agent.md`：新增 Agent 前端与交互契约。
- `docs/SPRING_AI_CHANGES.md`：本清单。

## 未改变的边界

原有两步 BOSS 浏览器采集、robots 检查、离线 HTML 导入、5 个 MCP 工具、不自动投递、不导出 Cookie、不绕过验证码/风控均保留。实网 DeepSeek 与登录后的 BOSS 当前页采集需用用户自己的配置和人工会话验收。
