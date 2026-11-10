# API 文档

默认 Base URL：`http://localhost:8080`。请求和响应均为 JSON（静态页面除外），字符集 UTF-8。

## 统一错误结构

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请检查以下字段：title、company",
  "timestamp": "2026-01-01 10:00:00"
}
```

时间仅为结构示例。常见状态码：400 参数错误、404 不存在、409 重复记录、428 默认功能未开启或前置条件不足、429 限流、502 上游调用失败。

## 1. 模型状态

### `GET /api/ai/status`

未配置密钥：

```json
{
  "configured": false,
  "mode": "LOCAL_DEMO",
  "provider": "DeepSeek OpenAI 兼容接口",
  "model": "deepseek-flash",
  "baseUrl": "https://api.deepseek.com",
  "message": "未配置密钥，当前使用本地规则与模板，不会发起 AI 请求"
}
```

不返回 API Key。

## 2. 候选人档案

### `GET /api/profile`

返回唯一候选人档案。

### `PUT /api/profile`

```json
{
  "name": "张三",
  "currentTitle": "Java 后端开发工程师",
  "city": "杭州",
  "yearsOfExperience": 4,
  "skills": "Java、Spring Boot、MySQL",
  "targetRoles": "Java 后端、AI 应用开发",
  "expectedSalary": "20-28K",
  "education": "本科 · 计算机科学",
  "experience": "本人真实工作经历…",
  "projectHighlights": "本人真实项目及可核验成果…"
}
```

必填：`name`、`currentTitle`、`city`、`yearsOfExperience`、`experience`、`projectHighlights`。年限范围 0–60。

## 3. 职位

### `GET /api/jobs`

可选查询参数：

| 参数 | 示例 | 说明 |
|---|---|---|
| `keyword` | `Java` | 搜索职位、公司、标签和描述 |
| `city` | `杭州` | 城市包含匹配 |
| `source` | `BOSS` | `DEMO` / `BOSS` / `MANUAL` |
| `analyzed` | `true` | 是否已有匹配分 |

响应为 `JobPosting` 数组，按创建时间倒序。

### `GET /api/jobs/{id}`

返回职位及已保存的分析和材料字段：`matchScore`、`matchSummary`、`strengths`、`gaps`、`tailoredResume`、`greeting`、`interviewPrep`、`analysisMode`。

### `POST /api/jobs/manual`

```json
{
  "title": "Java 后端工程师",
  "company": "某真实公司",
  "city": "杭州",
  "district": "西湖区",
  "salary": "20-30K",
  "experienceRequirement": "3-5年",
  "educationRequirement": "本科",
  "sourceUrl": "https://example.com/job/123",
  "tags": "Java、Spring Boot、Redis",
  "description": "真实岗位职责与任职要求…"
}
```

成功返回 HTTP 201。重复职位返回 400 / `DUPLICATE_JOB`。

## 4. BOSS 采集

### `POST /api/crawler/boss/import`

```json
{
  "city": "杭州",
  "html": "<div class=\"job-card-wrapper\">…</div>"
}
```

HTML 最大 2,000,000 字符，单次最多写入 100 个职位。成功：

```json
{
  "parsed": 3,
  "imported": 3,
  "duplicates": 0,
  "jobIds": [13, 14, 15],
  "warnings": [],
  "mode": "BOSS_OFFLINE_HTML"
}
```

重复导入同一数据时 `imported=0`，`duplicates` 增加。

### `POST /api/crawler/boss/search`

默认 `BOSS_ONLINE_ENABLED=false`。合法请求直接返回 HTTP 428 / `BOSS_ONLINE_DISABLED`，不会构造目标请求、检查 robots 或发起任何网络访问：

```json
{
  "code": "BOSS_ONLINE_DISABLED",
  "message": "BOSS 在线采集默认关闭，未检查 robots、未发起任何网络请求…"
}
```

只有用户明确设置 `BOSS_ONLINE_ENABLED=true` 并重启后，以下请求才进入原流程：

```json
{
  "query": "Java 后端",
  "city": "杭州",
  "cityCode": "101210100",
  "page": 1
}
```

页码 1–3，每次最多一页，先检查 robots；拒绝时返回 `ROBOTS_DISALLOWED` 且不请求目标页。在线成功时 `mode=BOSS_ONLINE_ROBOTS_CHECKED`。开启不代表获得授权或能避免封号，不要通过 Cookie、代理或验证码识别绕过限制。

### `POST /api/browser/boss/intent`

```json
{"text":"帮我搜集杭州 Java 后端岗位数据，最多 10 个"}
```

返回 `query`、`city`、`maxItems`、`mode=DEEPSEEK|LOCAL_RULES`、`requiresManualSearch=true` 与 `steps`。只提取意图，不访问 BOSS，不受浏览器开关影响。

### `POST /api/browser/boss/open`

默认 `BOSS_BROWSER_ENABLED=false`，合法请求返回 HTTP 428 / `BOSS_BROWSER_DISABLED`，不会启动 Playwright、检查 robots 或访问 BOSS。

显式开启后才接受 `{"jobsPage":true,"headless":false}` 并启动或复用本机 Chrome/Edge。已有 BOSS 页面时不重新导航；真正导航到无 query 的基础页前检查 robots。开启不代表能避免账号风控。

### `GET /api/browser/boss/status`

默认 HTTP 200，`running=false`、`pageAvailable=false`，`message` 为默认离线说明，不启动浏览器。响应字段包括 `headless`、`channel`、`profileDirectory`、`currentUrl`、`currentTitle`、`onBossDomain`、`manualActionRequired` 等，不返回 Cookie 或 localStorage。显式开启后，状态读取也不会刷新网页。

### `POST /api/browser/boss/collect`

```json
{"city":"杭州","maxItems":10,"confirmedCurrentPage":true}
```

参数校验仍先执行；合法请求在默认配置下返回 HTTP 428 / `BOSS_BROWSER_DISABLED`，未读取任何页面。显式开启后，`confirmedCurrentPage` 必须为 true，`maxItems` 为 1–20，只读取当前活动 BOSS 标签一次。成功时 `importResult.mode=BOSS_BROWSER_CURRENT_PAGE`；持续导航、验证页或无卡片返回真实错误。

### `POST /api/browser/boss/close`

关闭旧浏览器与驱动，保留 `./data/browser-profile`，不删除或导出会话；默认离线状态下也可调用。

### MCP STDIO

```bash
java -Dfile.encoding=UTF-8 -jar target/ai-job-search-cn.jar --mcp-stdio
```

保留 `boss_browser_start`、`boss_open_jobs_page`、`boss_browser_status`、`boss_collect_current_page`、`boss_browser_close` 5 个工具。默认 start/open/collect 返回 `BOSS_BROWSER_DISABLED`，status 返回离线说明，close 可清理旧资源。MCP 是独立 JVM，可选功能需在 MCP 自己的环境中显式开启。`importToApp` 默认 false；为 true 时通过 `JOB_SEARCH_API_BASE` 调用本机离线导入 API。完整契约见 `docs/MCP_BROWSER.md`。

## 5. Agent

### `POST /api/agent/runs`

开始一个 Spring AI 对话任务。`goal` 必填且最多 2000 字；`conversationId` 可省略，省略时生成 UUID。传入同一会话 ID 可复用最多 24 条 JDBC 对话记忆。

```json
{
  "goal": "查询职位库里杭州的 Java 后端岗位",
  "conversationId": "可选，上一次任务返回的会话 ID"
}
```

无密钥时不会调用模型或工具，仍返回可审计任务：

```json
{
  "id": 1,
  "conversationId": "7ab1…",
  "goal": "查询职位库里杭州的 Java 后端岗位",
  "status": "COMPLETED",
  "mode": "LOCAL_DEMO",
  "response": "当前为本地演示模式，未调用大模型或工具…",
  "steps": [
    {"id": 1, "toolName": "user_goal", "summary": "查询职位库里杭州的 Java 后端岗位", "status": "SUCCESS", "durationMs": 0},
    {"id": 2, "toolName": "local_fallback", "summary": "未配置模型，返回明确的本地演示说明", "status": "SUCCESS", "durationMs": 0}
  ]
}
```

配置模型后，创建接口先返回 `RUNNING` 快照，模型和工具在有界后台执行器中运行。前端每约 1.2 秒调用 GET，最终状态为 `COMPLETED` 或 `FAILED`；后台队列已满会立即落库为 `FAILED` 且未调用模型。只有显式开启 BOSS 浏览器功能后，相关任务才可能进入 `WAITING_USER_ACTION` 并返回确认单字段。`steps` 是实际数据库记录，不是模型自述。

### `GET /api/agent/runs/{runId}`

返回同一任务结构，不触发模型、工具或浏览器刷新，供前端轮询和任务恢复。

### `POST /api/agent/runs/{runId}/confirm`

```json
{"confirmationId": 1, "approved": true}
```

仅用于显式开启 BOSS 浏览器功能后的等待任务。确认单必须与 runId、当前 pending 动作匹配且未过期、未使用。`approved=true` 才确定性读取当前 BOSS 页一次；`false` 取消且不读取。常见错误：`CONFIRMATION_MISMATCH`、`CONFIRMATION_ALREADY_RESOLVED`、`CONFIRMATION_EXPIRED`、`AGENT_NOT_WAITING_CONFIRMATION`。

### `POST /api/agent/runs/{runId}/cancel`

无请求体。有待处理确认单时按拒绝处理，否则可将非运行任务标记为 `CANCELLED`。`RUNNING` 返回 400 / `AGENT_STILL_RUNNING`，不假装已经中断模型或工具。不会自动关闭 BOSS 浏览器。

### `POST /api/agent/jobs/{id}/analyze`

无请求体。成功：

```json
{
  "jobId": 1,
  "mode": "LOCAL_DEMO",
  "model": "local-rules-v1",
  "matchScore": 82,
  "summary": "本地规则…不是 DeepSeek AI 分析。",
  "strengths": ["已匹配技能：Java、Spring Boot"],
  "gaps": ["职位提及但档案未体现：Kafka…"],
  "analyzedAt": "2026-01-01 10:00:00"
}
```

配置真实密钥并成功调用时 `mode=DEEPSEEK`，`model` 为环境变量中的模型名。

### `POST /api/agent/jobs/{id}/materials`

无请求体。成功：

```json
{
  "jobId": 1,
  "mode": "LOCAL_DEMO",
  "model": "local-template-v1",
  "tailoredResume": "# 张三｜应聘 Java 后端工程师…",
  "greeting": "您好，我有 4 年…",
  "interviewQuestions": ["请结合一个真实项目…"]
}
```

结果同时保存到职位记录，之后可通过 `GET /api/jobs/{id}` 读取。

## 6. 投递管理

### `GET /api/applications`

按更新时间倒序返回：

```json
[
  {
    "id": 1,
    "jobId": 1,
    "jobTitle": "Java 后端工程师",
    "company": "某公司",
    "city": "杭州",
    "salary": "20-30K",
    "status": "APPLIED",
    "statusLabel": "已投递",
    "note": "等待 HR 反馈",
    "appliedAt": "2026-01-01 10:00:00",
    "updatedAt": "2026-01-01 10:00:00"
  }
]
```

### `POST /api/applications`

```json
{"jobId": 1, "status": "SAVED", "note": "准备投递"}
```

状态枚举：`SAVED`、`APPLIED`、`CHATTING`、`INTERVIEWING`、`OFFER`、`REJECTED`、`CLOSED`。省略状态默认 `SAVED`。每个职位最多一条记录，重复返回 HTTP 409 / `APPLICATION_EXISTS`。

### `PATCH /api/applications/{id}`

```json
{"status": "INTERVIEWING", "note": "周五 14:00 一面"}
```

### `DELETE /api/applications/{id}`

成功 HTTP 204，只删除投递记录，保留职位。

## 7. 仪表盘

### `GET /api/dashboard`

```json
{
  "totalJobs": 12,
  "analyzedJobs": 3,
  "highMatchJobs": 2,
  "applications": 2,
  "interviews": 1,
  "offers": 0,
  "topJobs": [
    {"id": 1, "title": "AI 应用开发工程师", "company": "演示公司", "city": "杭州", "salary": "22-30K", "matchScore": 90}
  ],
  "recentApplications": []
}
```

## 8. 静态资源

- `GET /` / `GET /index.html`：中文单页应用。
- `GET /css/app.css`：样式。
- `GET /js/app.js`：固定工作台交互。
- `GET /js/agent.js`：对话任务、恢复、确认、拒绝和取消交互。
- `GET /demo/boss-search.html`：虚构 BOSS 离线导入样例。
- `GET /h2-console`：H2 控制台。
