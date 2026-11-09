"use strict";

const state = { jobs: [], applications: [], currentJob: null, ai: null, view: "dashboard" };
const viewTitles = { dashboard: "求职仪表盘", jobs: "职位库", crawler: "BOSS 职位采集", applications: "投递管理", profile: "我的求职档案" };
const statusLabels = { SAVED: "已收藏", APPLIED: "已投递", CHATTING: "沟通中", INTERVIEWING: "面试中", OFFER: "Offer", REJECTED: "已拒绝", CLOSED: "已结束" };
let pendingRequests = 0;

const $ = id => document.getElementById(id);
const esc = value => String(value ?? "").replace(/[&<>'"]/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" }[char]));

function progressStart() { pendingRequests++; $("top-progress").hidden = false; }
function progressEnd() { pendingRequests = Math.max(0, pendingRequests - 1); if (!pendingRequests) $("top-progress").hidden = true; }

async function api(url, options = {}) {
  progressStart();
  try {
    const config = { ...options, headers: { ...(options.body ? { "Content-Type": "application/json" } : {}), ...(options.headers || {}) } };
    const response = await fetch(url, config);
    if (response.status === 204) return null;
    const text = await response.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch { data = { message: text }; }
    }
    if (!response.ok) {
      const error = new Error(data?.message || `请求失败（HTTP ${response.status}）`);
      error.code = data?.code || "HTTP_ERROR";
      error.status = response.status;
      throw error;
    }
    return data;
  } finally { progressEnd(); }
}

function toast(message, type = "success", duration = 3600) {
  const item = document.createElement("div");
  item.className = `toast ${type}`;
  item.textContent = message;
  $("toast-region").appendChild(item);
  setTimeout(() => item.remove(), duration);
}

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(String(value).replace(" ", "T"));
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" }) + " " + date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
}

function sourceLabel(source) { return ({ BOSS: "BOSS", DEMO: "演示", MANUAL: "手动" })[source] || source || "其他"; }
function scoreHtml(score) {
  if (score === null || score === undefined) return '<span class="unscored">尚未分析</span>';
  const cls = score >= 80 ? "score-high" : score >= 60 ? "score-mid" : "score-low";
  return `<span class="score-badge ${cls}">${esc(score)} 分</span>`;
}

function safeUrl(value) {
  if (!value) return "";
  try {
    const url = new URL(value);
    return ["https:", "http:"].includes(url.protocol) ? url.href : "";
  } catch { return ""; }
}

function bindEvents() {
  document.querySelectorAll("[data-view]").forEach(button => button.addEventListener("click", () => showView(button.dataset.view)));
  document.querySelectorAll("[data-action='manual-job']").forEach(button => button.addEventListener("click", openManualEntry));
  $("top-add-job").addEventListener("click", openManualEntry);
  $("job-filter").addEventListener("click", loadJobs);
  $("job-keyword").addEventListener("keydown", event => { if (event.key === "Enter") loadJobs(); });
  $("job-city").addEventListener("change", loadJobs);
  $("job-source").addEventListener("change", loadJobs);
  $("job-analyzed").addEventListener("change", loadJobs);
  $("jobs-list").addEventListener("click", event => {
    const open = event.target.closest("[data-open-job]");
    const analyze = event.target.closest("[data-analyze-job]");
    if (open) openJob(Number(open.dataset.openJob));
    if (analyze) quickAnalyze(Number(analyze.dataset.analyzeJob), analyze);
  });
  document.querySelectorAll("[data-close-modal]").forEach(element => element.addEventListener("click", closeModal));
  $("job-modal-body").addEventListener("click", handleModalAction);
  document.addEventListener("keydown", event => { if (event.key === "Escape" && !$("job-modal").hidden) closeModal(); });
  document.querySelectorAll("[data-crawler-tab]").forEach(button => button.addEventListener("click", () => showCrawlerTab(button.dataset.crawlerTab)));
  $("boss-browser-form").addEventListener("submit", openBossBrowser);
  $("boss-browser-refresh").addEventListener("click", () => refreshBossBrowser(true));
  $("boss-browser-close").addEventListener("click", closeBossBrowser);
  $("boss-collect-current").addEventListener("click", collectBossCurrentPage);
  $("copy-mcp-command").addEventListener("click", event => copyElement("mcp-command", event.target));
  $("boss-online-form").addEventListener("submit", searchBoss);
  $("boss-import-form").addEventListener("submit", importBossHtml);
  $("manual-job-form").addEventListener("submit", saveManualJob);
  $("load-boss-demo").addEventListener("click", loadBossDemo);
  $("boss-html-file").addEventListener("change", readBossFile);
  $("boss-online-form").elements.city.addEventListener("change", event => {
    $("boss-online-form").elements.cityCode.value = event.target.value;
  });
  $("applications-list").addEventListener("click", handleApplicationAction);
  $("profile-form").addEventListener("submit", saveProfile);
  $("mobile-menu").addEventListener("click", () => { $("sidebar").classList.add("open"); $("sidebar-backdrop").hidden = false; });
  $("sidebar-backdrop").addEventListener("click", closeMobileMenu);
}

