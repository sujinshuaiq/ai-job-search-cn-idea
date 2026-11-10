# 实际验证报告

本报告只记录实际执行结果，并明确区分自动化测试、真实本机运行和未验证项。当前默认不连接 BOSS；本地模拟模型、真实 JAR 和真实 Chrome 验证都不等于 DeepSeek 实网或 BOSS 登录采集验收。

## 当前修正版：2026-11-10 后台 Agent 与默认离线

### 修正内容

- Agent 配置模型后先返回 `RUNNING`，模型和工具在核心 2、最大 4、队列 32 的后台执行器中运行；前端每约 1.2 秒轮询，终态停止。无密钥本地任务仍立即完成。
- 默认 `BOSS_BROWSER_ENABLED=false`、`BOSS_ONLINE_ENABLED=false`。Web、Agent、MCP 共用服务端禁用边界，默认不启动浏览器、不检查 BOSS robots、不请求 BOSS。
- 默认页面为离线 HTML 导入；静态 CSS/JS 增加版本参数。可选功能显式开启后仍保留原 robots、人工确认和单页上限，但不宣称能防止封号。
- 补齐 MCP 独立 JVM 对 `BOSS_BROWSER_ENABLED` 的读取，避免文档可开启而进程配置始终为 false 的不一致。

### 完整构建与回归

当前源码实际执行 `mvn clean package`：

- `BUILD SUCCESS`，退出码 `0`，耗时约 **19.52 秒**。
- **43 项默认测试全部通过**：`Failures: 0`、`Errors: 0`、`Skipped: 0`。工作区仍含已标记从交付包排除的旧 `DeepSeekClientTest` 1 项；最终 ZIP 独立重建数量单独记录。
- 可执行 JAR：`target/ai-job-search-cn.jar`，大小 **266,959,201 bytes**。
- `node --check src/main/resources/static/js/app.js` 与 `node --check src/main/resources/static/js/agent.js` 均退出 `0`。
- 新增覆盖：后台 RUNNING → COMPLETED、默认无 BOSS 工具、在线/浏览器网络前禁用、MCP 独立配置开关。原 Spring AI 工具回调、离线导入、去重和确认单测试继续通过。

### 真实 Chrome 页面交互：2 / 2 通过

额外执行 `mvn -Dtest=AgentFrontendBrowserIT test`，退出码 `0`，2 项测试全部通过：

1. 从首页真实点击左侧“求职 Agent”，示例可填，提交本地任务后显示完成和 2 条轨迹；再进入采集页确认默认显示离线面板、浏览器面板隐藏，返回 Agent 后检查 390px 移动布局无横向溢出，未捕获 `pageerror`。
2. 用浏览器路由模拟创建接口返回 RUNNING、查询接口返回 COMPLETED，真实页面从“执行中”自动轮询到“已完成”，至少发生一次状态 GET，未捕获 `pageerror`。

截图已在 `target/agent-workbench.png` 和 `target/agent-workbench-mobile.png` 生成并实际查看。第二项是前端轮询交互验证，模型协议和真实后台执行由下面的 JAR 冒烟独立验证。

### 真实 JAR 冒烟：12 / 12 通过

启动最终 JAR，使用独立内存 H2、随机端口和本机假 OpenAI 服务。没有访问真实 DeepSeek 或 BOSS：

- 首页默认离线和版本化静态资源正常；浏览器状态返回默认离线说明。
- `open` 返回 HTTP 428 / `BOSS_BROWSER_DISABLED`；`search` 返回 HTTP 428 / `BOSS_ONLINE_DISABLED`。
- 离线演示 HTML 解析 3、新增 3、重复 0。
- 假模型第一次响应故意延迟 3 秒，Agent POST 实测约 **0.062 秒**返回 `RUNNING`，没有同步等待 3 秒。
- 运行中取消返回 `AGENT_STILL_RUNNING`，未伪报中断成功。
- GET 轮询最终为 `COMPLETED`，`search_saved_jobs=SUCCESS`，数据库步骤真实存在。
- 假模型共收到 2 次请求；第二次 JSON 消息角色包含 `system`、`user`、`assistant`、`tool`，并含 `call_search_1`，证明工具结果回传。
- 第一次模型请求不包含 `open_boss_for_manual_search`，验证默认 BOSS 工具未注册。

