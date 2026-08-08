const chatArea = document.getElementById("chatArea");
const messageInput = document.getElementById("messageInput");
const sendBtn = document.getElementById("sendBtn");
const sidebar = document.getElementById("sidebar");
const convList = document.getElementById("convList");
const attachBtn = document.getElementById("attachBtn");
const fileInput = document.getElementById("fileInput");
const attachPreview = document.getElementById("attachPreview");
const micBtn = document.getElementById("micBtn");

let messagesEl = null;
let currentConvId = null;
let pendingFile = null;

// ---------- Splash ----------
setTimeout(() => {
  const splash = document.getElementById("splash");
  if (splash) splash.remove();
}, 1700);

// ---------- Theme toggle ----------
function applyTheme(theme) {
  document.body.setAttribute("data-theme", theme);
  const icon = theme === "light" ? "☀️" : "🌙";
  document.getElementById("themeToggle").textContent = icon;
  document.getElementById("themeToggleMobile").textContent = icon;
  localStorage.setItem("novara-theme", theme);
}
const savedTheme = localStorage.getItem("novara-theme") || "dark";
applyTheme(savedTheme);

function toggleTheme() {
  const current = document.body.getAttribute("data-theme") === "light" ? "light" : "dark";
  applyTheme(current === "light" ? "dark" : "light");
}
document.getElementById("themeToggle").onclick = toggleTheme;
document.getElementById("themeToggleMobile").onclick = toggleTheme;

// ---------- Sidebar toggle (mobile) ----------
document.getElementById("openSidebar").onclick = () => sidebar.classList.add("open");
document.getElementById("closeSidebar").onclick = () => sidebar.classList.remove("open");

// ---------- Auto-resize textarea ----------
messageInput.addEventListener("input", () => {
  messageInput.style.height = "auto";
  messageInput.style.height = Math.min(messageInput.scrollHeight, 140) + "px";
});
messageInput.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
});
sendBtn.onclick = sendMessage;

// ---------- Speech to text ----------
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
let recognition = null;
let isListening = false;

if (SpeechRecognition) {
  recognition = new SpeechRecognition();
  recognition.continuous = false;
  recognition.interimResults = false;
  recognition.lang = "en-IN";

  recognition.onresult = (event) => {
    const transcript = event.results[0][0].transcript;
    messageInput.value += (messageInput.value ? " " : "") + transcript;
    messageInput.dispatchEvent(new Event("input"));
  };
  recognition.onend = () => {
    isListening = false;
    micBtn.classList.remove("listening");
  };
  recognition.onerror = () => {
    isListening = false;
    micBtn.classList.remove("listening");
  };

  micBtn.onclick = () => {
    if (isListening) {
      recognition.stop();
    } else {
      recognition.start();
      isListening = true;
      micBtn.classList.add("listening");
    }
  };
} else {
  micBtn.style.display = "none";
}

// ---------- Attachments ----------
attachBtn.onclick = () => fileInput.click();

fileInput.addEventListener("change", () => {
  const file = fileInput.files[0];
  if (!file) return;
  pendingFile = file;

  attachPreview.style.display = "flex";
  attachPreview.innerHTML = "";

  if (file.type.startsWith("image/")) {
    const img = document.createElement("img");
    img.src = URL.createObjectURL(file);
    attachPreview.appendChild(img);
  }

  const label = document.createElement("span");
  label.textContent = file.name;
  attachPreview.appendChild(label);

  const remove = document.createElement("span");
  remove.className = "remove-attach";
  remove.textContent = "✕";
  remove.onclick = () => {
    pendingFile = null;
    fileInput.value = "";
    attachPreview.style.display = "none";
  };
  attachPreview.appendChild(remove);
});

// ---------- Chat rendering ----------
function resetChatArea() {
  chatArea.innerHTML = `
    <div class="welcome" id="welcome">
      <div class="welcome-mark">Novara</div>
      <p>Poocho kuch bhi — text, photo, PDF, ya awaaz se.</p>
    </div>`;
  messagesEl = null;
}

function ensureMessagesContainer() {
  if (!messagesEl) {
    const w = document.getElementById("welcome");
    if (w) w.remove();
    messagesEl = document.createElement("div");
    messagesEl.className = "messages";
    chatArea.appendChild(messagesEl);
  }
  return messagesEl;
}