function closeMobileMenu() { $("sidebar").classList.remove("open"); $("sidebar-backdrop").hidden = true; }

async function showView(view) {
  if (!viewTitles[view]) return;
  state.view = view;
  document.querySelectorAll(".view").forEach(section => { section.hidden = section.id !== `view-${view}`; section.classList.toggle("active", section.id === `view-${view}`); });
  document.querySelectorAll(".nav-item").forEach(button => button.classList.toggle("active", button.dataset.view === view));
  $("page-title").textContent = viewTitles[view];
  closeMobileMenu();
  window.scrollTo({ top: 0, behavior: "smooth" });
  try {
    if (view === "dashboard") await loadDashboard();
    if (view === "jobs") await loadJobs();
    if (view === "applications") await loadApplications();
    if (view === "profile") await loadProfile();
    if (view === "crawler") await refreshBossBrowser(false);
  } catch (error) { toast(error.message, "error"); }
}

function openManualEntry() { showView("crawler").then(() => showCrawlerTab("manual")); }

function showCrawlerTab(tab) {
  document.querySelectorAll("[data-crawler-tab]").forEach(button => button.classList.toggle("active", button.dataset.crawlerTab === tab));
  ["browser", "online", "offline", "manual"].forEach(name => { $(`crawler-${name}`).hidden = name !== tab; });
  $("crawler-result").hidden = true;
  if (tab === "browser") refreshBossBrowser(false);
}

async function loadAiStatus() {
  try {
    state.ai = await api("/api/ai/status");
    $("ai-mode").textContent = state.ai.configured ? "DeepSeek 已就绪" : "本地演示模式";
    $("ai-model").textContent = state.ai.configured ? state.ai.model : "未配置密钥 · 使用规则与模板";
    $("ai-dot").classList.toggle("online", state.ai.configured);
  } catch (error) {
    $("ai-mode").textContent = "模型状态未知";
    $("ai-model").textContent = error.message;
  }
}

async function loadDashboard() {
  const data = await api("/api/dashboard");
  $("stat-jobs").textContent = data.totalJobs;
  $("stat-analyzed").textContent = data.analyzedJobs;
  $("stat-high").textContent = data.highMatchJobs;
  $("stat-applications").textContent = data.applications;
  $("stat-interviews").textContent = data.interviews;
  $("stat-offers").textContent = data.offers;
  $("nav-job-count").textContent = data.totalJobs;
  $("nav-application-count").textContent = data.applications;
  renderDashboardTopJobs(data.topJobs || []);
  renderDashboardRecent(data.recentApplications || []);
}