结构化结果保存在本机 `target/async-offline-jar-smoke.json`。首轮冒烟的文本断言曾受 JSON 空格和 chunked 请求体影响，修正测试客户端的 JSON/chunked 解析后复验通过；应用链路本身已完成并返回正确工具结果。

### 真实 MCP STDIO 冒烟：6 / 6 通过

以最终 JAR 的 `--mcp-stdio` 独立启动，未设置浏览器启用开关：

- initialize、5 个工具 Schema、status、start、close、shutdown、exit 均完成，进程退出码 0。
- stdout 共 6 行响应，6/6 均为合法 JSON-RPC，无普通日志混入。
- status 返回 `browser.running=false` 和默认离线说明。
- start 返回 `BOSS_BROWSER_DISABLED`，没有启动浏览器；close 和 shutdown 正常。

结构化结果保存在本机 `target/mcp-offline-smoke.json`。

### 最终交付包复验

- 完整 IDEA 源码包按白名单重新生成，共 **141 个文件**；相对上一版基线 `target/pre-async-offline-idea-baseline.zip` 的变更包共 **33 个文件**。基线 ZIP 只用于比较，不进入交付包。
- 将完整 ZIP 解压到独立的 `target/async-offline-zip-check-20261110`，实际执行 `mvn clean package`：退出码 `0`、`BUILD SUCCESS`，**42 项默认测试全部通过**。工作区的 43 项包含已从交付 ZIP 排除的旧 `DeepSeekClientTest` 1 项，因此数量相差 1 是预期结果。
- 独立重建耗时约 **19.48 秒**。由该独立构建复制出的最终 `ai-job-search-cn.jar` 大小为 **266,954,768 bytes**，SHA-256 为 `fcd09758a710600803761226f0e69e42f77e5bb00b1e5d13e71cfc76d66e3dbb`；工作区根目录 JAR 与工程 `target/` JAR 的大小和哈希一致。
- 最终 JAR 共 367 个条目，CRC 全量检查无错误，不含旧 `cn/jobsearch/service/agent/DeepSeekClient.class`。复制后的最终 JAR 再次执行上述真实 JAR 冒烟 **12 / 12** 和 MCP STDIO 冒烟 **6 / 6**，全部通过；Agent POST 仍实测约 **0.062 秒**返回 `RUNNING`。
- 两个最终 ZIP 均通过全量 CRC、禁止项、通用真实密钥和私钥标记扫描，不带入 `target/`、运行时数据、浏览器会话或密钥；`env.example` 仅含明确的公开占位符。结构化结果保存在本机 `target/final-async-offline-package-report.json`。

### 当前未验证项

- 当前执行环境没有可用的真实 `DEEPSEEK_API_KEY`，**未进行真实 DeepSeek 计费调用**，未验收用户账号下 `deepseek-flash` 的权限、模型质量和真实工具规划。测试密钥仅为本机 `test-secret`。
- 本次没有扫码登录 BOSS、没有请求 BOSS 搜索页、没有读取真实线上职位卡片。默认禁用是刻意行为，不能把离线测试描述成在线采集成功。
- 未验证显式开启后的真实账号风控结果；任何实现都不承诺“避免封号”。
- 运行中的可信强制取消、应用重启后的后台任务恢复和多用户部署仍未实现；当前定位为单用户本机应用。

## 历史升级版：2026-11-09 Spring AI Agent

### 完整构建与回归

当前源码实际执行 `mvn clean package`：

- `BUILD SUCCESS`，退出码 `0`。
- **38 项默认测试全部通过**：`Failures: 0`、`Errors: 0`、`Skipped: 0`。
- 总耗时 `22.858 s`，完成时间 `2026-11-09T16:43:15+08:00`。
- 可执行 JAR：`target/ai-job-search-cn.jar`，大小 **266,955,511 bytes**。
- `node --check src/main/resources/static/js/app.js` 与 `node --check src/main/resources/static/js/agent.js` 均退出 `0`；CSS 花括号计数平衡。
- 原始构建日志保存在本机 `target/spring-ai-build.log`，不进入源码 ZIP。

