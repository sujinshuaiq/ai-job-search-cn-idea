# 职航 JobPilot · 国内 AI 求职 Agent

基于原项目 [ai-job-search-dev/ai-job-search](https://github.com/ai-job-search-dev/ai-job-search) 的“职位发现 → 匹配分析 → 求职材料 → 投递跟踪”理念，改造为 **IntelliJ IDEA + Java 17 + Spring Boot + DeepSeek OpenAI 兼容接口** 的国内求职应用。

> 不需要 Claude Code、Node、Bun、Python 或 LaTeX。IDEA 打开根目录 `pom.xml`，运行主类即可使用中文 Web 页面。

![职航中文求职仪表盘](docs/dashboard.png)

## 已实现功能

- **求职仪表盘**：职位、分析、高匹配、投递、面试与 Offer 统计。
- **中文职位库**：关键词、城市、来源、分析状态筛选，查看职位详情。
- **BOSS 直聘 Skill**：robots.txt 前置检查、公开单页入口、离线 HTML 解析、字段清洗和 SHA-256 去重。
- **真实浏览器协同**：Playwright 持久化本机会话；首次人工扫码，用户手动搜索并确认后读取当前页，单次最多 20 个职位。
- **MCP / Agent 工具**：同一 JAR 以 `--mcp-stdio` 启动，向 Claude、Cursor 提供 5 个 BOSS 浏览器工具；stdout 仅输出 JSON-RPC。
- **自然语言 + 界面选择**：DeepSeek JSON 意图解析，无密钥时使用本地中英文规则；关键词、城市、条数和运行方式均可编辑。
- **手动职位录入**：支持其他国内招聘平台或线下职位，不依赖页面结构。
- **DeepSeek Agent**：通过 OpenAI 兼容 `/chat/completions` 生成匹配分析、定向简历、BOSS 初次沟通话术和面试题。
- **无密钥本地模式**：明确标识 `LOCAL_DEMO`，用规则评分和模板完成全流程演示，不冒充 AI。
- **求职档案**：单一事实来源，提示词禁止模型虚构经历与绩效。
- **投递管理**：收藏、已投递、沟通中、面试、Offer、拒绝和结束状态，支持备注。
- **本地持久化**：H2 文件数据库，重启后数据保留。
- **免构建前端**：原生 HTML/CSS/JavaScript，响应式布局，无外部 CDN。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.3、Spring MVC |
| 数据 | Spring Data JPA、H2 |
| AI | DeepSeek OpenAI 兼容 API、Java HttpClient、Jackson |
| 采集 | Jsoup、robots.txt 规则检查、限速与失败关闭 |
| 浏览器 / MCP | Playwright Java 持久会话、自实现 MCP 2024-11-05 STDIO |
| 前端 | 原生 HTML、CSS、JavaScript |
| 测试 | JUnit 5、AssertJ、MockMvc、JDK 本地模拟 HTTP 服务 |

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

只有你自己的真实密钥才能验证实网模型效果；本地模拟 HTTP 测试只验证协议、鉴权头和 JSON 解析。

## 3. BOSS 职位采集说明

### 浏览器协同 + MCP（真实浏览器，两步操作）

1. 本机安装 Chrome 或 Edge，无需 Go、Node 或额外 MCP 二进制。首次保持默认有头模式。
2. 打开“BOSS 职位采集 → 浏览器协同 + MCP”，输入自然语言后点击 **解析并打开 BOSS**；也可修改关键词、城市和条数后点击 **按上方条件直接打开**，不调用 DeepSeek。
3. 首次真正导航前，程序检查 robots.txt，只打开无 query 的首页或 `/web/geek/job`。在真实浏览器中由你手动扫码、完成验证、输入关键词与城市并搜索；程序不会自动填词或翻页。
4. 激活肉眼可见职位列表的标签，点击 **确认当前页并采集**。不再需要单独勾选确认框；可先点“同步当前标签”核对 URL，同步状态不会刷新 BOSS 网页。
5. 结果区显示解析、新增、重复数和前 5 个职位预览。单次最多 20 个卡片；之后手动换条件或翻页，直接再次执行第二步即可。

重复打开已有 BOSS 页面不会重新导航；搜索打开新标签后，状态与采集会优先跟随当前可见的 BOSS 标签。完整两步说明、旧工程升级和故障排查见 [`docs/BOSS_GUIDE.md`](docs/BOSS_GUIDE.md)。

会话目录默认为 `./data/browser-profile`，已被 `.gitignore` 排除。可用环境变量 `BOSS_BROWSER_PROFILE`、`BOSS_BROWSER_HEADLESS`、`BOSS_BROWSER_CHANNEL=chrome|msedge`、`BOSS_BROWSER_EXECUTABLE_PATH`、`BOSS_BROWSER_TIMEOUT_MS` 调整。程序自动探测 Windows 标准 Chrome/Edge 安装路径，并强制 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`，不会自动下载浏览器。非标准安装位置可将 `BOSS_BROWSER_EXECUTABLE_PATH` 设为浏览器 exe 绝对路径。无头模式只用于已经建立会话的后续运行，不保证第三方网站允许无头登录。

MCP STDIO 启动：

```bash
java -Dfile.encoding=UTF-8 -jar target/ai-job-search-cn.jar --mcp-stdio
```

Claude Desktop 和 Cursor 示例在 `mcp/`。把示例里的 `C:\absolute\path\ai-job-search-cn.jar` 替换为本机 JAR 绝对路径；`JOB_SEARCH_API_BASE` 默认 `http://127.0.0.1:8080`。MCP 的 `importToApp` 默认 false，只有显式为 true 才导入另行启动的 Web 应用。完整工具与安全边界见 [`docs/MCP_BROWSER.md`](docs/MCP_BROWSER.md)。

### 离线 HTML 导入（稳定演示）

1. 正常打开 BOSS 直聘职位搜索页，不借助本程序登录或验证。
2. 在浏览器中保存完整网页 HTML，或复制包含职位卡片的 HTML。
3. 打开应用“BOSS 职位采集 → 离线 HTML 导入”。
4. 选择文件或粘贴内容，点击“解析并导入职位”。
5. 点击“载入演示 HTML”可不访问网站，直接验证 3 个虚构职位的导入流程。

解析器只保存卡片中存在的字段；没有详情时会标注“未自动抓取详情页”，不会补写职位责任。

### 在线单页入口的真实限制

工程已实现 `POST /api/crawler/boss/search`，但编写时核验的 BOSS `robots.txt` 明确包含：

```text
User-agent: *
Disallow: /*?query=*
Disallow: *?city=*
```

因此当前在线搜索通常会在 robots 阶段返回 `ROBOTS_DISALLOWED`，且**不会请求目标搜索页**。这是刻意保留的合规行为，不是可以通过加请求头修复的 bug。

工程不包含也不应添加：Cookie/localStorage 导出、账号数据采集、代理池、设备指纹或 stealth、滑块/验证码识别、内部 API、反爬绕过、自动打招呼或自动投递。遇 401、403、429、登录跳转或安全验证立即停止。持久化人工会话不等于获得采集授权。

完整 Skill：`.agents/skills/boss-zhipin-search/SKILL.md`。

## 4. 一条可重复的演示流程

1. 首页进入“职位库”。
2. 打开任意职位，点击“开始匹配分析”。
3. 查看匹配分、优势与缺口；无密钥时模式为 `LOCAL_DEMO`。
4. 点击“生成定向材料”，查看并复制定向简历和沟通话术。
5. 点击“加入投递管理”，在投递页更新状态和备注。
6. 前往“BOSS 职位采集”，载入演示 HTML 并导入。
7. 再次导入同一 HTML，第二次应全部去重。

## 5. 测试与打包

在 IDEA 的 Maven 工具窗口运行 `test`、`package`，或在项目根目录执行（需要本机 Maven）：

```bash
mvn test
mvn package
java -jar target/ai-job-search-cn.jar
```

也可以右键 `JobSearchIntegrationTest` 运行端到端测试。测试使用独立内存 H2，不会污染项目 `data/`。

实测结果见 [`docs/VERIFICATION.md`](docs/VERIFICATION.md)。

## 6. 数据库与重置

默认 JDBC：

```text
jdbc:h2:file:./data/jobsearch;DB_CLOSE_DELAY=-1
用户名：sa
密码：空（可用 H2_PASSWORD 环境变量设置）
```

停止应用后，删除项目根目录 `data/`，下次启动会重新生成演示数据。

不需要演示职位时，在 IDEA 环境变量中设置：

```text
SEED_DEMO_DATA=false
```

这会保留一个待填写的档案模板，但不生成演示职位和投递记录。

## 7. 项目结构

```text
ai-job-search-cn/
├── pom.xml                         # IDEA / Maven 工程入口
├── .mvn/                           # 阿里云 Maven 镜像
├── src/main/java/cn/jobsearch/     # 后端源码
├── src/main/resources/static/      # 中文前端与 BOSS 演示 HTML
├── src/test/                       # 单元与端到端测试
├── .agents/skills/boss-zhipin-search/
│   ├── SKILL.md                    # BOSS Skill 使用与输出契约
│   └── references/PARSING.md       # 选择器维护文档
├── mcp/                            # Claude / Cursor MCP 配置示例
├── docs/
│   ├── BOSS_GUIDE.md               # 两步采集、升级与故障排查
│   ├── ARCHITECTURE.md             # 架构、线程、信任边界
│   ├── API.md                      # API 请求响应
│   ├── MCP_BROWSER.md              # 浏览器协同与 MCP 契约
│   ├── INTERVIEW.md                # 项目讲解与架构问答
│   └── VERIFICATION.md             # 实际验证记录
├── data/                           # H2 与浏览器会话，运行时生成，不提交
└── target/                         # 构建产物，不提交
```

IDEA 交付包只保留国内 Java 应用、中文页面、BOSS Skill、MCP 示例与文档；原上游丹麦招聘站 Skill、LaTeX 模板等迁移参考不属于运行工程，**不是 Java 应用运行依赖**。Java 应用以 `src/`、`application.yml` 和本文档为准。

## 8. 已知边界

- 定位为**单用户、本机使用**；没有登录、多租户和权限系统，不应直接暴露到公网。
- H2 适合本地与演示；多人或生产使用需切换 MySQL/PostgreSQL、增加迁移和备份。
- 页面 DOM 可能变化，离线解析测试通过不代表 BOSS 线上结构永久有效。
- AI 输出可能错误，需人工核对；系统不承诺职位真实性或求职结果。
- 未配置真实 DeepSeek 密钥时使用本地演示；真实配置与实网验证结果见验证报告。
- 当前为同步 Agent 请求，长材料生成需要等待接口完成。

## 文档导航

- [BOSS 两步采集与故障排查](docs/BOSS_GUIDE.md)
- [架构说明](docs/ARCHITECTURE.md)
- [API 文档](docs/API.md)
- [项目讲解 / 面试问答](docs/INTERVIEW.md)
- [验证报告](docs/VERIFICATION.md)

## License

沿用原项目 MIT License，见 [LICENSE](LICENSE)。BOSS 直聘、DeepSeek 等名称归各自权利人所有；本工程为非官方个人求职辅助工具，不代表相关平台。
