# 实际验证报告

本报告只记录实际执行结果，并明确区分自动化测试、真实本机运行和未验证项。BOSS 基础页打开成功不等于登录后的职位采集成功。

## 当前修复版：2026-11-08 两步浏览器协同

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
