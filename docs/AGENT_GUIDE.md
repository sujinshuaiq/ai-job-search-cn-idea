# Spring AI 求职 Agent 使用指南

## 1. 当前实现

本工程使用 **Spring AI 1.1.8**，不是手写提示词后假装 Agent：

- `ChatClient` 调用 DeepSeek OpenAI 兼容接口。
- `@Tool` 让模型按目标选择受限 Java 工具。
- `MessageChatMemoryAdvisor` + JDBC ChatMemory 保存同一对话最近 24 条消息。
- `agent_run`、`agent_step`、`agent_confirmation` 保存任务、真实工具轨迹和人工确认单。
- `ThreadPoolTaskExecutor` 在后台执行模型和工具调用；核心线程 2、最大线程 4、队列容量 32，不为每次请求无限创建线程。

业务权限、工具预算和确认单仍由确定性 Java 代码控制，模型不能执行任意代码。

## 2. IDEA 启动与 DeepSeek 配置

1. 停止旧版 `JobSearchApplication`，避免旧进程继续占用 8080。
2. 用 IDEA 打开新工程根目录 `pom.xml`，重新加载 Maven，确认 JDK / Language level 均为 17。
3. 在 Run Configuration 的环境变量中配置：

   ```text
   DEEPSEEK_API_KEY=sk-替换为你的密钥
   DEEPSEEK_BASE_URL=https://api.deepseek.com
   DEEPSEEK_MODEL=deepseek-flash
   ```

4. 运行 `cn.jobsearch.JobSearchApplication`，访问 <http://localhost:8080>，从左侧点击 **求职 Agent**。
5. 如果仍看到旧“同步请求”文案，按 **Ctrl+F5**。本版静态 CSS / JS 已带版本参数。

密钥只从环境变量读取。`local-demo-only` 只是无密钥时完成 Spring AI 自动配置的占位值，不会被业务层当成真实密钥。模型名是否可用于你的账号以 DeepSeek 控制台和真实响应为准。

## 3. 推荐任务示例

### 查询已有职位

```text
查询职位库里杭州的 Java 后端岗位，按匹配分排序，告诉我最匹配的 3 个及理由
```

模型可调用 `search_saved_jobs`，必要时再读取详情，不需要打开浏览器。

### 分析明确职位

```text
分析职位 ID 1 和我档案的匹配度，列出优势与缺口
```

模型可调用 `analyze_saved_job`，分析结果保存到原职位记录。

### 生成定向材料

```text
根据我的真实求职档案，为职位 ID 1 生成定向简历和初次沟通话术
```

模型可调用 `generate_job_materials`。结果必须人工核对，系统不会自动投递或发送话术。

### 补充新职位

默认建议先在“BOSS 职位采集 → 离线 HTML 导入”或“手动录入”中补充，再让 Agent 查询。也可以直接问：

```text
我想补充杭州 Java 后端职位，请说明如何使用离线 HTML 导入，不要连接 BOSS 网站
```

默认模式下系统提示明确为离线模式，并且不把两个 BOSS 浏览器工具注册给模型。

## 4. 后台执行与页面操作

配置真实模型后，一次任务的流程为：

1. `POST /api/agent/runs` 保存目标和 `RUNNING` 状态，并立即返回任务快照。
2. 有界后台执行器调用模型和工具，Tomcat 请求不再一直等待模型结束。
3. 前端每约 1.2 秒 `GET /api/agent/runs/{runId}`，只读取本机任务记录，不刷新 BOSS。
4. 进入 `COMPLETED` 或 `FAILED` 后停止轮询并刷新职位统计。

无密钥本地模式仍同步返回 `COMPLETED / LOCAL_DEMO`，明确说明未调用模型或工具，不进行无意义的后台等待。

状态含义：

- `RUNNING`：后台模型或工具正在执行。
- `WAITING_USER_ACTION`：仅显式开启 BOSS 浏览器功能后可能出现，等待人工确认。
- `COMPLETED`：任务完成，以步骤和实际数量为准。
- `FAILED`：模型、工具或后台队列失败，展示真实原因。
- `CANCELLED`：用户拒绝等待中的确认，或取消允许取消的任务。

