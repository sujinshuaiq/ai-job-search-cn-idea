"use strict";

const agentState = { run: null, busy: false, pollTimer: null, polling: false };
const agentStatusLabels = {
  RUNNING: "执行中",
  WAITING_USER_ACTION: "等待人工操作",
  COMPLETED: "已完成",
  FAILED: "执行失败",
  CANCELLED: "已取消"
};
const agentToolLabels = {
  user_goal: "接收目标",
  local_fallback: "本地演示",
  assistant_response: "生成回答",
  agent_execution: "Agent 执行",
  wait_for_user: "等待人工搜索",
  confirm_boss_collection: "确认并采集当前页",
  search_saved_jobs: "查询职位库",
  get_saved_job: "读取职位详情",
  analyze_saved_job: "分析职位匹配度",
  generate_job_materials: "生成求职材料",
  get_candidate_profile: "读取求职档案",
  list_job_applications: "读取投递记录",
  open_boss_for_manual_search: "打开 BOSS 基础页",
  get_boss_browser_status: "读取浏览器状态"
};

function agentStorageGet(key) {
  try { return localStorage.getItem(key); } catch { return null; }
}

function agentStorageSet(key, value) {
  try {
    if (value === null || value === "") localStorage.removeItem(key);
    else localStorage.setItem(key, String(value));
  } catch { /* 隐私模式下仍可在当前页面使用 */ }
}

function bindAgentEvents() {
  $("agent-goal-form").addEventListener("submit", startAgentRun);
  $("agent-new").addEventListener("click", resetAgentConversation);
  $("agent-refresh").addEventListener("click", resumeAgentRun);
  $("agent-cancel").addEventListener("click", cancelAgentRun);
  $("agent-confirm").addEventListener("click", () => confirmAgentRun(true));
  $("agent-reject").addEventListener("click", () => confirmAgentRun(false));
  document.querySelectorAll("[data-agent-example]").forEach(button => button.addEventListener("click", () => {
    $("agent-goal").value = button.dataset.agentExample;
    $("agent-goal").focus();
  }));
  document.querySelectorAll("[data-view='agent']").forEach(button => button.addEventListener("click", resumeAgentRun));
}

function stopAgentPolling() {
  if (agentState.pollTimer !== null) {
    clearTimeout(agentState.pollTimer);
    agentState.pollTimer = null;
  }
}

function scheduleAgentPolling() {
  if (!agentState.run || agentState.run.status !== "RUNNING"
      || agentState.pollTimer !== null || agentState.polling) return;
  const runId = agentState.run.id;
  agentState.pollTimer = setTimeout(() => {
    agentState.pollTimer = null;
    pollAgentRun(runId);
  }, 1200);
}

async function pollAgentRun(runId) {
  if (agentState.polling || agentState.run?.id !== runId || agentState.run.status !== "RUNNING") return;
  agentState.polling = true;
  try {
    const run = await api(`/api/agent/runs/${runId}`);
    if (agentState.run?.id !== runId) return;
    renderAgentRun(run);
    if (run.status !== "RUNNING") {
      if (run.status === "COMPLETED") toast("Agent 后台任务已完成");
      else if (run.status === "FAILED") toast(run.response || "Agent 执行失败", "error", 5500);
      await Promise.allSettled([loadJobs(), loadDashboard()]);
    }
  } catch (error) {
    if (agentState.run?.id !== runId) return;
    if (error.status === 404) {
      agentStorageSet("jobpilot.agent.runId", null);
      renderAgentRun(null);
      toast("原任务已不存在，请重新发起", "error");
    } else {
      $("agent-answer").textContent = `后台任务仍在执行，状态更新暂时失败，将自动重试：${error.message}`;
    }
  } finally {
    agentState.polling = false;
    scheduleAgentPolling();
  }
}

function setAgentBusy(busy) {
  agentState.busy = busy;
  ["agent-start", "agent-refresh", "agent-new", "agent-cancel", "agent-confirm", "agent-reject"].forEach(id => {
    if ($(id)) $(id).disabled = busy;
  });
  $("agent-start").textContent = busy ? "正在创建后台任务…" : "开始执行任务";
}

async function startAgentRun(event) {
  event.preventDefault();
  if (agentState.busy) return;
  if (agentState.run?.status === "RUNNING") {
    toast("当前任务仍在后台执行，请等待完成或点击新对话", "error");
    return;
  }
  const goal = $("agent-goal").value.trim();
  if (!goal) { toast("请先输入求职目标", "error"); return; }
  stopAgentPolling();
  agentState.run = null;
  agentStorageSet("jobpilot.agent.runId", null);
  setAgentBusy(true);
  $("agent-run-card").hidden = false;
  $("agent-run-status").textContent = "执行中";
  $("agent-run-status").className = "agent-status RUNNING";
  $("agent-answer").textContent = "正在创建后台任务，创建后页面会自动更新结果。";
  try {
    const run = await api("/api/agent/runs", { method: "POST", body: JSON.stringify({
      goal,
      conversationId: agentStorageGet("jobpilot.agent.conversationId") || null
    }) });
    renderAgentRun(run);
    if (run.status === "WAITING_USER_ACTION") toast("请按提示在 BOSS 浏览器中手动搜索，再回来确认");
    else if (run.status === "COMPLETED") toast("Agent 任务已完成");
    else if (run.status === "FAILED") toast(run.response || "Agent 执行失败", "error", 5500);
    if (run.status !== "RUNNING") await Promise.allSettled([loadJobs(), loadDashboard()]);
  } catch (error) {
    stopAgentPolling();
    $("agent-answer").textContent = error.message;
    $("agent-run-status").textContent = "请求失败";
    $("agent-run-status").className = "agent-status FAILED";
    toast(error.message, "error", 5500);
  } finally { setAgentBusy(false); }
}

