// =========================
// Novara — frontend logic
// =========================

const chatArea = document.getElementById("chatArea");
const convList = document.getElementById("convList");
const messageInput = document.getElementById("messageInput");
const sendBtn = document.getElementById("sendBtn");
const fileInput = document.getElementById("fileInput");
const attachBtn = document.getElementById("attachBtn");
const attachPreview = document.getElementById("attachPreview");
const micBtn = document.getElementById("micBtn");
const newChatBtn = document.getElementById("newChatBtn");
const sidebar = document.getElementById("sidebar");
const openSidebarBtn = document.getElementById("openSidebar");
const closeSidebarBtn = document.getElementById("closeSidebar");
const themeToggle = document.getElementById("themeToggle");
const themeToggleMobile = document.getElementById("themeToggleMobile");
const webSearchToggle = document.getElementById("webSearchToggle");

let currentConvId = null;
let pendingFile = null;
let webSearchOn = false;
let renameTargetId = null;
let shareTargetId = null;

// ===== Theme =====
function applyTheme(theme) {
  document.documentElement.setAttribute("data-theme", theme);
  localStorage.setItem("novara-theme", theme);
}
(function initTheme() {
  const saved = localStorage.getItem("novara-theme") || "dark";
  applyTheme(saved);
})();
function toggleTheme() {
  const current = document.documentElement.getAttribute("data-theme") === "light" ? "light" : "dark";
  applyTheme(current === "light" ? "dark" : "light");
}
if (themeToggle) themeToggle.onclick = toggleTheme;
if (themeToggleMobile) themeToggleMobile.onclick = toggleTheme;

// ===== Sidebar (mobile) =====
if (openSidebarBtn) openSidebarBtn.onclick = () => sidebar.classList.add("open");
if (closeSidebarBtn) closeSidebarBtn.onclick = () => sidebar.classList.remove("open");

// ===== Web search toggle =====
if (webSearchToggle) {
  webSearchToggle.onclick = () => {
    webSearchOn = !webSearchOn;
    webSearchToggle.classList.toggle("active", webSearchOn);
  };
}

// ===== Load conversations =====
async function loadConversations() {
  const res = await fetch("/api/conversations");
  const data = await res.json();
  renderConvList(data.conversations || []);
}

function renderConvList(convs) {
  convList.innerHTML = "";
  if (!convs.length) {
    convList.innerHTML = '<div class="empty-hint">Koi conversation nahi hai abhi</div>';
    return;
  }
  convs.forEach(c => {
    const item = document.createElement("div");
    item.className = "conv-item" + (c.id === currentConvId ? " active" : "");
    item.dataset.id = c.id;
    item.innerHTML = `
      <span class="conv-title">${escapeHtml(c.title)}</span>
      <span class="conv-actions">
        <span class="conv-icon-btn conv-rename" title="Rename">✎</span>
        <span class="conv-icon-btn conv-share" title="Share">↗</span>
        <span class="conv-icon-btn conv-delete" title="Delete">✕</span>
      </span>
    `;
    item.querySelector(".conv-title").onclick = () => openConversation(c.id);
    item.querySelector(".conv-rename").onclick = (e) => { e.stopPropagation(); openRenameModal(c.id, c.title); };
    item.querySelector(".conv-share").onclick = (e) => { e.stopPropagation(); openShareModal(c.id); };
    item.querySelector(".conv-delete").onclick = (e) => { e.stopPropagation(); deleteConversation(c.id); };
    convList.appendChild(item);
  });
}

async function openConversation(id) {
  currentConvId = id;
  sidebar.classList.remove("open");
  const res = await fetch(`/api/conversations/${id}/messages`);
  const data = await res.json();
  chatArea.innerHTML = "";
  (data.messages || []).forEach(m => renderMessage(m));
  loadConversations();
  chatArea.scrollTop = chatArea.scrollHeight;
}

async function deleteConversation(id) {
  if (!confirm("Ye chat delete karni hai?")) return;
  await fetch(`/api/conversations/${id}`, { method: "DELETE" });
  if (currentConvId === id) {
    currentConvId = null;
    startNewChat();
  }
  loadConversations();
}

if (newChatBtn) newChatBtn.onclick = startNewChat;
function startNewChat() {
  currentConvId = null;
  chatArea.innerHTML = `
    <div class="welcome" id="welcome">
      <div class="welcome-mark">Novara</div>
      <p>Poocho kuch bhi — text, photo, PDF, ya awaaz se.</p>
    </div>`;
  sidebar.classList.remove("open");
  loadConversations();
}