新增回归覆盖：`StructuredAiServiceTest` 3 项、`SpringAiToolCallingIntegrationTest` 1 项、`AgentExecutionGuardTest` 1 项、`AgentToolSafetyTest` 2 项、`AgentConfirmationServiceIntegrationTest` 3 项、`AgentRunApiIntegrationTest` 2 项。已有 BOSS、MCP、离线导入、去重和固定本地分析测试仍通过。

### Spring AI 工具链实测

`SpringAiToolCallingIntegrationTest` 使用本机 JDK HTTP 服务模拟 OpenAI 兼容响应，不访问真实 DeepSeek：

1. 第一次模型响应要求调用 `search_saved_jobs`。
2. Spring AI 实际执行受控 Java 工具并查询内存 H2 中的 1 个职位。
3. 第二次 HTTP 请求包含 `role=tool` 和对应 `tool_call_id`。
4. 最终任务为 `COMPLETED`，`agent_step` 中 `search_saved_jobs=SUCCESS`，摘要含真实数量 1。
5. 模拟服务收到 `Authorization: Bearer test-secret`；该值仅为测试常量。

这证明当前 Spring AI 自动配置、OpenAI 协议、工具回调、ToolContext、任务记录和第二次模型请求链路可运行，不等于真实模型质量或实网密钥验收。

### 真实 JAR 冒烟：10 / 10 通过

使用更新后的真实 JAR、独立内存 H2、端口 `18085`、无 DeepSeek 密钥启动，原始结果保存在本机 `target/spring-ai-smoke-results.json`：

- Agent 页面、脚本、样式均可访问。
- 模型状态为 `LOCAL_DEMO`。
- POST Agent 任务返回 `COMPLETED`，明确未调用模型和工具；GET 可读取持久化步骤。
- 非等待任务不能直接确认，返回 `AGENT_NOT_WAITING_CONFIRMATION`。
- 空目标返回 `VALIDATION_ERROR`。
- 手动职位创建返回 201；固定分析仍返回 `LOCAL_DEMO`。

### 真实 Chrome 页面交互：1 / 1 通过

额外执行 `mvn -Dtest=AgentFrontendBrowserIT test`，完成时间 `2026-11-09T16:48:39+08:00`：

- Playwright 使用本机 Chrome 打开真实随机端口 Web 应用，等待 Agent 视图并完成示例按钮点击。
- 实际提交本地 Agent 任务，页面显示“已完成”、本地说明和 2 条执行轨迹；等待人工确认卡保持隐藏。
- 未捕获前端 `pageerror`。
- 切换 390px 视口后 `document.documentElement.scrollWidth <= 390`，无横向文档溢出。
- 已生成并实际查看 `docs/agent-workbench.png`（1280 × 1134）和 `docs/agent-workbench-mobile.png`（390 × 1809）。

该 IT 使用本机 Chrome，因此不纳入无浏览器环境的默认 38 项测试；源码中提供，具备 Chrome 时可单独复验。

### IDEA 源码 ZIP 与变更包复验

最终按上一版白名单重新生成 `ai-job-search-cn-idea.zip` 和 `ai-job-search-spring-ai-changes.zip`：

- 完整工程 **138 个文件**，变更文件包 **53 个文件**；仅含国内 Java 工程、BOSS Skill、MCP 示例、测试和文档，不含上游丹麦招聘站脚本。
- 完整包不再包含旧 `DeepSeekClient.java` 和 `DeepSeekClientTest.java`；变更清单说明覆盖旧版时需要删除的两个文件。
- 两个 ZIP 均通过全量 CRC 检查；无 `target/`、`data/`、`.git/`、`.idea/`、缓存、日志、`.env`、H2 或浏览器会话；通用真实密钥和私钥标记扫描未命中。
- 将完整 ZIP 解压到独立 `target/spring-ai-zip-check-final`，实际执行 `mvn clean package`：退出码 `0`、`BUILD SUCCESS`，**37 项默认测试全部通过**（移除旧手工客户端测试 1 项，因此与工作区含旧文件时的 38 项不同）。
- 独立构建总耗时 `19.136 s`，完成时间 `2026-11-09T16:54:56+08:00`；最终交付 JAR 为 `ai-job-search-cn.jar`，大小 **266,951,078 bytes**。
- 原始日志为 `target/spring-ai-zip-check-final-build.log`，结构化结果为 `target/spring-ai-zip-final-report.json`，均不进入源码 ZIP。

### 本次未验证项

