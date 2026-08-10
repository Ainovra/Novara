// =========================
// Novara — frontend logic
// =========================

const chatArea = document.getElementById("chatArea");
const convList = document.getElementById("convList");
const messageInput = document.getElementById("messageInput");
const sendBtn = document.getElementById("sendBtn");
const attachBtn = document.getElementById("attachBtn");
const attachMenu = document.getElementById("attachMenu");
const attachCameraOption = document.getElementById("attachCameraOption");
const attachMediaOption = document.getElementById("attachMediaOption");
const attachFilesOption = document.getElementById("attachFilesOption");
const fileInputCamera = document.getElementById("fileInputCamera");
const fileInputMedia = document.getElementById("fileInputMedia");
const fileInputFiles = document.getElementById("fileInputFiles");
const attachPreview = document.getElementById("attachPreview");
const micBtn = document.getElementById("micBtn");
const newChatBtn = document.getElementById("newChatBtn");
const sidebar = document.getElementById("sidebar");
const openSidebarBtn = document.getElementById("openSidebar");
const closeSidebarBtn = document.getElementById("closeSidebar");
const themeToggle = document.getElementById("themeToggle");
const themeToggleMobile = document.getElementById("themeToggleMobile");
const webSearchToggle = document.getElementById("webSearchToggle");
const imageGenToggle = document.getElementById("imageGenToggle");
const micWave = document.getElementById("micWave");

let currentConvId = null;
let pendingFile = null;
let webSearchOn = false;
let imageGenOn = false;
let selectedModel = localStorage.getItem("novara-model") || "fast";
let renameTargetId = null;
let shareTargetId = null;

// ===== Model picker (Fast / Thinking / Omega) — dropdown near the attach button =====
const modelSelectBtn = document.getElementById("modelSelectBtn");
const modelSelectIcon = document.getElementById("modelSelectIcon");
const modelSelectLabel = document.getElementById("modelSelectLabel");
const modelMenu = document.getElementById("modelMenu");
const modelFastBtn = document.getElementById("modelFastBtn");
const modelThinkingBtn = document.getElementById("modelThinkingBtn");
const modelOmegaBtn = document.getElementById("modelOmegaBtn");

const MODEL_INFO = {
  fast: { icon: "⚡", label: "Fast" },
  thinking: { icon: "💭", label: "Thinking" },
  omega: { icon: "🧠", label: "Omega" }
};

function applyModelSelection() {
  [modelFastBtn, modelThinkingBtn, modelOmegaBtn].forEach(btn => {
    if (btn) btn.classList.toggle("active", btn.dataset.model === selectedModel);
  });
  const info = MODEL_INFO[selectedModel] || MODEL_INFO.fast;
  if (modelSelectIcon) modelSelectIcon.textContent = info.icon;
  if (modelSelectLabel) modelSelectLabel.textContent = info.label;
}
applyModelSelection();

[modelFastBtn, modelThinkingBtn, modelOmegaBtn].forEach(btn => {
  if (!btn) return;
  btn.onclick = () => {
    selectedModel = btn.dataset.model;
    localStorage.setItem("novara-model", selectedModel);
    applyModelSelection();
    closeAllMenus();
  };
});

function closeAllMenus() {
  if (attachMenu) attachMenu.classList.add("hidden");
  if (modelMenu) modelMenu.classList.add("hidden");
}

if (modelSelectBtn) {
  modelSelectBtn.onclick = (e) => {
    e.stopPropagation();
    const wasOpen = !modelMenu.classList.contains("hidden");
    closeAllMenus();
    if (!wasOpen) modelMenu.classList.remove("hidden");
  };
}

document.addEventListener("click", (e) => {
  if (attachMenu && !attachMenu.classList.contains("hidden") && !attachMenu.contains(e.target) && e.target !== attachBtn) {
    attachMenu.classList.add("hidden");
  }
  if (modelMenu && !modelMenu.classList.contains("hidden") && !modelMenu.contains(e.target) && e.target !== modelSelectBtn) {
    modelMenu.classList.add("hidden");
  }
});

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
    if (webSearchOn && imageGenOn) {
      imageGenOn = false;
      imageGenToggle.classList.remove("active");
    }
  };
}