function renderDashboardTopJobs(jobs) {
  if (!jobs.length) {
    $("dashboard-top-jobs").innerHTML = '<div class="empty-state compact"><div class="empty-icon">◎</div><strong>还没有匹配榜单</strong><p>到职位库点击“分析”，生成你的第一份匹配报告。</p><button class="text-button" data-view="jobs">去分析职位 →</button></div>';
    $("dashboard-top-jobs").querySelector("[data-view]").addEventListener("click", () => showView("jobs"));
    return;
  }
  $("dashboard-top-jobs").innerHTML = jobs.map((job, index) => `<div class="rank-item" data-dashboard-job="${job.id}" role="button" tabindex="0">
    <span class="rank-number ${index < 3 ? "top" : ""}">${String(index + 1).padStart(2, "0")}</span>
    <div class="rank-main"><strong>${esc(job.title)}</strong><small>${esc(job.company)} · ${esc(job.city || "城市待定")} · ${esc(job.salary || "薪资面议")}</small></div>
    <span class="rank-score">${esc(job.matchScore)}%</span></div>`).join("");
  $("dashboard-top-jobs").querySelectorAll("[data-dashboard-job]").forEach(item => {
    const open = () => openJob(Number(item.dataset.dashboardJob));
    item.addEventListener("click", open);
    item.addEventListener("keydown", event => { if (event.key === "Enter") open(); });
  });
}

function renderDashboardRecent(applications) {
  if (!applications.length) {
    $("dashboard-recent").innerHTML = '<div class="empty-state compact"><div class="empty-icon">✓</div><strong>从收藏一个机会开始</strong><p>职位详情中可加入投递管理。</p></div>';
    return;
  }
  $("dashboard-recent").innerHTML = applications.map(app => `<div class="activity-item"><span class="activity-dot"></span><div class="activity-main"><strong>${esc(app.jobTitle)}</strong><p>${esc(app.company)} · ${esc(app.note || "暂无备注")}</p><small>${esc(formatDate(app.updatedAt))}</small></div><span class="status-pill">${esc(app.statusLabel)}</span></div>`).join("");
}

async function loadJobs() {
  const params = new URLSearchParams();
  const keyword = $("job-keyword").value.trim();
  const city = $("job-city").value;
  const source = $("job-source").value;
  const analyzed = $("job-analyzed").value;
  if (keyword) params.set("keyword", keyword);
  if (city) params.set("city", city);
  if (source) params.set("source", source);
  if (analyzed) params.set("analyzed", analyzed);
  try {
    state.jobs = await api(`/api/jobs?${params}`);
    renderJobs();
  } catch (error) { toast(error.message, "error"); }
}

function renderJobs() {
  $("job-result-count").textContent = `${state.jobs.length} 个职位`;
  $("nav-job-count").textContent = state.jobs.length;
  if (!state.jobs.length) {
    $("jobs-list").innerHTML = '<div class="empty-state"><div class="empty-icon">⌕</div><strong>没有符合条件的职位</strong><p>调整筛选条件，或前往 BOSS 采集页导入职位。</p></div>';
    return;
  }
  $("jobs-list").innerHTML = state.jobs.map(job => `<div class="job-row">
    <button class="job-title-button" data-open-job="${job.id}"><strong>${esc(job.title)}</strong><small>${esc(job.company)}${job.tags ? " · " + esc(job.tags) : ""}</small></button>
    <span class="job-cell city-cell">${esc(job.city || "—")}</span>
    <span class="job-cell salary-cell salary-text">${esc(job.salary || "面议")}</span>
    <span class="job-cell source-cell"><span class="source-badge ${esc(job.source)}">${esc(sourceLabel(job.source))}</span></span>
    <span class="job-cell score-cell">${scoreHtml(job.matchScore)}</span>
    <div class="row-actions"><button class="mini-button" data-open-job="${job.id}">详情</button><button class="mini-button" data-analyze-job="${job.id}">${job.matchScore == null ? "分析" : "重析"}</button></div>
  </div>`).join("");
}

async function quickAnalyze(id, button) {
  const original = button.textContent;
  button.disabled = true; button.textContent = "分析中";
  try {
    const result = await api(`/api/agent/jobs/${id}/analyze`, { method: "POST" });
    toast(`${result.mode === "DEEPSEEK" ? "DeepSeek" : "本地规则"}分析完成：${result.matchScore} 分`);
    await Promise.all([loadJobs(), loadDashboard()]);
  } catch (error) { toast(error.message, "error", 5000); }
  finally { button.disabled = false; button.textContent = original; }
}