- 当前执行环境的 `DEEPSEEK_API_KEY` 不存在，**没有对升级版进行真实 DeepSeek 计费调用**，未验收账号下的 `deepseek-flash` 可用性、结构化输出质量和真实工具规划。历史旧实例的意图解析结果不能替代新版验收。
- 本次没有扫码登录 BOSS、没有读取真实线上职位卡片，也没有重新进行 MCP 真实浏览器冒烟；既有 MCP 协议 3 项测试通过。登录后的当前页 DOM、页面持续刷新和线上风控仍需用户按两步流程人工验收。
- 未实现或绕过自动填词、自动搜索、自动翻页、Cookie 导出、验证码/风控绕过和自动投递。

## 历史修复版：2026-11-08 两步浏览器协同

### 编译与自动化测试

当前源码实际执行 `mvn clean package`：

- `BUILD SUCCESS`，退出码 `0`。
- **26 项测试全部通过**：`Failures: 0`、`Errors: 0`、`Skipped: 0`。
- 总耗时 `17.677 s`，完成时间 `2026-11-08T20:06:11+08:00`。
- 可执行 JAR：`target/ai-job-search-cn.jar`，大小 **248,916,056 bytes**。
- 相比历史基线，`BrowserAutomationServiceTest` 从 2 项增至 3 项，`BossHtmlParserTest` 从 3 项增至 4 项；新增活动搜索标签、重复打开不导航和新布局 `/job_detail/` 兜底解析回归。

### 更新后真实 JAR 修复冒烟：10 / 10 通过

使用独立内存 H2、端口 `18084` 启动更新后的真实 JAR，原始结构化结果为本机 `target/browser-fix-smoke-results.json`：

| 分组 | 结果 | 实际检查 |
|---|---|---|
| 启动与两步页面 | 2 / 2 | 真实 JAR 启动；页面恰好两个步骤，无独立确认框 |
| 真实 Chrome 打开行为 | 3 / 3 | 经 robots 检查打开 `https://www.zhipin.com/web/geek/job`；第二次打开返回“已保留当前 BOSS 标签页，没有重新导航”且 URL 不变；正常关闭 |
| 新布局解析 | 1 / 1 | 旧 class 缺失时通过 `/job_detail/` 链接解析 1 个职位并成功入库 1 个 |
| 页面与脚本 | 4 / 4 | 桌面和移动端截图；真实 390px 仿真无横向溢出；`node --check` 通过 |
| **合计** | **10 / 10** | **全部通过** |

本次真实 Chrome 冒烟使用无头模式，页面标题为 `BOSS直聘`；没有扫码、没有登录、没有自动搜索、没有读取真实线上职位卡片。活动标签跟随和重复打开行为另由模拟浏览器单测覆盖，**真实登录后的搜索新标签采集仍需用户在新版上验收**。

### DeepSeek 真实配置复验

2026-11-08 对用户已经启动在 `127.0.0.1:8080` 的实例再次做了真实功能检查，没有把密钥写入文件、日志或结果：

- `GET /api/ai/status`：`configured=true`、`mode=DEEPSEEK`、模型为环境变量配置的 `deepseek-flash`。
- `POST /api/browser/boss/intent` 输入“帮我搜集杭州 Java 后端岗位数据，最多 3 个”：HTTP 200，耗时 **1.015 秒**。
- 返回 `query=Java 后端`、`city=杭州`、`maxItems=3`、`mode=DEEPSEEK`、`requiresManualSearch=true`。

这证明当前密钥配置和 DeepSeek 实网意图解析可用；它不是 BOSS 登录后采集成功的证明。该 8080 实例仍是用户此前启动的旧交付工程，浏览器修复必须以新包重新构建启动后的实例为准。

当前截图已重新生成并实际查看：

- `docs/browser-collaboration.png`：1440 × 1200，166,783 bytes。
- `docs/browser-collaboration-mobile.png`：390 × 1600，100,586 bytes。
- 移动端 DOM 实测 `innerWidth=390`、`scrollWidth=390`、`bodyWidth=390`，两个步骤，无确认框。

两步操作、旧工程升级和故障排查见 `docs/BOSS_GUIDE.md`。

### 当前 IDEA 源码 ZIP 完整性复验