// ===== Rename modal =====
const renameModal = document.getElementById("renameModal");
const renameInput = document.getElementById("renameInput");
function openRenameModal(id, currentTitle) {
  renameTargetId = id;
  renameInput.value = currentTitle;
  renameModal.classList.remove("hidden");
  renameInput.focus();
}
document.getElementById("renameCancel").onclick = () => renameModal.classList.add("hidden");
document.getElementById("renameSave").onclick = async () => {
  const title = renameInput.value.trim();
  if (!title || !renameTargetId) return;
  await fetch(`/api/conversations/${renameTargetId}/rename`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ title })
  });
  renameModal.classList.add("hidden");
  loadConversations();
};

// ===== Share modal =====
const shareModal = document.getElementById("shareModal");
const shareLinkInput = document.getElementById("shareLinkInput");
function openShareModal(id) {
  shareTargetId = id;
  shareLinkInput.value = "Link ban raha hai…";
  shareModal.classList.remove("hidden");
  fetch(`/api/conversations/${id}/share`, { method: "POST" })
    .then(r => r.json())
    .then(data => { shareLinkInput.value = data.share_url || data.error || ""; });
}
document.getElementById("shareClose").onclick = () => shareModal.classList.add("hidden");
document.getElementById("shareCopyBtn").onclick = () => {
  shareLinkInput.select();
  navigator.clipboard.writeText(shareLinkInput.value).catch(() => document.execCommand("copy"));
  document.getElementById("shareCopyBtn").textContent = "Copied!";
  setTimeout(() => { document.getElementById("shareCopyBtn").textContent = "Copy"; }, 1500);
};
document.getElementById("shareUnlink").onclick = async () => {
  if (!shareTargetId) return;
  await fetch(`/api/conversations/${shareTargetId}/unshare`, { method: "POST" });
  shareModal.classList.add("hidden");
};

// ===== Attach file =====
if (attachBtn) attachBtn.onclick = () => fileInput.click();
if (fileInput) fileInput.onchange = () => {
  const file = fileInput.files[0];
  if (!file) return;
  pendingFile = file;
  attachPreview.style.display = "flex";
  const isImage = file.type.startsWith("image/");
  attachPreview.innerHTML = `
    ${isImage ? `<img src="${URL.createObjectURL(file)}">` : "📎"}
    <span>${escapeHtml(file.name)}</span>
    <span class="remove-attach">✕</span>
  `;
  attachPreview.querySelector(".remove-attach").onclick = () => {
    pendingFile = null;
    fileInput.value = "";
    attachPreview.style.display = "none";
  };
};

// ===== Speech to text =====
let recognition = null;
if ("webkitSpeechRecognition" in window || "SpeechRecognition" in window) {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  recognition = new SpeechRecognition();
  recognition.lang = "hi-IN";
  recognition.continuous = false;
  recognition.interimResults = false;

  recognition.onresult = (e) => {
    messageInput.value += (messageInput.value ? " " : "") + e.results[0][0].transcript;
    autoResize();
  };
  recognition.onend = () => micBtn.classList.remove("listening");

  if (micBtn) {
    micBtn.onclick = () => {
      if (micBtn.classList.contains("listening")) {
        recognition.stop();
      } else {
        micBtn.classList.add("listening");
        recognition.start();
      }
    };
  }
} else if (micBtn) {
  micBtn.style.display = "none";
}

// ===== Textarea auto-resize + enter to send =====
function autoResize() {
  messageInput.style.height = "auto";
  messageInput.style.height = Math.min(messageInput.scrollHeight, 140) + "px";
}
if (messageInput) {
  messageInput.addEventListener("input", autoResize);
  messageInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  });
}
if (sendBtn) sendBtn.onclick = sendMessage;

// ===== Send message =====
async function sendMessage() {
  const text = messageInput.value.trim();
  if (!text && !pendingFile) return;

  document.getElementById("welcome")?.remove();

  const userMsg = { role: "user", text, attachment_type: pendingFile ? (pendingFile.type.startsWith("image/") ? "image" : "file") : null };
  renderMessage(userMsg, pendingFile ? URL.createObjectURL(pendingFile) : null);

  const typingEl = renderTyping();

  const fd = new FormData();
  fd.append("message", text);
  if (currentConvId) fd.append("conversation_id", currentConvId);
  fd.append("web_search", webSearchOn ? "true" : "false");
  if (pendingFile) fd.append("file", pendingFile);

  messageInput.value = "";
  autoResize();
  const sentFile = pendingFile;
  pendingFile = null;
  fileInput.value = "";
  attachPreview.style.display = "none";
  sendBtn.disabled = true;

  try {
    const res = await fetch("/api/chat", { method: "POST", body: fd });
    const data = await res.json();
    typingEl.remove();

    if (data.error) {
      renderMessage({ role: "assistant", text: "Error: " + data.error });
    } else {
      currentConvId = data.conversation_id;
      renderMessage({
        id: data.message_id,
        role: "assistant",
        text: data.reply,
        sources: (data.sources || []).map(s => `${s.file} p.${s.page}`).join(", "),
        web_sources: data.web_sources
      });
      loadConversations();
    }
  } catch (e) {
    typingEl.remove();
    renderMessage({ role: "assistant", text: "Network error — dobara try karein." });
  }
  sendBtn.disabled = false;
  chatArea.scrollTop = chatArea.scrollHeight;
}

