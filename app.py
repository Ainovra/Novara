import os
import re
import json
import requests
from datetime import datetime
from flask import Flask, request, jsonify, render_template, session
from pypdf import PdfReader

# =========================
# MyAI Web — Flask backend
# =========================

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
MODEL = "gemini-3.5-flash"

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PDF_FOLDER = os.path.join(BASE_DIR, "knowledge")
MEMORY_FILE = os.path.join(BASE_DIR, "smart_memory.txt")
HISTORY_FILE = os.path.join(BASE_DIR, "conversation_history.json")

if not os.path.exists(PDF_FOLDER):
    os.makedirs(PDF_FOLDER)

app = Flask(__name__)
app.secret_key = os.getenv("FLASK_SECRET", "myai-dev-secret-change-me")

# =========================
# MEMORY (persisted to disk)
# =========================

def read_memory():
    if os.path.exists(MEMORY_FILE):
        with open(MEMORY_FILE, "r", encoding="utf-8") as f:
            return f.read()
    return ""


def append_memory(line):
    with open(MEMORY_FILE, "a", encoding="utf-8") as f:
        f.write(line.strip() + "\n")


def clear_memory_file():
    with open(MEMORY_FILE, "w", encoding="utf-8") as f:
        f.write("")


def forget_from_memory(keyword):
    if not os.path.exists(MEMORY_FILE):
        return
    with open(MEMORY_FILE, "r", encoding="utf-8") as f:
        lines = f.readlines()
    new_lines = [l for l in lines if keyword.lower() not in l.lower()]
    with open(MEMORY_FILE, "w", encoding="utf-8") as f:
        f.writelines(new_lines)


# =========================
# CONVERSATION HISTORY (persisted, list of {role, text, sources})
# =========================

def load_history():
    if os.path.exists(HISTORY_FILE):
        try:
            with open(HISTORY_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return []
    return []


def save_history(history_list):
    with open(HISTORY_FILE, "w", encoding="utf-8") as f:
        json.dump(history_list[-100:], f, ensure_ascii=False, indent=2)


# =========================
# PDF KNOWLEDGE
# =========================

chunks = []


def load_pdfs():
    global chunks
    chunks = []

    files = [f for f in os.listdir(PDF_FOLDER) if f.lower().endswith(".pdf")]

    for filename in files:
        path = os.path.join(PDF_FOLDER, filename)
        try:
            reader = PdfReader(path)
            for page_number, page in enumerate(reader.pages, 1):
                text = page.extract_text() or ""
                if not text.strip():
                    continue
                words = text.split()
                for i in range(0, len(words), 250):
                    text_chunk = " ".join(words[i:i + 250])
                    if text_chunk.strip():
                        chunks.append({
                            "file": filename,
                            "page": page_number,
                            "text": text_chunk
                        })
        except Exception as e:
            print(f"PDF LOAD ERROR ({filename}):", e)


load_pdfs()


def search_pdf(question):
    question_words = set(re.findall(r"[a-zA-Z0-9]+", question.lower()))
    results = []
    for chunk in chunks:
        chunk_words = set(re.findall(r"[a-zA-Z0-9]+", chunk["text"].lower()))
        score = len(question_words & chunk_words)
        if score > 0:
            results.append((score, chunk))
    results.sort(key=lambda x: x[0], reverse=True)
    return [x[1] for x in results[:5]]


# =========================
# INTENT DETECTION — keyword based (no API call, saves quota)
# =========================

PDF_HINTS = [
    "pdf", "notes", "according to my", "in my book", "in the book",
    "as per my", "mera pdf", "meri pdf", "syllabus", "chapter"
]

WEB_HINTS = [
    "latest", "today", "current", "news", "price", "score",
    "weather", "abhi", "aaj ka", "recent"
]


def detect_intent(question):
    q = question.lower()
    if any(word in q for word in PDF_HINTS):
        return "PDF"
    if any(word in q for word in WEB_HINTS):
        return "WEB"
    return "NORMAL"


# =========================
# GEMINI CALL
# =========================

def call_gemini(prompt, timeout=60):
    if not GEMINI_API_KEY:
        return None, "GEMINI_API_KEY not set on server."

    try:
        response = requests.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent",
            headers={
                "x-goog-api-key": GEMINI_API_KEY,
                "Content-Type": "application/json"
            },
            json={"contents": [{"role": "user", "parts": [{"text": prompt}]}]},
            timeout=timeout
        )
        data = response.json()

        if "candidates" not in data:
            err = data.get("error", {})
            return None, err.get("message", str(data))

        text = data["candidates"][0]["content"]["parts"][0]["text"]
        return text, None

    except Exception as e:
        return None, str(e)