async function resumeAgentRun() {
  if (agentState.busy) return;
  const runId = Number(agentStorageGet("jobpilot.agent.runId"));
  if (!Number.isSafeInteger(runId) || runId <= 0) { renderAgentRun(null); return; }
  try {
    const run = await api(`/api/agent/runs/${runId}`);
    renderAgentRun(run);
  } catch (error) {
    if (error.status === 404) {
      agentStorageSet("jobpilot.agent.runId", null);
      renderAgentRun(null);
    }
    toast(error.message, "error");
  }
}

function renderAgentRun(run) {
  stopAgentPolling();
  agentState.run = run;
  $("agent-run-card").hidden = !run;
  $("agent-confirmation").hidden = !(run && run.status === "WAITING_USER_ACTION" && run.pendingConfirmationId);
  $("agent-cancel").hidden = !(run && run.status === "WAITING_USER_ACTION");
  if (!run) {
    $("agent-steps").innerHTML = '<div class="empty-state compact">开始任务后，这里会展示真实工具调用和人工确认记录。</div>';
    return;
  }
  agentStorageSet("jobpilot.agent.runId", run.id);
  agentStorageSet("jobpilot.agent.conversationId", run.conversationId);
  $("agent-run-status").textContent = agentStatusLabels[run.status] || run.status;
  $("agent-run-status").className = `agent-status ${esc(run.status)}`;
  $("agent-run-meta").textContent = `任务 #${run.id} · ${run.mode || "UNKNOWN"} · ${formatDate(run.updatedAt)}`;
  $("agent-answer").textContent = run.response || (run.status === "RUNNING"
    ? "任务已在后台执行，页面每 1.2 秒自动更新。你可以切换到其他页面，无需反复点击。"
    : run.status === "WAITING_USER_ACTION"
      ? "Agent 已打开 BOSS 基础页，等待你手动搜索。完成后再确认当前页。"
      : "任务已结束，暂无文字摘要。请查看下方执行步骤。");
  if (run.pendingConfirmationId) {
    $("agent-confirm-summary").textContent = "请确认 BOSS 当前活动标签是你手动搜索得到、稳定显示且允许读取的职位列表。确认后只读取当前页一次，不自动翻页；拒绝或过期都不会采集。";
  }
  const steps = run.steps || [];
  $("agent-steps").innerHTML = steps.length ? steps.map((step, index) => {
    const statusClass = step.status === "FAILED" ? "failed" : step.status === "WAITING" ? "waiting" : "";
    return `<div class="agent-step ${statusClass}"><span class="agent-step-marker">${index + 1}</span><div><strong>${esc(agentToolLabels[step.toolName] || step.toolName)}</strong><p>${esc(step.summary || "无摘要")}</p><small>${esc(step.status)} · ${esc(step.durationMs)} ms · ${esc(formatDate(step.createdAt))}</small></div></div>`;
  }).join("") : '<div class="empty-state compact">任务已创建，正在等待后台执行轨迹。</div>';
  scheduleAgentPolling();
}

async function confirmAgentRun(approved) {
  const run = agentState.run;
  if (!run || agentState.busy || !run.pendingConfirmationId) return;
  setAgentBusy(true);
  try {
    const result = await api(`/api/agent/runs/${run.id}/confirm`, { method: "POST", body: JSON.stringify({
      confirmationId: run.pendingConfirmationId,
      approved
    }) });
    renderAgentRun(result);
    toast(approved ? "当前页处理完成，请核对采集数量" : "已拒绝采集，未读取当前页");
    if (approved) await Promise.allSettled([loadJobs(), loadDashboard()]);
  } catch (error) {
    toast(error.message, "error", 5500);
    await resumeAgentRunAfterBusy();
  } finally { setAgentBusy(false); }
}

async function resumeAgentRunAfterBusy() {
  const run = agentState.run;
  if (!run) return;
  try { renderAgentRun(await api(`/api/agent/runs/${run.id}`)); } catch { /* 保留原错误提示 */ }
}

async function cancelAgentRun() {
  const run = agentState.run;
  if (!run || agentState.busy) return;
  setAgentBusy(true);
  try {
    renderAgentRun(await api(`/api/agent/runs/${run.id}/cancel`, { method: "POST" }));
    toast("任务已取消");
  } catch (error) { toast(error.message, "error"); }
  finally { setAgentBusy(false); }
}

function resetAgentConversation() {
  agentState.run = null;
  agentStorageSet("jobpilot.agent.runId", null);
  agentStorageSet("jobpilot.agent.conversationId", null);
  $("agent-goal").value = "";
  $("agent-goal").focus();
  renderAgentRun(null);
  toast("已开始新对话；历史任务仍保留在本机数据库");
}

function initAgentView() {
  bindAgentEvents();
  if (new URLSearchParams(window.location.search).get("view") === "agent") resumeAgentRun();
}

document.addEventListener("DOMContentLoaded", initAgentView);