async function openJob(id) {
  $("job-modal").hidden = false;
  document.body.style.overflow = "hidden";
  $("job-modal-body").innerHTML = '<div class="empty-state">正在读取职位详情…</div>';
  try {
    state.currentJob = await api(`/api/jobs/${id}`);
    renderJobModal();
  } catch (error) {
    $("job-modal-body").innerHTML = `<div class="empty-state"><strong>加载失败</strong><p>${esc(error.message)}</p></div>`;
  }
}

function closeModal() { $("job-modal").hidden = true; document.body.style.overflow = ""; state.currentJob = null; }

function renderJobModal() {
  const job = state.currentJob;
  if (!job) return;
  const url = safeUrl(job.sourceUrl);
  const analysis = job.matchScore == null ? `<div class="detail-section ai full"><h3>Agent 匹配分析</h3><p>尚未分析。点击上方“匹配分析”，当前模式：${esc(state.ai?.configured ? "DeepSeek" : "本地规则演示")}。</p></div>` : `<div class="detail-section ai full"><h3>Agent 匹配分析 <span class="mode-label">${esc(job.analysisMode || "UNKNOWN")}</span></h3><div class="analysis-score"><div class="score-ring" style="--score:${Number(job.matchScore)}"><strong>${esc(job.matchScore)}</strong></div><div class="analysis-summary">${esc(job.matchSummary || "暂无摘要")}</div></div></div>
    <div class="detail-section"><h3>匹配优势</h3><div class="text-content">${esc(job.strengths || "暂无")}</div></div>
    <div class="detail-section"><h3>缺口与待确认项</h3><div class="text-content">${esc(job.gaps || "暂无")}</div></div>`;
  const materials = `${job.tailoredResume ? `<div class="detail-section full"><h3>定向简历 <button class="copy-button" data-copy-target="resume-content">复制</button></h3><div class="text-content" id="resume-content">${esc(job.tailoredResume)}</div></div>` : ""}
    ${job.greeting ? `<div class="detail-section full"><h3>BOSS 初次沟通话术 <button class="copy-button" data-copy-target="greeting-content">复制</button></h3><div class="text-content" id="greeting-content">${esc(job.greeting)}</div></div>` : ""}
    ${job.interviewPrep ? `<div class="detail-section full"><h3>面试准备题</h3><div class="text-content">${esc(job.interviewPrep)}</div></div>` : ""}`;
  $("job-modal-body").innerHTML = `<div class="job-modal-header"><span class="source-badge ${esc(job.source)}">${esc(sourceLabel(job.source))}</span><h2 id="modal-job-title">${esc(job.title)}</h2><div class="company">${esc(job.company)}</div><div class="job-meta"><span>📍 ${esc(job.city || "城市待定")}${job.district ? " · " + esc(job.district) : ""}</span><span>薪资 ${esc(job.salary || "面议")}</span><span>经验 ${esc(job.experienceRequirement || "未注明")}</span><span>学历 ${esc(job.educationRequirement || "未注明")}</span>${url ? `<a href="${esc(url)}" target="_blank" rel="noopener noreferrer"><span>原始职位 ↗</span></a>` : ""}</div></div>
    <div class="agent-actions"><button class="button button-primary" data-modal-action="analyze">${job.matchScore == null ? "开始匹配分析" : "重新匹配分析"}</button><button class="button button-secondary" data-modal-action="materials">生成定向材料</button><button class="button button-secondary" data-modal-action="save">☆ 加入投递管理</button></div>
    <div class="detail-grid"><div class="detail-section full"><h3>职位描述与标签</h3><div class="text-content">${esc(job.description || "暂无描述")}${job.tags ? "\n\n标签：" + esc(job.tags) : ""}</div></div>${analysis}${materials}</div>
    <div class="modal-footer-note">AI 或本地模板都可能遗漏信息。请核对职位真实性、个人事实与薪资细节，系统不会自动投递或发送消息。</div>`;
}