当前交付包 `ai-job-search-cn-idea.zip` 只包含 Java 工程、中文前端、测试、BOSS Skill、MCP 示例和文档：

- ZIP 条目：`101`，CRC 全量检查无错误；必需文件 `14 / 14` 存在，包括新增的 `docs/BOSS_GUIDE.md`。
- 无 `target/`、`data/`、`.git/`、`.idea/`、`.m2/`、缓存、日志、`.env`、H2 文件或浏览器 profile / Cookie；通用密钥模式与私钥标记扫描未命中。
- 将 ZIP 解压到独立的 `target/zip-check`，使用 JDK 17 和 Maven 3.9.16 实际执行 `mvn clean package`：退出码 `0`，`BUILD SUCCESS`。
- 解压重建为 **26 项测试全部通过**，Maven 总耗时 `13.924 s`，完成时间 `2026-11-08T20:21:26+08:00`。
- 解压重建生成 `target/ai-job-search-cn.jar`，大小 **248,916,056 bytes**。构建日志保存在原工作区 `target/zip-check-build.log`，日志和构建产物均不进入 ZIP。

这验证交付源码可独立重建，不依赖原工作区的 `target/` 或运行时数据；真实登录后的 BOSS 新标签采集仍按上文标记为待用户验收。

## 历史基线：2026-11-07 初版交付

以下记录保留用于对照，描述的是修复前的 24 项测试、35 项冒烟和旧三步页面，不代表当前修复版的数量或界面。

验证时间锚点：2026-11-07 21:25:49（本机 UTC+8，由 `date` 命令取得）。

## 1. 验证环境

- Windows x64，本机单用户运行。
- JDK：Microsoft OpenJDK 17.0.20.1。
- Maven：3.9.16，使用工程内阿里云镜像配置。
- JavaScript 语法检查：Node.js v24.15.0；Node.js 仅为验证工具，不是项目运行依赖。
- 浏览器：本机 Google Chrome，真实 Playwright Java 驱动。
- 最终 Web 冒烟：独立运行目录、独立内存 H2、端口 `18081`、无 `DEEPSEEK_API_KEY`。

## 2. 最终编译、测试与打包

最终源码实际执行：

```text
mvn clean package
```

结果：

- Maven 退出码：`0`。
- `BUILD SUCCESS`。
- 总耗时：`12.935 s`。
- 完成时间：`2026-11-07T21:23:03+08:00`。
- 测试：`Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`。
- 可执行 JAR：`target/ai-job-search-cn.jar`。
- JAR 大小：`248,913,986 bytes`（约 `237.38 MiB`）。

| 测试类 | 数量 | 覆盖点 |
|---|---:|---|
| `BrowserApiIntegrationTest` | 2 | 自然语言意图 API、人工确认与浏览器前置条件 |
| `JobSearchIntegrationTest` | 2 | 内存 H2 业务流、档案初始化、首页与静态资源 |
| `McpProtocolHandlerTest` | 3 | MCP 握手、工具 Schema、错误与确认约束 |
| `DeepSeekClientTest` | 1 | 本机模拟 HTTP、Bearer 鉴权、OpenAI 兼容请求体与 JSON 解析 |
| `LocalAgentEngineTest` | 2 | 匹配评分、优势缺口、材料不扩写档案事实 |
| `BossSearchIntentServiceTest` | 4 | 中文/英文意图、独立“找”字清理、DeepSeek JSON 意图分支 |
| `BrowserAutomationServiceTest` | 2 | BOSS 域名校验与浏览器读取边界 |
| `BossHtmlParserTest` | 3 | 多选择器、站外链接、脚本清理、验证页和空页 |
| `RobotsTxtServiceTest` | 3 | query/city 拦截、Allow 优先级、User-agent 分组 |
| `JobKeyGeneratorTest` | 2 | URL 规范化与复合去重键稳定性 |

前端脚本另执行：

```text
node --check src/main/resources/static/js/app.js
```

结果：退出码 `0`，无语法错误。

## 3. 最终真实 JAR 冒烟：35 / 35 通过

启动的是重新打包后的真实 `ai-job-search-cn.jar`，不是 MockMvc。最终结果为 **35 项通过、0 项失败**；原始结构化结果保存在本机 `target/final-smoke-results.json`。