function renderMessage(role, text, sources, attachmentPath, attachmentType) {
  const container = ensureMessagesContainer();
  const msg = document.createElement("div");
  msg.className = "msg " + role;

  const label = document.createElement("div");
  label.className = "msg-label";
  label.textContent = role === "user" ? "You" : "Novara";
  msg.appendChild(label);

  if (attachmentPath) {
    const attWrap = document.createElement("div");
    attWrap.className = "msg-attachment";
    if (attachmentType === "image") {
      const img = document.createElement("img");
      img.src = "/uploads/" + attachmentPath;
      attWrap.appendChild(img);
    } else if (attachmentType === "video") {
      const vid = document.createElement("video");
      vid.src = "/uploads/" + attachmentPath;
      vid.controls = true;
      attWrap.appendChild(vid);
    } else {
      const chip = document.createElement("div");
      chip.className = "file-chip";
      chip.textContent = "📄 " + attachmentPath.split("_").slice(1).join("_");
      attWrap.appendChild(chip);
    }
    msg.appendChild(attWrap);
  }

  if (text) {
    const bubble = document.createElement("div");
    bubble.className = "bubble";
    bubble.textContent = text;
    msg.appendChild(bubble);
  }

  if (sources && sources.length) {
    const src = document.createElement("div");
    src.className = "sources";
    const list = typeof sources === "string" ? sources : sources.map(s => `${s.file} (p.${s.page})`).join(", ");
    if (list) {
      src.textContent = "📚 " + list;
      msg.appendChild(src);
    }
  }

  container.appendChild(msg);
  chatArea.scrollTop = chatArea.scrollHeight;
}

function renderTyping() {
  const container = ensureMessagesContainer();
  const msg = document.createElement("div");
  msg.className = "msg assistant";
  msg.id = "typingMsg";
  msg.innerHTML = `<div class="msg-label">Novara</div><div class="bubble"><div class="typing"><span></span><span></span><span></span></div></div>`;
  container.appendChild(msg);
  chatArea.scrollTop = chatArea.scrollHeight;
}
function removeTyping() {
  const el = document.getElementById("typingMsg");
  if (el) el.remove();
}

async function sendMessage() {
  const text = messageInput.value.trim();
  if (!text && !pendingFile) return;

  const fileToSend = pendingFile;

  messageInput.value = "";
  messageInput.style.height = "auto";
  pendingFile = null;
  fileInput.value = "";
  attachPreview.style.display = "none";
  attachPreview.innerHTML = "";
  sendBtn.disabled = true;

  const localAttachmentUrl = fileToSend && fileToSend.type.startsWith("image/") ? URL.createObjectURL(fileToSend) : null;
  renderMessage("user", text, null,
    localAttachmentUrl ? null : null, null);

  if (fileToSend) {
    const container = ensureMessagesContainer();
    const last = container.lastElementChild;
    if (fileToSend.type.startsWith("image/") && last) {
      const attWrap = document.createElement("div");
      attWrap.className = "msg-attachment";
      const img = document.createElement("img");
      img.src = localAttachmentUrl;
      attWrap.appendChild(img);
      last.insertBefore(attWrap, last.querySelector(".bubble") || null);
    }
  }

  renderTyping();

  try {
    const formData = new FormData();
    formData.append("message", text);
    if (currentConvId) formData.append("conversation_id", currentConvId);
    if (fileToSend) formData.append("file", fileToSend);

    const res = await fetch("/api/chat", { method: "POST", body: formData });
    const data = await res.json();
    removeTyping();

    if (data.error) {
      renderMessage("assistant", "Error: " + data.error);
    } else {
      renderMessage("assistant", data.reply, data.sources);
      if (!currentConvId) {
        currentConvId = data.conversation_id;
        loadConversations();
      }
    }
  } catch (err) {
    removeTyping();
    renderMessage("assistant", "Connection error: " + err.message);
  }

  sendBtn.disabled = false;
}

// ---------- New chat ----------
document.getElementById("newChatBtn").onclick = () => {
  currentConvId = null;
  resetChatArea();
  document.querySelectorAll(".conv-item").forEach(el => el.classList.remove("active"));
  sidebar.classList.remove("open");
};

// ---------- Conversations ----------
async function loadConversations() {
  const res = await fetch("/api/conversations");
  const data = await res.json();
  convList.innerHTML = "";

  if (!data.conversations.length) {
    convList.innerHTML = `<div class="empty-hint">Koi conversation nahi hai abhi</div>`;
    return;
  }

  data.conversations.forEach(c => {
    const item = document.createElement("div");
    item.className = "conv-item" + (c.id === currentConvId ? " active" : "");

    const titleSpan = document.createElement("span");
    titleSpan.className = "conv-title";
    titleSpan.textContent = c.title;
    item.appendChild(titleSpan);

    const delSpan = document.createElement("span");
    delSpan.className = "conv-delete";
    delSpan.textContent = "✕";
    delSpan.onclick = async (e) => {
      e.stopPropagation();
      await fetch(`/api/conversations/${c.id}`, { method: "DELETE" });
      if (currentConvId === c.id) {
        currentConvId = null;
        resetChatArea();
      }
      loadConversations();
    };
    item.appendChild(delSpan);

    item.onclick = () => openConversation(c.id);
    convList.appendChild(item);
  });
}

async function openConversation(convId) {
  currentConvId = convId;
  resetChatArea();
  document.querySelectorAll(".conv-item").forEach(el => el.classList.remove("active"));

  const res = await fetch(`/api/conversations/${convId}/messages`);
  const data = await res.json();

  if (data.messages && data.messages.length) {
    data.messages.forEach(m => renderMessage(m.role, m.text, m.sources, m.attachment_path, m.attachment_type));
  }

  loadConversations();
  sidebar.classList.remove("open");
}

// ---------- Init ----------
loadConversations();
