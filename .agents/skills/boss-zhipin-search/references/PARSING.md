# BOSS HTML 解析维护说明

## 输入边界

只解析用户提供的 HTML 字符串。Jsoup 不执行 JavaScript；解析前移除 `script/style/noscript/template/svg/iframe`。职位详情链接只接受 HTTPS 且主机严格等于 `www.zhipin.com` 或 `zhipin.com`，查询参数会移除。

## 选择器优先级

职位卡片按以下顺序选择，前四组命中一组后不再混用，避免父子卡片重复：

1. `.job-card-wrapper`
2. `.job-card-box`
3. `li.job-card-wrapper`
4. `.job-list-box > li`
5. 以上均未命中时，通过 `a[href*='/job_detail/']` 向上定位 `.job-card-wrapper`、`.job-card-box`、`article` 或 `li`，无容器时才使用链接父元素，并按元素去重。

字段选择器：

| 字段 | 选择器 |
|---|---|
| 职位 | `.job-name`、`.job-title-text`、`span.job-name`、`[class*='job-name']`、`.job-title [class*='name']` |
| 公司 | `.company-name`、`.boss-name`、`.company-info h3`、`[class*='company-name']`、`.company-info a` |
| 地点 | `.job-area`、`.job-location`、`.work-address`、`[class*='job-area']` |
| 薪资 | `.salary`、`.job-salary`、`.salary-range`、`[class*='salary']` |
| 标签 | `.job-limit span`、`.job-card-footer .tag-list li`、`.tag-list li`、`.job-tags span`、`.labels span`、`.welfare-label span` |
| 详情链接 | `a[href*='/job_detail/']` |
| 摘要 | `.job-card-footer`、`.job-description`、`.job-detail`，否则使用卡片可见文本 |

浏览器 DOM 提取同样具有 `/job_detail/` 链接兜底，并过滤互为父子的重复卡片。招聘站可能随时修改 DOM。测试夹具通过不代表线上选择器永远有效；线上结构变化应返回 `NO_JOB_CARDS` 或 `NO_VALID_JOBS`，不得用虚构字段兜底。

## 去重

- 有详情 URL：去除 query、fragment、末尾 `/`，主机小写后计算 SHA-256。
- 无详情 URL：对规范化“公司 + 职位 + 城市”计算 SHA-256。
- `externalKey` 有数据库唯一约束，Service 在写入前检查重复。

## 修改后的验证清单

1. 更新测试夹具并增加至少两个不同职位卡片。
2. 验证脚本文本不会进入描述。
3. 验证站外 URL 和 `javascript:` URL 被丢弃。
4. 验证缺少公司或标题的卡片跳过且产生 warning。
5. 验证第二次导入全部去重。
6. 执行 `mvn test`，不能只做浏览器手工验证。
