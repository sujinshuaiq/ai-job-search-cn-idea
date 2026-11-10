# BOSS 浏览器协同与 MCP 契约

## 1. 默认离线边界

本工程保留基于 Playwright Java 的本地浏览器协同服务和轻量 MCP STDIO 服务，但从 2026-11-10 修正版开始：

```text
BOSS_BROWSER_ENABLED=false
```

`BrowserAutomationService.start()`、`openBoss()` 和 `readCurrentJobs()` 在任何 Playwright 或 BOSS 网络操作之前统一拒绝，错误码为 `BOSS_BROWSER_DISABLED`。Web、Agent 和 MCP 都经过这个边界，不能通过更换入口绕过。

`status()` 不报错，返回 `running=false` 和默认离线说明；`closeBrowser()` 保留用于清理旧会话。正常 Web 启动、测试启动和 MCP 工具列举都不会启动浏览器。

**默认关闭不是防封号技术，而是默认不执行可能带来账号风险的在线自动操作。** 即使显式开启，模拟人工、持久会话、低频和 robots 检查也不能保证账号安全。

本工程不是外部 `mcp-boss-zp` 的封装，不包含 Go/go-rod 程序。

## 2. 明确开启后的人工协同流程

只有用户理解风险并确认有权操作时，才为 Web 或 MCP 进程设置 `BOSS_BROWSER_ENABLED=true` 并重启。开启后的流程仍为：

1. 启动有头浏览器，用户数据目录默认 `./data/browser-profile`。
2. 自动导航只允许 `https://www.zhipin.com/` 或不带 query 的 `https://www.zhipin.com/web/geek/job`；真正导航前检查 robots.txt。已有 BOSS 页面时重复打开不重新导航。
3. 用户手动扫码、完成验证、输入关键词与城市并搜索。程序不自动填词或构造搜索 URL。
4. 用户激活肉眼可见职位列表的标签；状态工具只读取状态，不刷新网页。
5. 用户点击页面确认，或 MCP 显式传入 `userConfirmed:true`。程序只读取当前活动 BOSS 标签一次，最多 20 个卡片，不自动翻页。
6. Web 去重入库；MCP 默认只返回职位，`importToApp:true` 时才调用本机 Web 的离线 HTML 导入接口。

**不实现：**自动搜索、自动翻页、批量详情追踪、Cookie/localStorage 导出、代理池、设备指纹、stealth、验证码识别、内部 API、自动打招呼或自动投递。

## 3. 浏览器线程与数据边界

- Playwright 创建、导航、状态、DOM 读取和关闭全部提交到 `BrowserAutomationService` 的同一个守护单线程执行器。
- 驱动设置 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`，不自动下载浏览器；支持 Windows Chrome/Edge 路径探测和 `BOSS_BROWSER_EXECUTABLE_PATH`。
- 当前页必须是 `https://www.zhipin.com`；站外、非 HTTPS 或已关闭页面均拒绝解析。
- 登录/验证页、持续导航、无卡片页面返回真实错误，不自动重试、不伪造职位。
- 只提取卡片 outerHTML，不返回 Cookie、localStorage 或账号信息。职位正文仍是不可信第三方数据。
- 有头/无头切换会重启浏览器上下文；无头模式不代表第三方网站允许无头登录。

## 4. Web API 默认响应

### `POST /api/browser/boss/open`

默认返回 HTTP 428：

```json
{
  "code": "BOSS_BROWSER_DISABLED",
  "message": "BOSS 浏览器自动操作默认关闭，未启动浏览器或访问网站…"
}
```

开启后请求 `{"jobsPage":true,"headless":false}`，才进入原有人工协同流程。

### `GET /api/browser/boss/status`

默认 HTTP 200，`running=false`、`pageAvailable=false`，`message` 说明默认离线安全模式。不会启动浏览器或刷新 BOSS。

### `POST /api/browser/boss/collect`

参数校验仍先执行。合法请求 `{"city":"杭州","maxItems":10,"confirmedCurrentPage":true}` 在默认配置下返回 HTTP 428 / `BOSS_BROWSER_DISABLED`，未读取任何页面。

### `POST /api/browser/boss/intent`

只提取 `query`、`city`、`maxItems`，可使用 DeepSeek 或本地规则；不打开浏览器、不访问 BOSS，不受浏览器开关影响。

### `POST /api/browser/boss/close`

关闭旧浏览器上下文和驱动，不删除用户数据目录；默认离线状态下也可调用。

离线 `POST /api/crawler/boss/import` 不受浏览器开关影响。独立在线搜索开关为 `BOSS_ONLINE_ENABLED=false`，默认错误码 `BOSS_ONLINE_DISABLED`。

## 5. MCP STDIO

```bash
java -Dfile.encoding=UTF-8 -jar target/ai-job-search-cn.jar --mcp-stdio
```

协议版本 `2024-11-05`，stdin/stdout 使用逐行 JSON-RPC。stdout 只输出协议消息，诊断信息写 stderr。MCP 与 Web 是独立 JVM；需要可选浏览器功能时，必须在 MCP 进程自己的环境中设置 `BOSS_BROWSER_ENABLED=true`。

工具 Schema 保持 5 个：

| 工具 | 默认关闭时 | 显式开启后的作用 |
|---|---|---|
| `boss_browser_start` | 返回 `BOSS_BROWSER_DISABLED`，不启动 | 启动持久化浏览器 |
| `boss_open_jobs_page` | 返回同一禁用错误，不导航 | 幂等打开基础页，导航前检查 robots |
| `boss_browser_status` | 返回停止状态和离线说明 | 同步活动标签状态，不刷新 |
| `boss_collect_current_page` | 合法确认请求仍返回禁用错误 | 用户确认后读取当前页，最多 20 个 |
| `boss_browser_close` | 可用于清理旧资源 | 关闭浏览器 |

`importToApp` 默认 false。为 true 时，MCP 将已经确认的卡片 HTML 发送到 `JOB_SEARCH_API_BASE`（默认 `http://127.0.0.1:8080`）的 `/api/crawler/boss/import`，Web 应用需另行启动。

Claude Desktop / Cursor 示例见 `mcp/`。JAR 路径必须替换为本机绝对路径，不要把 API Key 或 Cookie 写进配置。