// ===== Image generation toggle =====
if (imageGenToggle) {
  imageGenToggle.onclick = () => {
    imageGenOn = !imageGenOn;
    imageGenToggle.classList.toggle("active", imageGenOn);
    if (imageGenOn && webSearchOn) {
      webSearchOn = false;
      webSearchToggle.classList.remove("active");
    }
    messageInput.placeholder = imageGenOn ? "Describe the image you want…" : "Message Novara…";
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
    convList.innerHTML = '<div class="empty-hint">No conversations yet</div>';
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
  (data.messages || []).forEach(m => renderMessage(m, m.attachment_path ? ("/uploads/" + m.attachment_path) : null));
  loadConversations();
  chatArea.scrollTop = chatArea.scrollHeight;
}

async function deleteConversation(id) {
  if (!confirm("Delete this chat?")) return;
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
  shareLinkInput.value = "Generating link…";
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

// ===== Attach menu (Camera / Photos & Videos / Files) =====
if (attachBtn) {
  attachBtn.onclick = (e) => {
    e.stopPropagation();
    const wasOpen = !attachMenu.classList.contains("hidden");
    closeAllMenus();
    if (!wasOpen) attachMenu.classList.remove("hidden");
  };
}

function openPicker(input) {
  attachMenu.classList.add("hidden");
  input.click();
}
if (attachCameraOption) attachCameraOption.onclick = () => openPicker(fileInputCamera);
if (attachMediaOption) attachMediaOption.onclick = () => openPicker(fileInputMedia);
if (attachFilesOption) attachFilesOption.onclick = () => openPicker(fileInputFiles);

function handleFileSelected(file) {
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
    fileInputCamera.value = "";
    fileInputMedia.value = "";
    fileInputFiles.value = "";
    attachPreview.style.display = "none";
  };
}

[fileInputCamera, fileInputMedia, fileInputFiles].forEach(input => {
  if (input) input.onchange = () => handleFileSelected(input.files[0]);
});

// ===== Speech to text (with real microphone waveform) =====
let recognition = null;
let waveAnimationId = null;

// Build waveform bars once
if (micWave) {
  for (let i = 0; i < 5; i++) {
    const bar = document.createElement("span");
    bar.className = "wave-bar";
    micWave.appendChild(bar);
  }
}
const waveBars = micWave ? micWave.querySelectorAll(".wave-bar") : [];

function startWaveform() {
  // No longer analyses a real audio stream (that required a second mic
  // access that was blocking speech recognition on some devices). Each bar
  // just animates with a slightly different timing/height so it still
  // reads as "listening," without competing for the microphone.
  let t = 0;
  function draw() {
    t += 1;
    waveBars.forEach((bar, i) => {
      const phase = t * 0.15 + i * 1.3;
      const scale = 0.35 + (Math.sin(phase) * 0.5 + 0.5) * 0.9;
      bar.style.transform = `scaleY(${scale})`;
    });
    waveAnimationId = requestAnimationFrame(draw);
  }
  draw();
}

function stopWaveform() {
  if (waveAnimationId) cancelAnimationFrame(waveAnimationId);
  waveBars.forEach(bar => (bar.style.transform = "scaleY(0.25)"));
}

if ("webkitSpeechRecognition" in window || "SpeechRecognition" in window) {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  recognition = new SpeechRecognition();
  recognition.lang = navigator.language || "en-US";
  recognition.continuous = false;
  recognition.interimResults = false;

  recognition.onresult = (e) => {
    messageInput.value += (messageInput.value ? " " : "") + e.results[0][0].transcript;
    autoResize();
  };
  recognition.onend = () => {
    micBtn.classList.remove("listening");
    stopWaveform();
  };
  recognition.onerror = (e) => {
    micBtn.classList.remove("listening");
    stopWaveform();

    // Some devices report their locale (navigator.language) in a form the
    // browser's speech engine doesn't actually support — retry immediately
    // in plain English instead of making the person click twice.
    if (e.error === "language-not-supported" && recognition.lang !== "en-US") {
      recognition.lang = "en-US";
      try {
        micBtn.classList.add("listening");
        recognition.start();
        return;
      } catch (err) {
        micBtn.classList.remove("listening");
      }
    }

    const messages = {
      "not-allowed": "Microphone access is blocked — allow it in your browser settings and try again.",
      "audio-capture": "No microphone found on this device.",
      "no-speech": "Didn't catch that — try again.",
      "network": "Speech recognition needs an internet connection.",
      "language-not-supported": "Speech recognition isn't supported in this language on your device.",
      "aborted": null  // person tapped stop — not an error worth showing
    };
    const msg = messages[e.error] !== undefined ? messages[e.error] : "Speech recognition had a problem — try again.";
    if (msg) showMicMessage(msg);
  };

  function showMicMessage(text) {
    let el = document.getElementById("micMessage");
    if (!el) {
      el = document.createElement("div");
      el.id = "micMessage";
      el.className = "mic-message";
      messageInput.closest(".input-bar").insertBefore(el, messageInput.closest(".input-wrap"));
    }
    el.textContent = text;
    el.style.opacity = "1";
    clearTimeout(el._hideTimer);
    el._hideTimer = setTimeout(() => { el.style.opacity = "0"; }, 4000);
  }

  if (micBtn) {
    micBtn.onclick = () => {
      if (micBtn.classList.contains("listening")) {
        recognition.stop();
        return;
      }

      // Speech recognition manages its own microphone access internally.
      // Requesting a second, separate mic stream at the same time (for the
      // waveform) was blocking recognition from actually hearing anything
      // on some Android devices — the mic indicator would turn on, but no
      // audio ever reached the recognizer. So we no longer open a second
      // stream; the "listening" animation below is a simple pulse instead.
      try {
        micBtn.classList.add("listening");
        startWaveform();
        recognition.start();
      } catch (err) {
        micBtn.classList.remove("listening");
        stopWaveform();
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
  fd.append("image_gen", imageGenOn ? "true" : "false");
  fd.append("model", selectedModel);
  if (pendingFile) fd.append("file", pendingFile);

  messageInput.value = "";
  autoResize();
  const sentFile = pendingFile;
  pendingFile = null;
  fileInputCamera.value = "";
  fileInputMedia.value = "";
  fileInputFiles.value = "";
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
        web_sources: data.web_sources,
        attachment_type: data.generated_image ? "image" : null
      }, data.generated_image ? ("/uploads/" + data.generated_image) : null);
      loadConversations();
    }
  } catch (e) {
    typingEl.remove();
    renderMessage({ role: "assistant", text: "Network error — please try again." });
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

// ===== Fenced code-block rendering (with per-block Copy buttons) =====
// Parses ```lang\ncode``` blocks out of an assistant message and turns each
// one into its own container with a language label + independent Copy
// button. Plain text around/between blocks is still HTML-escaped normally.
let codeBlockCounter = 0;

function renderBubbleContent(text) {
  if (!text) return "";
  const codeBlockRegex = /```([\w+-]*)\n?([\s\S]*?)```/g;
  let lastIndex = 0;
  let html = "";
  let match;

  while ((match = codeBlockRegex.exec(text)) !== null) {
    const [full, rawLang, rawCode] = match;

    if (match.index > lastIndex) {
      html += escapeHtml(text.slice(lastIndex, match.index));
    }

    const lang = (rawLang || "text").trim().toLowerCase();
    const code = rawCode.replace(/\n$/, ""); // drop the newline right before the closing ```
    const blockId = `code-block-${++codeBlockCounter}`;

    html += `
      <div class="code-block">
        <div class="code-block-header">
          <span class="code-lang">${escapeHtml(lang)}</span>
          <button type="button" class="code-copy-btn" data-target="${blockId}">Copy</button>
        </div>
        <pre class="code-block-pre"><code id="${blockId}" class="language-${escapeHtml(lang)}">${escapeHtml(code)}</code></pre>
      </div>
    `;

    lastIndex = codeBlockRegex.lastIndex;
  }

  if (lastIndex < text.length) {
    html += escapeHtml(text.slice(lastIndex));
  }

  return html;
}

// Clipboard API with a manual fallback for older/non-secure-context browsers.
function copyToClipboard(text) {
  if (navigator.clipboard && window.isSecureContext) {
    return navigator.clipboard.writeText(text);
  }
  return new Promise((resolve, reject) => {
    const textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.style.position = "fixed";
    textarea.style.opacity = "0";
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    try {
      document.execCommand("copy");
      resolve();
    } catch (err) {
      reject(err);
    } finally {
      document.body.removeChild(textarea);
    }
  });
}

// Event delegation on chatArea — this makes the Copy button work for every
// code block in every message, including ones rendered after the page
// already loaded (new AI replies), without wiring each one individually.
if (chatArea) {
  chatArea.addEventListener("click", (e) => {
    const btn = e.target.closest(".code-copy-btn");
    if (!btn) return;
    const codeEl = document.getElementById(btn.dataset.target);
    if (!codeEl) return;

    copyToClipboard(codeEl.textContent).then(() => {
      const original = btn.textContent;
      btn.textContent = "Copied ✓";
      btn.classList.add("copied");
      setTimeout(() => {
        btn.textContent = original;
        btn.classList.remove("copied");
      }, 1500);
    }).catch(() => {
      btn.textContent = "Failed";
      setTimeout(() => { btn.textContent = "Copy"; }, 1500);
    });
  });
}

// ===== Render a message with action bar =====
function renderMessage(m, attachmentUrl) {  const el = document.createElement("div");
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
    <div class="msg-label">${m.role === "user" ? "You" : "Novara"}</div>
    ${attachmentHtml}
    <div class="bubble">${m.role === "assistant" ? renderBubbleContent(m.text) : escapeHtml(m.text)}</div>
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
      <button class="msg-action-btn" data-action="like" title="Good response">👍</button>
      <button class="msg-action-btn" data-action="dislike" title="Bad response">👎</button>
      <button class="msg-action-btn" data-action="copy" title="Copy">📋</button>
      <button class="msg-action-btn" data-action="play" title="Read aloud">🔊</button>
      <button class="msg-action-btn" data-action="share" title="Share chat">↗</button>
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
