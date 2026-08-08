const chatArea = document.getElementById("chatArea");
const welcome = document.getElementById("welcome");
const messageInput = document.getElementById("messageInput");
const sendBtn = document.getElementById("sendBtn");
const sidebar = document.getElementById("sidebar");
const fileList = document.getElementById("fileList");
const memoryList = document.getElementById("memoryList");
const pdfInput = document.getElementById("pdfInput");
const rememberInput = document.getElementById("rememberInput");

let messagesEl = null;

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

function ensureMessagesContainer() {
  if (!messagesEl) {
    welcome.remove();
    messagesEl = document.createElement("div");
    messagesEl.className = "messages";
    chatArea.appendChild(messagesEl);
  }
  return messagesEl;
}

function renderMessage(role, text, sources) {
  const container = ensureMessagesContainer();
  const msg = document.createElement("div");
  msg.className = "msg " + role;

  const label = document.createElement("div");
  label.className = "msg-label";
  label.textContent = role === "user" ? "You" : "MyAI";
  msg.appendChild(label);

  const bubble = document.createElement("div");
  bubble.className = "bubble";
  bubble.textContent = text;
  msg.appendChild(bubble);

  if (sources && sources.length) {
    const src = document.createElement("div");
    src.className = "sources";
    src.textContent = "📚 " + sources.map(s => `${s.file} (p.${s.page})`).join(", ");
    msg.appendChild(src);
  }

  container.appendChild(msg);
  chatArea.scrollTop = chatArea.scrollHeight;
  return msg;
}

function renderTyping() {
  const container = ensureMessagesContainer();
  const msg = document.createElement("div");
  msg.className = "msg assistant";
  msg.id = "typingMsg";
  msg.innerHTML = `<div class="msg-label">MyAI</div><div class="bubble"><div class="typing"><span></span><span></span><span></span></div></div>`;
  container.appendChild(msg);
  chatArea.scrollTop = chatArea.scrollHeight;
}

function removeTyping() {
  const el = document.getElementById("typingMsg");
  if (el) el.remove();
}

async function sendMessage() {
  const text = messageInput.value.trim();
  if (!text) return;

  messageInput.value = "";
  messageInput.style.height = "auto";
  sendBtn.disabled = true;

  renderMessage("user", text);
  renderTyping();

  try {
    const res = await fetch("/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ message: text })
    });
    const data = await res.json();
    removeTyping();

    if (data.error) {
      renderMessage("assistant", "Error: " + data.error);
    } else {
      renderMessage("assistant", data.reply, data.sources);
    }
  } catch (err) {
    removeTyping();
    renderMessage("assistant", "Connection error: " + err.message);
  }

  sendBtn.disabled = false;
}

// ---------- New chat ----------
document.getElementById("newChatBtn").onclick = async () => {
  await fetch("/api/new_chat", { method: "POST" });
  location.reload();
};

// ---------- Load history on start ----------
async function loadHistory() {
  const res = await fetch("/api/history");
  const data = await res.json();
  if (data.history && data.history.length) {
    data.history.forEach(h => renderMessage(h.role, h.text, h.sources));
  }
}

// ---------- PDF files ----------
async function loadFiles() {
  const res = await fetch("/api/files");
  const data = await res.json();
  fileList.innerHTML = "";
  if (!data.files.length) {
    fileList.innerHTML = `<div class="empty-hint">No PDFs yet</div>`;
    return;
  }
  data.files.forEach(f => {
    const item = document.createElement("div");
    item.className = "file-item";
    item.textContent = "📄 " + f;
    fileList.appendChild(item);
  });
}

pdfInput.addEventListener("change", async () => {
  const file = pdfInput.files[0];
  if (!file) return;
  const formData = new FormData();
  formData.append("file", file);
  await fetch("/api/upload", { method: "POST", body: formData });
  pdfInput.value = "";
  loadFiles();
});

// ---------- Memory ----------
async function loadMemory() {
  const res = await fetch("/api/memory");
  const data = await res.json();
  memoryList.innerHTML = "";
  const lines = (data.memory || "").split("\n").filter(l => l.trim());
  if (!lines.length) {
    memoryList.innerHTML = `<div class="empty-hint">Nothing remembered yet</div>`;
    return;
  }
  lines.forEach(l => {
    const item = document.createElement("div");
    item.className = "memory-item";
    item.textContent = l;
    memoryList.appendChild(item);
  });
}

document.getElementById("rememberBtn").onclick = async () => {
  const fact = rememberInput.value.trim();
  if (!fact) return;
  await fetch("/api/memory/remember", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ fact })
  });
  rememberInput.value = "";
  loadMemory();
};

rememberInput.addEventListener("keydown", (e) => {
  if (e.key === "Enter") document.getElementById("rememberBtn").click();
});

// ---------- Init ----------
loadHistory();
loadFiles();
loadMemory();