| 分组 | 通过数 | 实际检查 |
|---|---:|---|
| 启动、静态资源与本地数据 | 9 | JAR 启动与种子初始化；首页中文和 UTF-8；`favicon.svg`；JavaScript；CSS；离线演示 HTML；12 个初始职位；2 条初始投递；无密钥时 `LOCAL_DEMO` |
| 意图与安全前置条件 | 4 | 中文自然语言；英文自然语言；未确认当前页返回 400；确认但未启动浏览器返回 428 / `BROWSER_NOT_RUNNING` |
| 离线导入与本地 Agent | 5 | 首次解析入库；重复导入去重；BOSS 来源筛选；本地分析；简历/话术/面试材料 |
| Web 浏览器协同 | 3 | 初始停止状态；真实 Chrome 打开无参数基础页；正常关闭会话 |
| 页面与脚本 | 4 | 桌面截图；移动端截图；390px 无横向溢出；JavaScript 语法检查 |
| MCP STDIO | 9 | initialize；5 个工具；ping；未确认拒绝；启动 Chrome；打开基础页；关闭浏览器；进程退出；stdout 纯 JSON-RPC |
| 日志回归 | 1 | Web 日志无 `favicon.ico` 和 `NoResourceFoundException` 异常 |
| **合计** | **35** | **全部通过** |

### 3.1 真实业务数字

| 项目 | 实测结果 |
|---|---|
| 初始演示职位 | 12 |
| 初始演示投递 | 2 |
| 无密钥 AI 状态 | `configured=false`、`mode=LOCAL_DEMO` |
| 中文输入 | `帮我在杭州找 10 个 Java 后端岗位` |
| 中文意图输出 | `query=Java 后端`、`city=杭州`、`maxItems=10`、`mode=LOCAL_RULES` |
| 英文输入 | `Find 5 Java backend jobs in Shanghai` |
| 英文意图输出 | `query=Java backend`、`city=上海`、`maxItems=5` |
| 本地分析 | 成功，示例匹配分 `74`，明确标记 `LOCAL_DEMO` |
| 离线演示 HTML 首次导入 | 解析 3、新增 3、重复 0 |
| 同一 HTML 第二次导入 | 解析 3、新增 0、重复 3 |
| BOSS 来源筛选 | 3 条 |
| 未确认当前页采集 | HTTP 400，未读取页面 |
| 已确认但浏览器未启动 | HTTP 428，`BROWSER_NOT_RUNNING` |

### 3.2 真实浏览器与 BOSS 边界

Web 模式和独立 MCP 模式分别真实启动本机 Chrome，均完成以下流程：

1. 启动持久化浏览器上下文。
2. 经 robots 检查后导航到 `https://www.zhipin.com/web/geek/job`。
3. 核对当前 URL 无 `query`，域名为 `www.zhipin.com`，页面标题为 `BOSS直聘`。
4. 关闭浏览器和驱动，保留测试会话目录。

本次**没有扫码、没有登录、没有自动填入关键词、没有搜索或翻页，也没有读取真实职位卡片**。因此这里验证的是浏览器启动、robots 前置检查、无参数基础页导航和资源释放，不是登录后采集成功。

### 3.3 MCP STDIO 实测

以独立 JVM 启动：

```text
java -Dfile.encoding=UTF-8 -jar target/ai-job-search-cn.jar --mcp-stdio
```

实测结果：

- 协议版本：`2024-11-05`。
- 工具列表恰好 5 个：`boss_browser_start`、`boss_open_jobs_page`、`boss_browser_status`、`boss_collect_current_page`、`boss_browser_close`。
- `boss_collect_current_page` 在 `userConfirmed=false` 时返回 `USER_CONFIRMATION_REQUIRED`。
- 完成 ping、真实 Chrome 启动、BOSS 基础页打开、关闭、shutdown 和 exit。
- MCP 进程退出码：`0`。
- stdout 共 `8` 行，`8 / 8` 均可独立解析为 `jsonrpc=2.0` 的 JSON-RPC 消息；没有浏览器下载进度或普通日志混入。
- 普通诊断日志写入 stderr。

本工程是 **Playwright Java + 自实现 MCP STDIO 服务**，没有声称嵌入或替换外部 `mcp-boss-zp` / go-rod 项目。

## 4. 前端视觉验证