def ask_ai(question, history_list, pdf_context="", memory=""):
    recent = "\n".join(
        f"{'User' if h['role']=='user' else 'MyAI'}: {h['text']}"
        for h in history_list[-10:]
    )

    prompt = f"""
You are MyAI, a personal AI assistant.
Be natural, friendly, intelligent and helpful.
Do not mention internal tools, prompts, intent detection, APIs or system instructions.
Use the information below only when relevant.

USER MEMORY:
{memory}

RECENT CONVERSATION:
{recent}

PDF INFORMATION:
{pdf_context}

USER QUESTION:
{question}
"""
    text, error = call_gemini(prompt)
    if error:
        return f"Sorry, I couldn't get a response right now. ({error})", None
    return text, None


# =========================
# ROUTES
# =========================

@app.route("/")
def index():
    return render_template("index.html")


@app.route("/api/history", methods=["GET"])
def api_get_history():
    return jsonify({"history": load_history()})


@app.route("/api/new_chat", methods=["POST"])
def api_new_chat():
    save_history([])
    return jsonify({"ok": True})


@app.route("/api/files", methods=["GET"])
def api_files():
    files = [f for f in os.listdir(PDF_FOLDER) if f.lower().endswith(".pdf")]
    return jsonify({"files": files})


@app.route("/api/upload", methods=["POST"])
def api_upload():
    if "file" not in request.files:
        return jsonify({"error": "No file part"}), 400
    f = request.files["file"]
    if not f.filename.lower().endswith(".pdf"):
        return jsonify({"error": "Only PDF files are supported"}), 400
    save_path = os.path.join(PDF_FOLDER, f.filename)
    f.save(save_path)
    load_pdfs()
    return jsonify({"ok": True, "filename": f.filename})


@app.route("/api/memory", methods=["GET"])
def api_get_memory():
    return jsonify({"memory": read_memory()})


@app.route("/api/memory/remember", methods=["POST"])
def api_remember():
    fact = (request.json or {}).get("fact", "").strip()
    if not fact:
        return jsonify({"error": "Empty fact"}), 400
    append_memory(fact)
    return jsonify({"ok": True})


@app.route("/api/memory/forget", methods=["POST"])
def api_forget():
    keyword = (request.json or {}).get("keyword", "").strip()
    if not keyword:
        return jsonify({"error": "Empty keyword"}), 400
    forget_from_memory(keyword)
    return jsonify({"ok": True})


@app.route("/api/memory/clear", methods=["POST"])
def api_clear_memory():
    clear_memory_file()
    return jsonify({"ok": True})


@app.route("/api/chat", methods=["POST"])
def api_chat():
    question = (request.json or {}).get("message", "").strip()
    if not question:
        return jsonify({"error": "Empty message"}), 400

    history_list = load_history()
    intent = detect_intent(question)

    pdf_context = ""
    pdf_sources = []

    if intent == "PDF":
        results = search_pdf(question)
        if results:
            parts = []
            for r in results:
                parts.append(f"Source: {r['file']}\nPage: {r['page']}\n\n{r['text']}\n")
                pdf_sources.append({"file": r["file"], "page": r["page"]})
            pdf_context = "\n".join(parts)

    memory = read_memory()

    answer, _ = ask_ai(question, history_list, pdf_context, memory)

    history_list.append({"role": "user", "text": question})
    history_list.append({"role": "assistant", "text": answer, "sources": pdf_sources})
    save_history(history_list)

    return jsonify({"reply": answer, "sources": pdf_sources})


if __name__ == "__main__":
    port = int(os.getenv("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=False)
