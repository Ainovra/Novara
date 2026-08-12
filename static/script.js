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


// ===== User settings: capabilities, haptics, font and voice =====
const SETTINGS_KEY = "novara-settings";
const SETTINGS_DEFAULTS = {haptic:true, web_search:true, image_gen:true, files:true, memory:true, voice_assistant:true, artifacts:false, code_execution:false, switch_flagged_model:true, font:"inter", voice:"", volume:1};
let novaraSettings = {...SETTINGS_DEFAULTS};
try { novaraSettings = {...SETTINGS_DEFAULTS, ...JSON.parse(localStorage.getItem(SETTINGS_KEY) || "{}")} } catch (_) {}
function saveNovaraSettings(){ localStorage.setItem(SETTINGS_KEY, JSON.stringify(novaraSettings)); }
function haptic(pattern=8){ if (novaraSettings.haptic && navigator.vibrate) navigator.vibrate(pattern); }
function updateActionButton(){
  const hasInput = !!(messageInput && (messageInput.value.trim() || pendingFile));
  const listening = micBtn && micBtn.classList.contains("listening");

  if (sendBtn) {
    sendBtn.classList.toggle("visible", hasInput);
    sendBtn.disabled = !hasInput;
  }
  if (micBtn) {
    micBtn.disabled = listening ? false : !(novaraSettings.voice_assistant && voiceSupported);
    micBtn.setAttribute("aria-label", listening ? "Stop voice input" : "Voice assistant");
    micBtn.title = listening ? "Stop listening" : "Talk to Novara";
  }
}
function applyFont(font){ document.documentElement.setAttribute("data-font", font || "inter"); }
applyFont(novaraSettings.font);
function getMemories(){ try { return JSON.parse(localStorage.getItem("novara-memory") || "[]"); } catch (_) { return []; } }
function getMemoryContext(){ return novaraSettings.memory ? getMemories().join("\n- ") : ""; }
function getVoiceList(){ return "speechSynthesis" in window ? window.speechSynthesis.getVoices() : []; }
function speakNovara(text){
  if (!novaraSettings.voice_assistant || !("speechSynthesis" in window) || !text) return;
  const u = new SpeechSynthesisUtterance(text);
  u.volume = Number(novaraSettings.volume ?? 1);
  const voices = getVoiceList();
  if (novaraSettings.voice && voices[Number(novaraSettings.voice)]) u.voice = voices[Number(novaraSettings.voice)];
  else u.lang = "en-IN";
  window.speechSynthesis.cancel();
  window.speechSynthesis.speak(u);
}

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
    haptic();
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
if (themeToggle) themeToggle.onclick = () => { haptic(); toggleTheme(); };
if (themeToggleMobile) themeToggleMobile.onclick = () => { haptic(); toggleTheme(); };

// ===== Sidebar (mobile) =====
if (openSidebarBtn) openSidebarBtn.onclick = () => { haptic(); sidebar.classList.add("open"); };
if (closeSidebarBtn) closeSidebarBtn.onclick = () => { haptic(); sidebar.classList.remove("open"); };

// ===== Web search toggle =====
if (webSearchToggle) {
  webSearchToggle.onclick = () => {
    if (!novaraSettings.web_search) return;
    haptic();
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
    if (!novaraSettings.image_gen) return;
    haptic();
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
    if (!novaraSettings.files) return;
    haptic();
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
    updateActionButton();
  };
}

[fileInputCamera, fileInputMedia, fileInputFiles].forEach(input => {
  if (input) input.onchange = () => handleFileSelected(input.files[0]);
});

// ===== Speech to text (with real microphone waveform) =====
let recognition = null;
let waveAnimationId = null;
let voiceCaptured = false;
const voiceSupported = ("webkitSpeechRecognition" in window || "SpeechRecognition" in window);

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

if (voiceSupported) {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  recognition = new SpeechRecognition();
  recognition.lang = navigator.language || "en-US";
  recognition.continuous = false;
  recognition.interimResults = false;

  recognition.onresult = (e) => {
    voiceCaptured = true;
    messageInput.value += (messageInput.value ? " " : "") + e.results[0][0].transcript;
    autoResize();
  };
  recognition.onend = () => {
    micBtn.classList.remove("listening");
    stopWaveform();
    updateActionButton();
    if (voiceCaptured && novaraSettings.voice_assistant && messageInput.value.trim()) {
      voiceCaptured = false;
      setTimeout(() => sendMessage(), 50);
    } else {
      voiceCaptured = false;
    }
  };
  recognition.onerror = (e) => {
    micBtn.classList.remove("listening");
    stopWaveform();
    updateActionButton();

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
      haptic();

      if (!novaraSettings.voice_assistant || !voiceSupported || !recognition) return;
      if (micBtn.classList.contains("listening")) {
        recognition.stop();
        return;
      }

      try {
        micBtn.classList.add("listening");
        updateActionButton();
        startWaveform();
        recognition.start();
      } catch (err) {
        micBtn.classList.remove("listening");
        stopWaveform();
        updateActionButton();
      }
    };
  }
} else if (micBtn) {
  // Keep the button visible but inert on browsers without SpeechRecognition.
  micBtn.classList.add("no-voice-support");
}

if (sendBtn) {
  sendBtn.onclick = () => {
    haptic();
    if (messageInput.value.trim() || pendingFile) sendMessage();
  };
}

// ===== Textarea auto-resize + enter to send =====
function autoResize() {
  messageInput.style.height = "auto";
  messageInput.style.height = Math.min(messageInput.scrollHeight, 140) + "px";
}
if (messageInput) {
  messageInput.addEventListener("input", () => { autoResize(); updateActionButton(); });
  messageInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      if (messageInput.value.trim() || pendingFile) sendMessage();
    }
  });
}
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
  fd.append("cap_web_search", novaraSettings.web_search ? "true" : "false");
  fd.append("cap_image_gen", novaraSettings.image_gen ? "true" : "false");
  fd.append("cap_files", novaraSettings.files ? "true" : "false");
  fd.append("cap_memory", novaraSettings.memory ? "true" : "false");
  fd.append("memory_context", getMemoryContext());
  if (pendingFile && novaraSettings.files) fd.append("file", pendingFile);

  messageInput.value = "";
  autoResize();
  const sentFile = pendingFile;
  pendingFile = null;
  fileInputCamera.value = "";
  fileInputMedia.value = "";
  fileInputFiles.value = "";
  attachPreview.style.display = "none";
  sendBtn.disabled = true;
  updateActionButton();

// ===== Init =====
loadConversations();
