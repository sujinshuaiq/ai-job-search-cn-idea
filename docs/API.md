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

时间仅为结构示例。常见状态码：400 参数错误、404 不存在、409 重复记录、429 限流、502 上游调用失败。

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

```json
{
  "query": "Java 后端",
  "city": "杭州",
  "cityCode": "101210100",
  "page": 1
}
```

页码 1–3，每次只请求一页。在线成功时 `mode=BOSS_ONLINE_ROBOTS_CHECKED`。

当前常见失败：

```json
{
  "code": "ROBOTS_DISALLOWED",
  "message": "BOSS 直聘 robots.txt 禁止自动访问该搜索地址…"
}
```

该情况不会请求目标搜索页。不要通过加 Cookie、换代理或识别验证码绕过。

### `POST /api/browser/boss/intent`

```json
{"text":"帮我搜集杭州 Java 后端岗位数据，最多 10 个"}
```

返回 `query`、`city`、`maxItems`、`mode=DEEPSEEK|LOCAL_RULES`、`requiresManualSearch=true` 与 `steps`。只提取意图，不访问 BOSS。

### `POST /api/browser/boss/open`

```json
{"jobsPage":true,"headless":false}
```

启动或复用本机 Chrome/Edge 持久会话。已有 BOSS 页面时保留当前页或切换到现有 BOSS 标签，不重新导航；只有真正导航到无 query 的首页或 `/web/geek/job` 前才检查 robots.txt。当前是站外页面且没有 BOSS 标签时新建标签，不覆盖站外页面。首次人工扫码建议使用有头模式。

### `GET /api/browser/boss/status`

返回 `running`、`pageAvailable`、`headless`、`channel`、`profileDirectory`、`currentUrl`、`currentTitle`、`onBossDomain`、`manualActionRequired`、`message`。读取前重新识别活动标签，优先可见 BOSS 标签；状态同步不会刷新网页。不返回 Cookie 或 localStorage。

### `POST /api/browser/boss/collect`

```json
{"city":"杭州","maxItems":10,"confirmedCurrentPage":true}
```

`confirmedCurrentPage` 必须为 true，`maxItems` 为 1–20。采集前重新识别活动标签，最多等待卡片出现 8 秒，仅提取当前活动 `https://www.zhipin.com` 标签的职位卡片，成功时 `importResult.mode=BOSS_BROWSER_CURRENT_PAGE`。响应包含解析、新增、重复数和职位数据，前端展示前 5 个职位预览。未启动、站外 URL、登录/验证页、持续导航或无卡片均返回真实错误；持续导航可能返回 `PAGE_NOT_READY`。

### `POST /api/browser/boss/close`

关闭浏览器与驱动，保留 `./data/browser-profile`，不删除或导出会话。

### MCP STDIO

```bash
java -Dfile.encoding=UTF-8 -jar target/ai-job-search-cn.jar --mcp-stdio
```

工具：`boss_browser_start`、`boss_open_jobs_page`、`boss_browser_status`、`boss_collect_current_page`、`boss_browser_close`。采集工具要求 `userConfirmed:true`，可选 `importToApp`（默认 false）；为 true 时通过 `JOB_SEARCH_API_BASE` 调用本机 `/api/crawler/boss/import`。完整契约见 `docs/MCP_BROWSER.md`。

## 5. Agent

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
- `GET /js/app.js`：交互。
- `GET /demo/boss-search.html`：虚构 BOSS 离线导入样例。
- `GET /h2-console`：H2 控制台。