function renderTyping() {
  const el = document.createElement("div");
  el.className = "msg assistant";
  el.innerHTML = `<div class="msg-label">Novara</div><div class="bubble"><span class="typing"><span></span><span></span><span></span></span></div>`;
  chatArea.appendChild(el);
  chatArea.scrollTop = chatArea.scrollHeight;
  return el;
}

// ===== Render a message with action bar =====
function renderMessage(m, attachmentUrl) {
  const el = document.createElement("div");
  el.className = "msg " + (m.role === "user" ? "user" : "assistant");

  let attachmentHtml = "";
  if (attachmentUrl && m.attachment_type === "image") {
    attachmentHtml = `<div class="msg-attachment"><img src="${attachmentUrl}"></div>`;
  } else if (m.attachment_type && attachmentUrl) {
    attachmentHtml = `<div class="msg-attachment"><span class="file-chip">📎 ${m.attachment_type}</span></div>`;
  }

  let sourcesHtml = "";
  if (m.sources) sourcesHtml += `<div class="sources">📄 ${escapeHtml(m.sources)}</div>`;
  if (m.web_sources && m.web_sources.length) {
    sourcesHtml += `<div class="sources">🌐 ${m.web_sources.map(s => escapeHtml(s.title)).join(" · ")}</div>`;
  }

  el.innerHTML = `
    <div class="msg-label">${m.role === "user" ? "Aap" : "Novara"}</div>
    ${attachmentHtml}
    <div class="bubble">${escapeHtml(m.text)}</div>
    ${sourcesHtml}
    ${m.role === "assistant" ? renderActionBar(m) : ""}
  `;
  chatArea.appendChild(el);
  chatArea.scrollTop = chatArea.scrollHeight;

  if (m.role === "assistant") wireActionBar(el, m);
}

function renderActionBar(m) {
  return `
    <div class="msg-actions">
      <button class="msg-action-btn" data-action="like" title="Achha jawab">👍</button>
      <button class="msg-action-btn" data-action="dislike" title="Bura jawab">👎</button>
      <button class="msg-action-btn" data-action="copy" title="Copy">📋</button>
      <button class="msg-action-btn" data-action="play" title="Sunein">🔊</button>
      <button class="msg-action-btn" data-action="share" title="Chat share karein">↗</button>
    </div>
  `;
}

function wireActionBar(el, m) {
  const bar = el.querySelector(".msg-actions");
  if (!bar) return;

  bar.querySelector('[data-action="copy"]').onclick = () => {
    navigator.clipboard.writeText(m.text);
    flashAction(bar, "copy", "✅");
  };

  bar.querySelector('[data-action="play"]').onclick = (e) => {
    const btn = e.currentTarget;
    if (!("speechSynthesis" in window)) return;
    if (window.speechSynthesis.speaking) {
      window.speechSynthesis.cancel();
      btn.textContent = "🔊";
      return;
    }
    const utter = new SpeechSynthesisUtterance(m.text);
    utter.lang = "hi-IN";
    utter.onend = () => { btn.textContent = "🔊"; };
    btn.textContent = "⏸";
    window.speechSynthesis.speak(utter);
  };

  bar.querySelector('[data-action="share"]').onclick = () => {
    if (!currentConvId) return;
    openShareModal(currentConvId);
  };

  const likeBtn = bar.querySelector('[data-action="like"]');
  const dislikeBtn = bar.querySelector('[data-action="dislike"]');
  if (m.id) {
    likeBtn.onclick = () => sendFeedback(m.id, "like", likeBtn, dislikeBtn);
    dislikeBtn.onclick = () => sendFeedback(m.id, "dislike", dislikeBtn, likeBtn);
  } else {
    likeBtn.disabled = true;
    dislikeBtn.disabled = true;
  }
}

async function sendFeedback(messageId, type, activeBtn, otherBtn) {
  const isActive = activeBtn.classList.contains("active");
  const newValue = isActive ? null : type;
  activeBtn.classList.toggle("active", !isActive);
  otherBtn.classList.remove("active");
  await fetch(`/api/messages/${messageId}/feedback`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ feedback: newValue })
  });
}

function flashAction(bar, action, symbol) {
  const btn = bar.querySelector(`[data-action="${action}"]`);
  const original = btn.textContent;
  btn.textContent = symbol;
  setTimeout(() => { btn.textContent = original; }, 1200);
}

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str || "";
  return div.innerHTML;
}

// ===== Init =====
loadConversations();
