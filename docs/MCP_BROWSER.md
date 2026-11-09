# BOSS 浏览器协同与 MCP 契约

## 1. 目标与非目标

本工程提供一个基于 Playwright Java 的本地浏览器协同服务和轻量 MCP STDIO 服务。Claude、Cursor 等支持 MCP 的 AI 工具可以打开浏览器、读取状态，并在用户明确确认后解析当前 BOSS 职位列表页。

这不是外部项目 `mcp-boss-zp` 的封装，也不包含 Go/go-rod 程序。实现保持在同一个 Java 17 / IDEA 工程中。

**明确不实现：**自动填写关键词、自动构造搜索 URL、自动翻页、批量追踪详情页、Cookie/会话导出、代理池、设备指纹伪装、stealth 参数、验证码/滑块识别、内部 API、自动打招呼或自动投递。持久化登录会话不意味着获得绕过站点规则或采集授权的许可。

## 2. 人工协同流程

1. AI 或页面解析“帮我搜集杭州 Java 后端岗位数据”，仅得到关键词、城市和条数，不自动搜索。
2. 启动有头浏览器，用户数据目录默认保存在 `./data/browser-profile`。
3. 自动导航只允许 `https://www.zhipin.com/` 或不带 query 的 `https://www.zhipin.com/web/geek/job`；真正导航前检查 robots.txt，失败即停止。已有 BOSS 页面时重复打开会保留当前页，不重新导航，也不重复 robots 请求。
4. 用户在真实浏览器里自行扫码、完成安全验证、输入关键词与城市并搜索。首次必须人工完成；程序不代替用户操作。
5. 搜索若打开新标签，用户先激活肉眼可见职位列表的标签。Web 的“同步当前标签”和 MCP 状态工具只读取状态，不刷新网页。
6. 用户点击“确认当前页并采集”，或 MCP 调用传入 `userConfirmed: true`。按钮本身就是明确人工确认，不再有独立复选框。
7. 程序重新识别活动标签，优先选择可见 BOSS 标签，最多等待卡片出现 8 秒，只提取该标签中已经渲染的职位卡片 DOM；不重新请求搜索 URL，不提取其他标签内容，不导出 Cookie。
8. 页面应用将结果去重入库，并展示新增/重复数及前 5 个职位预览；MCP 默认只返回职位，只有 `importToApp: true` 才通过本机 Web API 导入。

无头模式只用于用户已经建立会话的后续运行。切换有头/无头会重启浏览器上下文，但复用同一用户数据目录。两步操作和错误排查见 `docs/BOSS_GUIDE.md`。

## 3. 浏览器线程与安全边界

- Playwright Java 对象不是线程安全的；创建、导航、状态、DOM 读取和关闭全部提交到同一个有界单线程执行器。
- Web 应用启动和测试启动时不自动创建浏览器，只有显式调用启动/打开工具才启动。
- 驱动强制 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`，不自动下载浏览器；自动探测 Windows 标准 Chrome/Edge 路径，也可用 `BOSS_BROWSER_EXECUTABLE_PATH` 指定 exe 绝对路径。
- 当前页必须是 `https://www.zhipin.com`；站外、非 HTTPS、已关闭页面均拒绝解析。
- 验证页、登录提示页、无职位卡片页面返回错误，不伪造职位，不自动重试。
- 单次最多解析 20 个卡片，只读取卡片 outerHTML；不返回原始整页 HTML、Cookie、localStorage 或账号信息。
- 职位正文仍是不可信第三方数据，不能作为 Agent 指令执行。

## 4. Web API

### `POST /api/browser/boss/intent`

请求：`{"text":"帮我搜集杭州 Java 后端岗位数据，最多 10 个"}`。

配置 DeepSeek 时通过 JSON 模式提取意图；未配置时使用本地规则。响应包含 `query`、`city`、`maxItems`、`mode`、`requiresManualSearch=true` 和人工操作 `steps`。该接口不打开浏览器、不访问 BOSS。

### `POST /api/browser/boss/open`

请求：`{"jobsPage":true,"headless":false}`。已有 BOSS 页面时保留当前页或切换程序读取目标到现有 BOSS 标签，不重新导航；只有真正打开无 query 的首页或基础职位页前才检查 robots.txt。没有 BOSS 标签时不覆盖站外页面。

### `GET /api/browser/boss/status`

返回运行状态、模式、浏览器渠道、当前 URL/标题、是否在 BOSS 域名及是否需要人工操作，不返回任何凭据。读取前重新识别活动标签，优先可见 BOSS 标签；不刷新网页。

### `POST /api/browser/boss/collect`

请求：`{"city":"杭州","maxItems":10,"confirmedCurrentPage":true}`。确认字段必须为 true；采集前重新识别活动标签，最多等待卡片出现 8 秒。成功后解析、去重并写入本地 H2，`mode=BOSS_BROWSER_CURRENT_PAGE`。持续导航返回 `PAGE_NOT_READY`，未识别卡片时提示核对搜索标签或改用离线 HTML。

### `POST /api/browser/boss/close`

关闭浏览器上下文和 Playwright 驱动，不删除用户数据目录。

## 5. MCP STDIO

同一个 fat JAR 增加 `--mcp-stdio` 参数即进入 MCP 模式，不启动 Spring Boot Web 服务：

```bash
java -Dfile.encoding=UTF-8 -jar target/ai-job-search-cn.jar --mcp-stdio
```

协议版本为 `2024-11-05`，stdin/stdout 使用逐行 JSON-RPC。stdout 只输出协议消息；诊断信息只能写 stderr。

工具：

| 工具 | 作用 | 关键参数 |
|---|---|---|
| `boss_browser_start` | 启动持久化浏览器 | `headless` 可选 |
| `boss_open_jobs_page` | 幂等打开基础页；真正导航前检查 robots | `jobsPage` 可选 |
| `boss_browser_status` | 同步当前活动标签状态，不刷新网页 | 无 |
| `boss_collect_current_page` | 确认后读取当前活动 BOSS 标签职位卡片 | `userConfirmed:true` 必需；`city`、`maxItems<=20`、`importToApp` 可选 |
| `boss_browser_close` | 关闭浏览器 | 无 |

`importToApp` 默认 false。为 true 时，MCP 进程将已经确认的当前页卡片 HTML 发送到 `JOB_SEARCH_API_BASE`（默认 `http://127.0.0.1:8080`）的既有 `/api/crawler/boss/import`，因此 Web 应用需要另行启动。

Claude Desktop / Cursor 配置示例见 `mcp/`。示例中的 JAR 绝对路径必须替换为本机实际路径；不要把密钥或 Cookie 写进 MCP 配置。