后台执行期间不支持假装“已取消”：服务端返回 `AGENT_STILL_RUNNING`，前端也不显示取消按钮。等待人工确认时仍可拒绝/取消。点击“新对话”只清理当前页面指针和轮询，不会强制中断已经提交给模型的 HTTP 请求。

## 5. 工具与权限

默认注册 6 个工具：

| 工具 | 权限 | 说明 |
|---|---|---|
| `search_saved_jobs` | 只读 | 查询本机职位库，最多返回 10 条 |
| `get_saved_job` | 只读 | 按数据库 ID 读取职位详情 |
| `analyze_saved_job` | 写分析结果 | 只有用户要求分析时才调用 |
| `generate_job_materials` | 写生成材料 | 只有用户明确要求时才调用 |
| `get_candidate_profile` | 只读 | 读取档案，不修改档案 |
| `list_job_applications` | 只读 | 读取投递记录，不修改状态 |

只有 `BOSS_BROWSER_ENABLED=true` 时才额外注册：

- `open_boss_for_manual_search`：打开/复用基础页并创建确认单，不直接采集。
- `get_boss_browser_status`：读取活动标签状态，不刷新网页、不读 Cookie。

单任务默认最多 8 次工具调用，可用 `AGENT_MAX_TOOL_CALLS` 调整，代码限制 1–20。BOSS 当前页采集从未暴露为 `@Tool`；开启浏览器功能后也必须由用户提交匹配、未过期、一次性确认单。

## 6. 记忆、隐私与安全边界

- 同一 `conversationId` 使用 JDBC ChatMemory，默认最多 24 条，可通过 `AGENT_HISTORY_MESSAGES` 调整。
- 任务、步骤、职位、档案和记忆保存在本机 H2。
- 配置 DeepSeek 后，目标、对话记忆、必要工具结果及档案内容可能发送给 DeepSeek。不要输入 API Key、Cookie、身份证明或无关敏感信息。
- 职位正文和工具结果都是不可信数据，不能成为新的系统指令。
- 不自动投递、不自动发送消息，不虚构候选人经历。
- 本项目是单用户本机应用，不应直接暴露公网。

## 7. Agent API

- `POST /api/agent/runs`：创建任务，字段 `goal`、可选 `conversationId`。
- `GET /api/agent/runs/{runId}`：读取任务和步骤。
- `POST /api/agent/runs/{runId}/confirm`：提交 `{confirmationId, approved}`，仅用于显式开启后的 BOSS 确认单。
- `POST /api/agent/runs/{runId}/cancel`：取消等待确认的任务；运行中返回 `AGENT_STILL_RUNNING`。

详细请求响应见 [API.md](API.md)。

## 8. 排错

- **点击 Agent 没反应**：确认启动的是新工程；停止旧进程、Maven Reload、重启并 Ctrl+F5。真实 Chrome 回归已覆盖“首页 → 点击左侧 Agent → 提交任务”。
- **点击开始后按钮像卡住**：本版应只等待很短的任务创建请求，随后自动轮询。若仍显示旧同步文案，说明浏览器或进程仍是旧版。
- **一直本地演示**：当前 Run Configuration 没有真实 `DEEPSEEK_API_KEY`，或修改后未重启。
- **401/403**：检查密钥和模型权限；不要从前端、数据库或日志中寻找密钥。
- **一直执行中后失败**：查看任务响应和执行轨迹；模型超时、限流或工具错误会记录为 `FAILED`，不会伪造完成。
- **后台队列已满**：任务会立即标记 `FAILED` 且未调用模型，稍后重试，不会永远停在 RUNNING。
- **要求搜集 BOSS 但没有打开网站**：这是默认离线行为，不是按钮失效。使用离线 HTML 或手动录入。
- **实网验收**：本机模拟 OpenAI 测试验证协议、工具链和后台状态，不等于你的密钥、`deepseek-flash` 权限或真实模型质量已经验收。