async function handleModalAction(event) {
  const action = event.target.closest("[data-modal-action]");
  const copy = event.target.closest("[data-copy-target]");
  if (copy) { copyElement(copy.dataset.copyTarget, copy); return; }
  if (!action || !state.currentJob) return;
  const type = action.dataset.modalAction;
  if (type === "save") { await addCurrentApplication(action); return; }
  const original = action.textContent;
  setModalBusy(true, action, type === "analyze" ? "分析中，请稍候…" : "生成中，请稍候…");
  try {
    const result = await api(`/api/agent/jobs/${state.currentJob.id}/${type}`, { method: "POST" });
    state.currentJob = await api(`/api/jobs/${state.currentJob.id}`);
    renderJobModal();
    await Promise.all([loadJobs(), loadDashboard()]);
    const label = result.mode === "DEEPSEEK" ? "DeepSeek" : "本地演示";
    toast(type === "analyze" ? `${label}分析完成：${result.matchScore} 分` : `${label}材料已生成，请人工核对`);
  } catch (error) { toast(error.message, "error", 5500); }
  finally { setModalBusy(false); if (document.body.contains(action)) { action.disabled = false; action.textContent = original; } }
}

function setModalBusy(busy, activeButton, text) {
  document.querySelectorAll("[data-modal-action]").forEach(button => { button.disabled = busy; });
  if (busy && activeButton) activeButton.textContent = text;
}

async function addCurrentApplication(button) {
  button.disabled = true;
  try {
    await api("/api/applications", { method: "POST", body: JSON.stringify({ jobId: state.currentJob.id, status: "SAVED", note: "" }) });
    toast("已加入投递管理，状态为“已收藏”");
    await Promise.all([loadApplications(), loadDashboard()]);
    button.textContent = "✓ 已加入";
  } catch (error) { toast(error.message, "error"); button.disabled = false; }
}

async function copyElement(id, button) {
  const text = $(id)?.textContent || "";
  if (!text) return;
  try {
    if (navigator.clipboard && window.isSecureContext) await navigator.clipboard.writeText(text);
    else {
      const textarea = document.createElement("textarea"); textarea.value = text; textarea.style.position = "fixed"; textarea.style.opacity = "0";
      document.body.appendChild(textarea); textarea.select(); document.execCommand("copy"); textarea.remove();
    }
    const old = button.textContent; button.textContent = "已复制 ✓"; setTimeout(() => { if (document.body.contains(button)) button.textContent = old; }, 1600);
  } catch { toast("复制失败，请手动选择文本", "error"); }
}

async function loadApplications() {
  try {
    state.applications = await api("/api/applications");
    $("nav-application-count").textContent = state.applications.length;
    renderApplications();
  } catch (error) { toast(error.message, "error"); }
}

function renderApplications() {
  if (!state.applications.length) {
    $("applications-list").innerHTML = '<div class="empty-state"><div class="empty-icon">▤</div><strong>还没有投递记录</strong><p>在职位详情中点击“加入投递管理”，开始跟踪机会。</p></div>';
    return;
  }
  $("applications-list").innerHTML = state.applications.map(app => `<div class="application-row" data-app-row="${app.id}">
    <div class="application-job"><strong>${esc(app.jobTitle)}</strong><small>${esc(app.company)} · ${esc(app.city || "—")} · ${esc(app.salary || "面议")}</small></div>
    <select class="app-status" aria-label="投递状态">${Object.entries(statusLabels).map(([value, label]) => `<option value="${value}" ${value === app.status ? "selected" : ""}>${label}</option>`).join("")}</select>
    <input class="app-note" maxlength="10000" value="${esc(app.note || "")}" placeholder="记录 HR 反馈、面试安排等">
    <span class="application-time">${esc(formatDate(app.updatedAt))}</span>
    <div class="application-actions"><button class="mini-button" data-save-app="${app.id}">保存</button><button class="mini-button" data-open-app-job="${app.jobId}">职位</button><button class="mini-button" data-delete-app="${app.id}" aria-label="删除记录">删</button></div>
  </div>`).join("");
}

