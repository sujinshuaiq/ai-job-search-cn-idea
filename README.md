# 职航 JobPilot · 国内 AI 求职 Agent

基于原项目 [ai-job-search-dev/ai-job-search](https://github.com/ai-job-search-dev/ai-job-search) 的“职位发现 → 匹配分析 → 求职材料 → 投递跟踪”理念，改造为 **IntelliJ IDEA + Java 17 + Spring Boot + DeepSeek OpenAI 兼容接口** 的国内求职应用。

> 不需要 Claude Code、Node、Bun、Python 或 LaTeX。IDEA 打开根目录 `pom.xml`，运行主类即可使用中文 Web 页面。

![职航中文求职仪表盘](docs/dashboard.png)

## 已实现功能

- **Spring AI 求职 Agent**：自然语言目标 → 模型规划 → 受限工具调用 → 真实执行轨迹；模型任务在有界后台执行器中运行，前端每约 1.2 秒自动更新，不再让 POST 同步等待到底。
- **对话记忆与任务恢复**：`ChatClient` + `MessageChatMemoryAdvisor` + JDBC ChatMemory，同一对话默认保留最近 24 条消息；任务、步骤和确认单持久化。
- **求职仪表盘**：职位、分析、高匹配、投递、面试与 Offer 统计。
- **中文职位库**：关键词、城市、来源、分析状态筛选，查看职位详情。
- **默认离线职位导入**：页面默认进入离线 HTML 导入，本机 Jsoup 解析、字段清洗和 SHA-256 去重；也可手动录入国内其他平台职位。
- **BOSS 在线功能默认关闭**：`BOSS_BROWSER_ENABLED=false`、`BOSS_ONLINE_ENABLED=false`，Web、Agent、MCP 均在任何浏览器、robots 或目标网络请求之前统一拒绝。
- **可选浏览器协同**：只有用户显式开启后才提供 Playwright 持久会话、手动搜索和当前页确认读取；模拟人工也不能避免封号。
- **MCP / Agent 工具**：同一 JAR 以 `--mcp-stdio` 启动，保留 5 个 BOSS 工具 Schema；默认 start/open/collect 同样返回禁用错误，不启动浏览器。
- **自然语言 + 界面选择**：DeepSeek 结构化意图解析，无密钥时使用本地中英文规则；意图解析本身不访问 BOSS。
- **DeepSeek 固定功能**：通过 Spring AI 结构化输出生成匹配分析、定向简历、BOSS 初次沟通话术和面试题。
- **人工确认边界**：模型不能直接采集 BOSS 当前页；可选浏览器功能开启后仍需一次性确认单，不自动投递。
- **无密钥本地模式**：明确标识 `LOCAL_DEMO`，用规则评分和模板完成固定功能演示，不冒充 AI。
- **求职档案**：单一事实来源，提示词禁止模型虚构经历与绩效。
- **投递管理**：收藏、已投递、沟通中、面试、Offer、拒绝和结束状态，支持备注。
- **本地持久化**：H2 文件数据库，重启后数据保留。
- **免构建前端**：原生 HTML/CSS/JavaScript，响应式布局，无外部 CDN；静态资源带版本参数，降低旧脚本缓存影响。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.5.15、Spring MVC |
| 数据 | Spring Data JPA、H2、Spring AI JDBC ChatMemory |
| AI | Spring AI 1.1.8、ChatClient、@Tool、DeepSeek OpenAI 兼容 API |
| 采集 | Jsoup、robots.txt 规则检查、限速与失败关闭 |
| 浏览器 / MCP | Playwright Java 持久会话、自实现 MCP 2024-11-05 STDIO |
| 前端 | 原生 HTML、CSS、JavaScript |
| 测试 | JUnit 5、AssertJ、MockMvc、JDK 本地模拟 OpenAI / HTTP 服务 |

## 1. 在 IDEA 中直接运行

### 环境要求

- IntelliJ IDEA 2023.3 或更新版本（Community / Ultimate 均可）。
- JDK 17。可在 IDEA 的 `File → Project Structure → SDKs` 中添加本机 JDK。
- 能访问阿里云 Maven 仓库以下载依赖。工程 `.mvn/settings.xml` 已内置镜像。

### 启动步骤

1. 解压工程，使用 IDEA **Open** 打开本目录，或直接打开根目录 `pom.xml` 并选择 **Open as Project**。
2. 等待 Maven 索引和依赖下载完成。
3. 确认 `Project SDK = 17`，`Language level = 17`。
4. 打开：

   ```text
   src/main/java/cn/jobsearch/JobSearchApplication.java
   ```

5. 点击主类左侧绿色三角形，选择 **Run 'JobSearchApplication'**。
6. 控制台出现 `Started JobSearchApplication` 后访问：

   - 应用首页：<http://localhost:8080>
   - H2 控制台：<http://localhost:8080/h2-console>
   - 健康检查式模型状态：<http://localhost:8080/api/ai/status>

首次启动包含 **12 个明确标识的虚构演示职位、2 条演示投递记录和 1 份示例档案**。它们不是真实招聘信息，请在页面中替换。

## 2. 配置 DeepSeek

### 推荐：IDEA 环境变量

打开 `Run → Edit Configurations → JobSearchApplication → Environment variables`，添加：

```text
DEEPSEEK_API_KEY=sk-替换为你的真实密钥
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-flash
```

保存后**重启应用**。密钥只从环境变量读取，不会返回前端、写入 H2 或主动打印日志。

默认调用：

```text
POST https://api.deepseek.com/chat/completions
Authorization: Bearer ${DEEPSEEK_API_KEY}
Content-Type: application/json
```

`deepseek-flash` 是编写本工程时从 DeepSeek 官方文档核验的默认模型。模型可用性可能随账号和平台变化，可通过 `DEEPSEEK_MODEL` 覆盖，以官方控制台为准。

### 无密钥也能演示

不设置 `DEEPSEEK_API_KEY` 时：

- 页面左下角显示“本地演示模式”。
- 匹配结果返回 `mode=LOCAL_DEMO`。
- 不会发起 DeepSeek 网络请求。
- 本地结果明确标注“不是 DeepSeek AI 分析”。

只有你自己的真实密钥才能验证实网模型效果；本地模拟 HTTP 测试验证协议、鉴权头、结构化输出和工具调用，不等于实网模型或 BOSS 登录采集验收。无密钥时，对话式 Agent 会明确说明未调用模型和工具；固定分析/材料按钮仍可使用本地规则。

## 3. 对话式求职 Agent

打开左侧 **求职 Agent**，或访问 <http://localhost:8080/?view=agent>：

- 查询已有职位：`查询职位库里杭州的 Java 后端岗位，告诉我最匹配的 3 个及理由`，不需要打开浏览器。
- 分析职位：`分析职位 ID 1 和我档案的匹配度`。
- 生成材料：`根据我的真实求职档案，为职位 ID 1 生成定向简历和初次沟通话术`。
- 新增职位：先使用离线 HTML 或手动录入，再让 Agent 查询；默认模式下 Agent 不获得 BOSS 工具。

配置模型后，POST 只负责创建任务并立即返回 `RUNNING`；模型和工具在核心 2、最大 4、队列 32 的后台执行器中运行，页面每约 1.2 秒读取状态，终态自动停止。后台队列拒绝会记录为 `FAILED`，不会永远卡住；运行中不伪报支持强制取消。无密钥本地任务仍立即返回 `COMPLETED / LOCAL_DEMO`。

默认注册 6 个受限工具，只有显式开启浏览器功能才增加 2 个 BOSS 工具。单任务默认最多 8 次调用；档案与投递工具只读，模型没有当前页采集或确认工具。任务轨迹来自数据库，不把模型自述当作执行凭证。

配置 DeepSeek 后，对话、必要工具结果和档案内容可能发送至模型服务。完整使用、隐私和排错说明见 [`docs/AGENT_GUIDE.md`](docs/AGENT_GUIDE.md)。

## 4. 职位导入与 BOSS 默认离线说明

### 离线 HTML 导入（默认推荐）

1. 在正常浏览器中查看职位列表并保存完整网页 HTML，不借助本程序绕过登录或验证。
2. 打开应用“BOSS 职位采集”，本版默认已选中 **离线 HTML 导入**。
3. 选择文件或粘贴内容，点击“解析并导入职位”。文件只在本机解析，不执行脚本、不读取 Cookie、不请求详情页。
4. 点击“载入演示 HTML”可不访问网站，直接验证 3 个虚构职位的导入与重复去重。

也可使用“手动录入”添加其他国内招聘平台或线下职位。解析器只保存真实卡片中存在的字段，不补写职位责任。

### 在线与浏览器功能默认关闭

```text
BOSS_BROWSER_ENABLED=false
BOSS_ONLINE_ENABLED=false
```

默认不会自动启动浏览器、刷新或请求 BOSS。Web、Agent 和 MCP 的 start/open/collect 都经过服务端统一禁用边界；在线 search 在 robots 之前返回 `BOSS_ONLINE_DISABLED`。这不是“防封号”技术，而是默认不执行可能带来账号风险的在线自动操作。

只有明确了解风险并确认有权操作时，才自行将对应开关设为 true 并重启。可选浏览器功能仍为“打开基础页 → 用户手动登录搜索 → 确认当前页读取一次”；可选在线入口仍先检查 robots，拒绝时不请求目标页。开启不代表获得授权或能避免封号。

MCP STDIO 启动：

```bash
java -Dfile.encoding=UTF-8 -jar target/ai-job-search-cn.jar --mcp-stdio
```

MCP 保留 5 个工具 Schema，默认同样不会启动浏览器。MCP 是独立 JVM，可选浏览器开关必须设置在 MCP 自己的环境中。Claude / Cursor 示例在 `mcp/`，完整契约见 [`docs/MCP_BROWSER.md`](docs/MCP_BROWSER.md)。

工程不包含也不应添加：Cookie/localStorage 导出、账号数据采集、代理池、设备指纹或 stealth、滑块/验证码识别、内部 API、反爬绕过、自动打招呼或自动投递。

完整升级、操作与故障排查见 [`docs/BOSS_GUIDE.md`](docs/BOSS_GUIDE.md)；Skill 位于 `.agents/skills/boss-zhipin-search/SKILL.md`。

## 5. 一条可重复的演示流程

1. 首页进入“职位库”。
2. 打开任意职位，点击“开始匹配分析”。
3. 查看匹配分、优势与缺口；无密钥时模式为 `LOCAL_DEMO`。
4. 点击“生成定向材料”，查看并复制定向简历和沟通话术。
5. 点击“加入投递管理”，在投递页更新状态和备注。
6. 前往“BOSS 职位采集”，载入演示 HTML 并导入。
7. 再次导入同一 HTML，第二次应全部去重。
8. 打开“求职 Agent”输入查询目标；无密钥时应返回明确的 `LOCAL_DEMO`，配置密钥后可观察真实工具轨迹。

## 6. 测试与打包

在 IDEA 的 Maven 工具窗口运行 `test`、`package`，或在项目根目录执行（需要本机 Maven）：

```bash
mvn test
mvn package
java -jar target/ai-job-search-cn.jar
```

也可以右键 `JobSearchIntegrationTest`、`AgentRunApiIntegrationTest` 或 `SpringAiToolCallingIntegrationTest` 运行测试。测试使用独立内存 H2 和本机模拟模型服务，不访问真实 DeepSeek，不污染项目 `data/`。

实测结果见 [`docs/VERIFICATION.md`](docs/VERIFICATION.md)。

## 7. 数据库与重置

默认 JDBC：

```text
jdbc:h2:file:./data/jobsearch;DB_CLOSE_DELAY=-1
用户名：sa
密码：空（可用 H2_PASSWORD 环境变量设置）
```

停止应用后，删除项目根目录 `data/`，下次启动会重新生成演示数据。H2 同时保存职位、档案、投递、Agent 任务/步骤/确认单和对话记忆；删除前请先备份。

不需要演示职位时，在 IDEA 环境变量中设置：

```text
SEED_DEMO_DATA=false
```

这会保留一个待填写的档案模板，但不生成演示职位和投递记录。

## 8. 项目结构

```text
ai-job-search-cn/
├── pom.xml                         # IDEA / Maven 工程入口
├── .mvn/                           # 阿里云 Maven 镜像
├── src/main/java/cn/jobsearch/     # 后端、Spring AI Agent 与受限工具
├── src/main/resources/static/      # 中文前端、Agent 工作台与 BOSS 演示 HTML
├── src/test/                       # 单元、工具链与端到端测试
├── .agents/skills/boss-zhipin-search/
│   ├── SKILL.md                    # BOSS Skill 使用与输出契约
│   └── references/PARSING.md       # 选择器维护文档
├── mcp/                            # Claude / Cursor MCP 配置示例
├── docs/
│   ├── AGENT_GUIDE.md              # Spring AI Agent 使用、权限与排错
│   ├── BOSS_GUIDE.md               # 两步采集、升级与故障排查
│   ├── ARCHITECTURE.md             # 架构、线程、信任边界
│   ├── API.md                      # API 请求响应
│   ├── MCP_BROWSER.md              # 浏览器协同与 MCP 契约
│   ├── SPRING_AI_CHANGES.md        # 本次更改/新增文件清单
│   ├── INTERVIEW.md                # 项目讲解与架构问答
│   └── VERIFICATION.md             # 实际验证记录
├── data/                           # H2 与浏览器会话，运行时生成，不提交
└── target/                         # 构建产物，不提交
```

IDEA 交付包只保留国内 Java 应用、中文页面、BOSS Skill、MCP 示例与文档；原上游丹麦招聘站 Skill、LaTeX 模板等迁移参考不属于运行工程，**不是 Java 应用运行依赖**。Java 应用以 `src/`、`application.yml` 和本文档为准。

## 9. 已知边界

- 定位为**单用户、本机使用**；没有登录、多租户和权限系统，不应直接暴露到公网。
- H2 适合本地与演示；多人或生产使用需切换 MySQL/PostgreSQL、增加迁移和备份。
- 页面 DOM 可能变化，离线解析测试通过不代表 BOSS 线上结构永久有效。
- AI 输出可能错误，需人工核对；系统不承诺职位真实性或求职结果。
- 配置 DeepSeek 后，对话、工具结果和必要档案内容可能发送给模型服务；不要输入密钥或无关敏感信息。
- 未配置真实 DeepSeek 密钥时使用本地演示；真实配置与实网验证结果见验证报告。
- 对话式 Agent 已改为后台执行和状态轮询；固定职位分析、材料生成按钮仍是同步接口，长生成需要等待对应请求完成。
- 默认不连接 BOSS 不等于具备“防封号”能力；显式开启在线功能仍需自行承担网站授权与账号风险。

## 文档导航

- [Spring AI Agent 使用指南](docs/AGENT_GUIDE.md)
- [本次更改文件清单](docs/SPRING_AI_CHANGES.md)
- [BOSS 两步采集与故障排查](docs/BOSS_GUIDE.md)
- [架构说明](docs/ARCHITECTURE.md)
- [API 文档](docs/API.md)
- [项目讲解 / 面试问答](docs/INTERVIEW.md)
- [验证报告](docs/VERIFICATION.md)

## License

沿用原项目 MIT License，见 [LICENSE](LICENSE)。BOSS 直聘、DeepSeek 等名称归各自权利人所有；本工程为非官方个人求职辅助工具，不代表相关平台。