最终截图来自真实运行中的 JAR 页面 `http://127.0.0.1:18081/?view=crawler`，两张图均已实际查看。

| 截图 | 尺寸 | 文件大小 | 结果 |
|---|---:|---:|---|
| `docs/browser-collaboration.png` | 1440 × 1200 | 164,301 bytes | 中文、侧边导航、步骤卡片、表单和按钮正常 |
| `docs/browser-collaboration-mobile.png` | 390 × 1600 | 99,858 bytes | 移动布局、标签横向滚动、表单和按钮正常 |

移动端使用 Chrome DevTools Protocol 的真实设备尺寸覆盖，而不是直接用桌面窗口截图。DOM 实测：

```text
innerWidth=390
document.documentElement.scrollWidth=390
document.body.scrollWidth=390
越界元素=[]
```

因此最终移动页面没有横向文档溢出。此次视觉验证聚焦“浏览器协同 + MCP”页；核心数据流另由真实 HTTP 和自动化测试覆盖，未逐个点击所有其他页面控件。

## 5. 最终修复与回归

1. 新增 `favicon.svg` 并显式链接，最终真实 Web 日志不再出现 `/favicon.ico` 的 500 / `NoResourceFoundException`。
2. 中文规则意图补充清理独立动词“找”，新增回归测试；“帮我在杭州找 10 个 Java 后端岗位”现在得到 `Java 后端`。
3. Playwright 创建时设置 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` 和 `NO_COLOR=1`；工程不会自动下载浏览器，最终 MCP stdout 纯 JSON-RPC 已复验。
4. Windows 支持本机 Chrome/Edge 路径探测，也可用 `BOSS_BROWSER_EXECUTABLE_PATH` 指定浏览器 exe；最终使用明确指定的本机 Chrome 验证通过。
5. 移动截图初次出现裁剪是 Chrome CLI 窗口宽度行为；改用真实 390px 设备仿真后确认页面无横向溢出，未以此误改桌面样式。

## 6. 未验证项与已知限制

- **未使用真实 `DEEPSEEK_API_KEY`**：没有进行实网 DeepSeek 计费调用，也没有模型质量结论。OpenAI 兼容协议由本机模拟 HTTP 单测验证；无密钥本地规则路径由真实 JAR 验证。
- **未进行用户扫码后的真实职位采集**：不能据此声称登录后 DOM 读取、去重入库已经实网验收。
- **未在真实登录页端到端验证 `importToApp=true`**：既有离线 HTML 导入 API 和重复去重已实测；MCP 的真实登录页读取并导入 Web 应用仍需用户人工扫码后验收。
- BOSS 页面结构、robots 规则和访问控制可能变化；测试夹具与本次基础页导航不保证线上 DOM 永久兼容。
- 未实现自动填词、自动搜索、自动翻页、Cookie 导出、代理池、stealth、验证码绕过、内部 API 或自动投递；这些是有意的安全边界，不是待宣称已完成的功能。
- 未做公网部署、多用户权限、安全渗透或高并发测试；工程定位为单用户本机求职助手。

## 7. IDEA 源码 ZIP 完整性复验

交付包 `ai-job-search-cn-idea.zip` 只包含 Java 工程、中文前端、测试、BOSS Skill、MCP 示例和文档，不包含无关的上游招聘站脚本。

- ZIP 条目：`100`。
- ZIP CRC 全量检查：无错误。
- 必需文件检查：`pom.xml`、`.mvn/`、`src/`、BOSS Skill、两个 MCP 配置、`design/`、验证文档和两张截图全部存在。
- 禁止项检查：无 `target/`、`data/`、`.git/`、`.idea/`、`.m2/`、依赖缓存、日志、`.env`、H2 文件、浏览器 profile / Cookie；文本扫描未发现疑似真实 DeepSeek 密钥。
- 将 ZIP 解压到一个全新的目录后，再次实际执行 `mvn clean package`：退出码 `0`，`BUILD SUCCESS`，24 项测试全部通过。
- 解压重建耗时：`12.264 s`，完成时间：`2026-11-07T21:30:01+08:00`。
- 解压重建同样生成 `target/ai-job-search-cn.jar`，大小为 `248,913,986 bytes`。

这验证的是交付 ZIP 的源码完整性和可重建性，不依赖原工作区中的 `target/` 或运行时数据。