async function handleApplicationAction(event) {
  const save = event.target.closest("[data-save-app]");
  const remove = event.target.closest("[data-delete-app]");
  const open = event.target.closest("[data-open-app-job]");
  if (open) openJob(Number(open.dataset.openAppJob));
  if (save) {
    const id = Number(save.dataset.saveApp);
    const row = document.querySelector(`[data-app-row='${id}']`);
    save.disabled = true;
    try {
      await api(`/api/applications/${id}`, { method: "PATCH", body: JSON.stringify({ status: row.querySelector(".app-status").value, note: row.querySelector(".app-note").value }) });
      toast("投递进展已更新"); await Promise.all([loadApplications(), loadDashboard()]);
    } catch (error) { toast(error.message, "error"); save.disabled = false; }
  }
  if (remove) {
    const id = Number(remove.dataset.deleteApp);
    if (!window.confirm("确认删除这条投递记录？职位本身会保留。")) return;
    try { await api(`/api/applications/${id}`, { method: "DELETE" }); toast("记录已删除"); await Promise.all([loadApplications(), loadDashboard()]); }
    catch (error) { toast(error.message, "error"); }
  }
}

async function loadProfile() {
  try {
    const profile = await api("/api/profile");
    const form = $("profile-form");
    ["name", "currentTitle", "city", "yearsOfExperience", "expectedSalary", "targetRoles", "skills", "education", "experience", "projectHighlights"].forEach(name => { form.elements[name].value = profile[name] ?? ""; });
  } catch (error) { toast(error.message, "error"); }
}

async function saveProfile(event) {
  event.preventDefault();
  const button = event.target.querySelector("button[type='submit']");
  const data = Object.fromEntries(new FormData(event.target).entries());
  data.yearsOfExperience = Number(data.yearsOfExperience);
  button.disabled = true;
  try { await api("/api/profile", { method: "PUT", body: JSON.stringify(data) }); toast("求职档案已保存，后续生成将使用最新内容"); }
  catch (error) { toast(error.message, "error"); }
  finally { button.disabled = false; }
}

function renderBossBrowserStatus(status) {
  $("boss-browser-state").textContent = status.running ? (status.pageAvailable ? "浏览器正在运行" : "浏览器运行中 · 标签页不可用") : "浏览器尚未启动";
  $("boss-browser-dot").classList.toggle("online", Boolean(status.running && status.pageAvailable));
  $("boss-browser-mode").textContent = status.running ? `${status.channel || "本机浏览器"} · ${status.headless ? "无头" : "有头"}` : "不会自动启动";
  $("boss-browser-message").textContent = status.message || "暂无状态说明";
  $("boss-browser-url").textContent = `当前 URL：${status.currentUrl || "—"}`;
}

async function fillBossIntent(text) {
  const result = await api("/api/browser/boss/intent", { method: "POST", body: JSON.stringify({ text }) });
  $("boss-browser-query").value = result.query;
  const city = $("boss-browser-city");
  if (result.city && !Array.from(city.options).some(option => option.value === result.city)) city.add(new Option(result.city, result.city));
  city.value = result.city || city.value;
  $("boss-browser-max").value = result.maxItems;
  return result;
}

async function openBossBrowser(event) {
  event.preventDefault();
  const form = event.target;
  const submitter = event.submitter || form.querySelector("[data-open-mode='parse']");
  const parseFirst = submitter.dataset.openMode !== "direct";
  const originalLabel = submitter.textContent;
  const buttons = Array.from(form.querySelectorAll("button[type='submit']"));
  buttons.forEach(button => { button.disabled = true; });
  submitter.textContent = parseFirst ? "正在解析并打开…" : "正在打开…";
  try {
    const intent = parseFirst ? await fillBossIntent(form.elements.text.value.trim()) : null;
    const data = Object.fromEntries(new FormData(form).entries());
    const status = await api("/api/browser/boss/open", { method: "POST", body: JSON.stringify({ jobsPage: data.jobsPage === "true", headless: data.headless === "true" }) });
    renderBossBrowserStatus(status);
    const intentText = intent ? `意图：${intent.query} · ${intent.city || "未指定城市"} · ${intent.maxItems} 个（${intent.mode}）\n\n` : "";
    showCrawlerResult("浏览器已就绪", `${intentText}${status.message}\n\n请在真实浏览器中手动登录和搜索。看到职位后直接进入第二步，不要重复点击打开。`);
  } catch (error) {
    showCrawlerResult(error.code || "浏览器打开失败", `${error.message}\n\n不会自动重试、构造搜索 URL 或绕过验证。`, true);
    try { await refreshBossBrowser(false); } catch { /* 状态错误已由原错误表达 */ }
  } finally {
    buttons.forEach(button => { button.disabled = false; });
    submitter.textContent = originalLabel;
  }
}

async function refreshBossBrowser(showResult) {
  try {
    const status = await api("/api/browser/boss/status");
    renderBossBrowserStatus(status);
    if (showResult) showCrawlerResult("浏览器状态已刷新", `${status.message}\n${status.currentUrl || "当前没有可显示的页面 URL"}`);
    return status;
  } catch (error) {
    if (showResult) showCrawlerResult(error.code || "状态读取失败", error.message, true);
    return null;
  }
}

async function closeBossBrowser() {
  const button = $("boss-browser-close");
  button.disabled = true;
  try {
    const status = await api("/api/browser/boss/close", { method: "POST" });
    renderBossBrowserStatus(status);
    showCrawlerResult("浏览器会话已关闭", "本地用户数据目录仍保留，下次可复用你已人工建立的会话。程序未读取或导出 Cookie。 ");
  } catch (error) { showCrawlerResult(error.code || "关闭失败", error.message, true); }
  finally { button.disabled = false; }
}

async function collectBossCurrentPage() {
  const button = $("boss-collect-current");
  button.disabled = true; button.textContent = "正在识别当前标签…";
  try {
    await refreshBossBrowser(false);
    button.textContent = "正在读取并去重…";
    const result = await api("/api/browser/boss/collect", { method: "POST", body: JSON.stringify({
      city: $("boss-browser-city").value,
      maxItems: Number($("boss-browser-max").value),
      confirmedCurrentPage: true
    }) });
    renderBossBrowserStatus(result.browser);
    const imported = result.importResult;
    const warnings = Array.from(new Set([...(result.warnings || []), ...(imported.warnings || [])]));
    const preview = (result.jobs || []).slice(0, 5).map((job, index) => `${index + 1}. ${job.title}｜${job.company}｜${job.city || "城市待确认"}`).join("\n");
    showCrawlerResult("当前页采集完成", `解析 ${result.parsed} 个，新增 ${imported.imported} 个，重复 ${imported.duplicates} 个。\n${warnings.join("\n") || "没有额外警告。"}\n\n${preview || "没有可预览的职位"}${preview ? "\n\n可前往职位库查看全部结果。" : ""}`);
    toast(`当前页处理完成：新增 ${imported.imported}，去重 ${imported.duplicates}`);
    await Promise.all([loadJobs(), loadDashboard()]);
  } catch (error) {
    showCrawlerResult(error.code || "当前页读取失败", `${error.message}\n\n请确认当前标签稳定显示职位列表。若 BOSS 自身持续刷新，请改用普通浏览器保存页面后进行“离线 HTML 导入”，程序不会绕过网站限制。`, true);
  } finally { button.disabled = false; button.textContent = "确认当前页并采集"; }
}

function showCrawlerResult(title, details, isError = false) {
  const box = $("crawler-result");
  box.hidden = false; box.classList.toggle("error", isError);
  box.innerHTML = `<strong>${esc(title)}</strong><div>${esc(details)}</div>`;
  box.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

async function searchBoss(event) {
  event.preventDefault();
  const button = event.target.querySelector("button[type='submit']");
  const data = Object.fromEntries(new FormData(event.target).entries()); data.page = Number(data.page);
  button.disabled = true; button.textContent = "正在检查 robots…";
  try {
    const result = await api("/api/crawler/boss/search", { method: "POST", body: JSON.stringify(data) });
    showCrawlerResult("在线采集完成", `解析 ${result.parsed} 个，新增 ${result.imported} 个，重复 ${result.duplicates} 个。\n${(result.warnings || []).join("\n")}`);
    toast(`成功新增 ${result.imported} 个职位`); await Promise.all([loadJobs(), loadDashboard()]);
  } catch (error) {
    showCrawlerResult(`${error.code || "采集失败"}：未能在线采集`, `${error.message}\n\n不会自动重试或绕过限制。你可以切换到“离线 HTML 导入”，或使用手动录入。`, true);
  } finally { button.disabled = false; button.textContent = "检查权限并开始采集"; }
}

async function importBossHtml(event) {
  event.preventDefault();
  const form = event.target; const button = form.querySelector("button[type='submit']");
  const html = form.elements.html.value.trim();
  if (new Blob([html]).size > 2 * 1024 * 1024) { showCrawlerResult("文件过大", "HTML 不能超过 2 MB，请减少无关内容后重试。", true); return; }
  button.disabled = true; button.textContent = "正在本地解析…";
  try {
    const result = await api("/api/crawler/boss/import", { method: "POST", body: JSON.stringify({ city: form.elements.city.value, html }) });
    showCrawlerResult("HTML 解析入库完成", `有效职位 ${result.parsed} 个，新增 ${result.imported} 个，重复 ${result.duplicates} 个。\n${(result.warnings || []).join("\n") || "没有额外警告。"}`);
    toast(`导入完成：新增 ${result.imported}，去重 ${result.duplicates}`); await Promise.all([loadJobs(), loadDashboard()]);
  } catch (error) { showCrawlerResult(error.code || "解析失败", error.message, true); }
  finally { button.disabled = false; button.textContent = "解析并导入职位"; }
}

async function loadBossDemo() {
  try {
    const response = await fetch("/demo/boss-search.html");
    if (!response.ok) throw new Error("演示 HTML 加载失败");
    $("boss-html").value = await response.text();
    showCrawlerResult("演示 HTML 已载入", "其中包含 3 个明确标识的虚构职位。点击“解析并导入职位”验证完整流程；重复导入会触发去重。 ");
  } catch (error) { toast(error.message, "error"); }
}

async function readBossFile(event) {
  const file = event.target.files[0];
  if (!file) return;
  if (file.size > 2 * 1024 * 1024) { toast("文件不能超过 2 MB", "error"); event.target.value = ""; return; }
  try { $("boss-html").value = await file.text(); toast(`已读取 ${file.name}，内容尚未提交`); }
  catch { toast("文件读取失败，请确认编码为 UTF-8", "error"); }
}

async function saveManualJob(event) {
  event.preventDefault();
  const form = event.target; const button = form.querySelector("button[type='submit']");
  const data = Object.fromEntries(new FormData(form).entries());
  button.disabled = true;
  try {
    const job = await api("/api/jobs/manual", { method: "POST", body: JSON.stringify(data) });
    form.reset(); showCrawlerResult("职位已保存", `已保存“${job.title}”，职位 ID：${job.id}。现在可以打开详情进行匹配分析。`);
    toast("手动职位已录入"); await Promise.all([loadJobs(), loadDashboard()]);
  } catch (error) { showCrawlerResult(error.code || "保存失败", error.message, true); }
  finally { button.disabled = false; }
}

async function init() {
  bindEvents();
  await Promise.allSettled([loadAiStatus(), loadDashboard(), loadJobs(), loadApplications(), loadProfile()]);
  const initialView = new URLSearchParams(window.location.search).get("view");
  if (viewTitles[initialView] && initialView !== "dashboard") await showView(initialView);
}

document.addEventListener("DOMContentLoaded", init);
