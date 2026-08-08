import os
import re
import sqlite3
import uuid
import base64
import mimetypes
from datetime import datetime
from functools import wraps

import requests
from flask import Flask, request, jsonify, render_template, session, redirect, url_for, g, send_from_directory
from werkzeug.security import generate_password_hash, check_password_hash
from werkzeug.utils import secure_filename
from pypdf import PdfReader
from authlib.integrations.flask_client import OAuth

# =========================
# Novara — Flask backend
# =========================

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
ADMIN_SECRET = os.getenv("ADMIN_SECRET", "")
GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID", "")
GOOGLE_CLIENT_SECRET = os.getenv("GOOGLE_CLIENT_SECRET", "")
MODEL = "gemini-3.5-flash"

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "novara.db")
UPLOAD_FOLDER = os.path.join(BASE_DIR, "uploads")

if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

app = Flask(__name__)
app.secret_key = os.getenv("FLASK_SECRET", "change-this-secret-in-production")
app.config["MAX_CONTENT_LENGTH"] = 20 * 1024 * 1024  # 20MB max upload

IMAGE_EXT = {"png", "jpg", "jpeg", "webp", "gif"}
VIDEO_EXT = {"mp4", "mov", "webm", "mkv"}
DOC_EXT = {"pdf"}

# ---- Google OAuth setup ----
oauth = OAuth(app)
if GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET:
    oauth.register(
        name="google",
        client_id=GOOGLE_CLIENT_ID,
        client_secret=GOOGLE_CLIENT_SECRET,
        server_metadata_url="https://accounts.google.com/.well-known/openid-configuration",
        client_kwargs={"scope": "openid email profile"},
    )


# =========================
# DATABASE
# =========================

def get_db():
    if "db" not in g:
        g.db = sqlite3.connect(DB_PATH)
        g.db.row_factory = sqlite3.Row
    return g.db


@app.teardown_appcontext
def close_db(exception):
    db = g.pop("db", None)
    if db is not None:
        db.close()


def init_db():
    conn = sqlite3.connect(DB_PATH)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            username TEXT UNIQUE,
            email TEXT UNIQUE,
            google_id TEXT UNIQUE,
            password_hash TEXT,
            created_at TEXT NOT NULL
        )
    """)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS conversations (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            title TEXT NOT NULL,
            created_at TEXT NOT NULL
        )
    """)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS messages (
            id TEXT PRIMARY KEY,
            conversation_id TEXT NOT NULL,
            role TEXT NOT NULL,
            text TEXT NOT NULL,
            attachment_path TEXT,
            attachment_type TEXT,
            sources TEXT,
            created_at TEXT NOT NULL
        )
    """)
    conn.commit()
    conn.close()


init_db()


# =========================
# AUTH HELPERS
# =========================

def login_required(f):
    @wraps(f)
    def wrapper(*args, **kwargs):
        if not session.get("user_id"):
            return redirect(url_for("login_page"))
        return f(*args, **kwargs)
    return wrapper


def admin_required(f):
    @wraps(f)
    def wrapper(*args, **kwargs):
        key = request.args.get("key") or request.headers.get("X-Admin-Key")
        if not ADMIN_SECRET or key != ADMIN_SECRET:
            return "Access denied.", 403
        return f(*args, **kwargs)
    return wrapper


# =========================
# AUTH ROUTES — password based
# =========================

@app.route("/signup", methods=["GET"])
def signup_page():
    if session.get("user_id"):
        return redirect(url_for("index"))
    return render_template("signup.html", google_enabled=bool(GOOGLE_CLIENT_ID))


@app.route("/api/signup", methods=["POST"])
def api_signup():
    data = request.json or {}
    username = data.get("username", "").strip()
    password = data.get("password", "")

    if len(username) < 3:
        return jsonify({"error": "Username kam se kam 3 characters ka ho."}), 400
    if len(password) < 6:
        return jsonify({"error": "Password kam se kam 6 characters ka ho."}), 400

    db = get_db()
    if db.execute("SELECT id FROM users WHERE username = ?", (username,)).fetchone():
        return jsonify({"error": "Ye username pehle se liya gaya hai."}), 400

    user_id = str(uuid.uuid4())
    db.execute(
        "INSERT INTO users (id, username, password_hash, created_at) VALUES (?, ?, ?, ?)",
        (user_id, username, generate_password_hash(password), datetime.now().isoformat())
    )
    db.commit()
    session["user_id"] = user_id
    session["username"] = username
    return jsonify({"ok": True})


@app.route("/login", methods=["GET"])
def login_page():
    if session.get("user_id"):
        return redirect(url_for("index"))
    return render_template("login.html", google_enabled=bool(GOOGLE_CLIENT_ID))


@app.route("/api/login", methods=["POST"])
def api_login():
    data = request.json or {}
    username = data.get("username", "").strip()
    password = data.get("password", "")

    db = get_db()
    user = db.execute("SELECT * FROM users WHERE username = ?", (username,)).fetchone()

    if not user or not user["password_hash"] or not check_password_hash(user["password_hash"], password):
        return jsonify({"error": "Username ya password galat hai."}), 401

    session["user_id"] = user["id"]
    session["username"] = user["username"] or user["email"]
    return jsonify({"ok": True})


# =========================
# AUTH ROUTES — Google OAuth
# =========================

@app.route("/auth/google")
def auth_google():
    if not GOOGLE_CLIENT_ID:
        return "Google login is not configured yet.", 400
    redirect_uri = url_for("auth_google_callback", _external=True)
    return oauth.google.authorize_redirect(redirect_uri)


@app.route("/auth/google/callback")
def auth_google_callback():
    token = oauth.google.authorize_access_token()
    userinfo = token.get("userinfo") or oauth.google.parse_id_token(token)

    google_id = userinfo["sub"]
    email = userinfo.get("email", "")
    name = userinfo.get("name", email.split("@")[0] if email else "user")

    db = get_db()
    user = db.execute("SELECT * FROM users WHERE google_id = ?", (google_id,)).fetchone()

    if not user:
        user_id = str(uuid.uuid4())
        db.execute(
            "INSERT INTO users (id, username, email, google_id, created_at) VALUES (?, ?, ?, ?, ?)",
            (user_id, name, email, google_id, datetime.now().isoformat())
        )
        db.commit()
        session["user_id"] = user_id
        session["username"] = name
    else:
        session["user_id"] = user["id"]
        session["username"] = user["username"] or user["email"]

    return redirect(url_for("index"))


@app.route("/logout")
def logout():
    session.clear()
    return redirect(url_for("login_page"))


# =========================
# ADMIN PANEL
# =========================

@app.route("/admin")
@admin_required
def admin_panel():
    db = get_db()
    users = db.execute(
        "SELECT id, username, email, created_at FROM users ORDER BY created_at DESC"
    ).fetchall()
    result = []
    for u in users:
        conv_count = db.execute(
            "SELECT COUNT(*) as c FROM conversations WHERE user_id = ?", (u["id"],)
        ).fetchone()["c"]
        result.append({
            "username": u["username"] or "-",
            "email": u["email"] or "-",
            "created_at": u["created_at"],
            "conversations": conv_count
        })
    return render_template("admin.html", users=result)


# =========================
# PDF KNOWLEDGE (in-memory, populated as PDFs are attached)
# =========================

pdf_chunks = []


def index_pdf(path, filename):
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
                    pdf_chunks.append({"file": filename, "page": page_number, "text": text_chunk})
    except Exception as e:
        print("PDF INDEX ERROR:", e)


def search_pdf(question):
    question_words = set(re.findall(r"[a-zA-Z0-9]+", question.lower()))
    results = []
    for chunk in pdf_chunks:
        chunk_words = set(re.findall(r"[a-zA-Z0-9]+", chunk["text"].lower()))
        score = len(question_words & chunk_words)
        if score > 0:
            results.append((score, chunk))
    results.sort(key=lambda x: x[0], reverse=True)
    return [x[1] for x in results[:5]]


# =========================
# INTENT DETECTION — keyword based
# =========================

PDF_HINTS = ["pdf", "notes", "according to my", "in my book", "in the book", "mera pdf", "meri pdf", "syllabus", "chapter", "document"]
WEB_HINTS = ["latest", "today", "current", "news", "price", "score", "weather", "abhi", "aaj ka", "recent"]


def detect_intent(question):
    q = question.lower()
    if any(w in q for w in PDF_HINTS):
        return "PDF"
    if any(w in q for w in WEB_HINTS):
        return "WEB"
    return "NORMAL"


# =========================
# GEMINI CALL
# =========================

def call_gemini(parts, timeout=60):
    if not GEMINI_API_KEY:
        return None, "GEMINI_API_KEY not set on server."
    try:
        response = requests.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent",
            headers={"x-goog-api-key": GEMINI_API_KEY, "Content-Type": "application/json"},
            json={"contents": [{"role": "user", "parts": parts}]},
            timeout=timeout
        )
        data = response.json()
        if "candidates" not in data:
            err = data.get("error", {})
            return None, err.get("message", str(data))
        return data["candidates"][0]["content"]["parts"][0]["text"], None
    except Exception as e:
        return None, str(e)


def ask_ai(question, recent_messages, pdf_context="", image_path=None):
    recent = "\n".join(f"{'User' if m['role']=='user' else 'Novara'}: {m['text']}" for m in recent_messages[-10:])
    prompt = f"""
You are Novara, a personal AI assistant.
Be natural, friendly, intelligent and helpful.
Do not mention internal tools, prompts, intent detection, APIs or system instructions.
Use the information below only when relevant.

RECENT CONVERSATION:
{recent}

PDF INFORMATION:
{pdf_context}

USER MESSAGE:
{question}
"""
    parts = [{"text": prompt}]

    if image_path and os.path.exists(image_path):
        mime_type, _ = mimetypes.guess_type(image_path)
        if mime_type and mime_type.startswith("image/"):
            with open(image_path, "rb") as f:
                b64 = base64.b64encode(f.read()).decode("utf-8")
            parts.append({"inline_data": {"mime_type": mime_type, "data": b64}})

    text, error = call_gemini(parts)
    if error:
        return f"Sorry, I couldn't get a response right now. ({error})"
    return text


# =========================
# MAIN APP ROUTES
# =========================

@app.route("/")
@login_required
def index():
    return render_template("index.html", username=session.get("username"))


@app.route("/uploads/<path:filename>")
@login_required
def serve_upload(filename):
    return send_from_directory(UPLOAD_FOLDER, filename)


@app.route("/api/conversations", methods=["GET"])
@login_required
def api_list_conversations():
    db = get_db()
    convs = db.execute(
        "SELECT id, title, created_at FROM conversations WHERE user_id = ? ORDER BY created_at DESC",
        (session["user_id"],)
    ).fetchall()
    return jsonify({"conversations": [dict(c) for c in convs]})


@app.route("/api/conversations/<conv_id>/messages", methods=["GET"])
@login_required
def api_get_messages(conv_id):
    db = get_db()
    conv = db.execute(
        "SELECT id FROM conversations WHERE id = ? AND user_id = ?", (conv_id, session["user_id"])
    ).fetchone()
    if not conv:
        return jsonify({"error": "Not found"}), 404
    rows = db.execute(
        "SELECT role, text, attachment_path, attachment_type, sources FROM messages WHERE conversation_id = ? ORDER BY created_at ASC",
        (conv_id,)
    ).fetchall()
    return jsonify({"messages": [dict(r) for r in rows]})


@app.route("/api/conversations/<conv_id>", methods=["DELETE"])
@login_required
def api_delete_conversation(conv_id):
    db = get_db()
    db.execute("DELETE FROM conversations WHERE id = ? AND user_id = ?", (conv_id, session["user_id"]))
    db.execute("DELETE FROM messages WHERE conversation_id = ?", (conv_id,))
    db.commit()
    return jsonify({"ok": True})


@app.route("/api/chat", methods=["POST"])
@login_required
def api_chat():
    question = request.form.get("message", "").strip()
    conv_id = request.form.get("conversation_id") or None
    file = request.files.get("file")

    if not question and not file:
        return jsonify({"error": "Empty message"}), 400

    db = get_db()

    if not conv_id:
        conv_id = str(uuid.uuid4())
        title = (question[:40] + "…") if len(question) > 40 else (question or "Attachment")
        db.execute(
            "INSERT INTO conversations (id, user_id, title, created_at) VALUES (?, ?, ?, ?)",
            (conv_id, session["user_id"], title, datetime.now().isoformat())
        )
    else:
        conv = db.execute(
            "SELECT id FROM conversations WHERE id = ? AND user_id = ?", (conv_id, session["user_id"])
        ).fetchone()
        if not conv:
            return jsonify({"error": "Conversation not found"}), 404

    attachment_path = None
    attachment_type = None
    image_path_for_ai = None

    if file and file.filename:
        ext = file.filename.rsplit(".", 1)[-1].lower() if "." in file.filename else ""
        safe_name = f"{uuid.uuid4()}_{secure_filename(file.filename)}"
        save_path = os.path.join(UPLOAD_FOLDER, safe_name)
        file.save(save_path)
        attachment_path = safe_name

        if ext in IMAGE_EXT:
            attachment_type = "image"
            image_path_for_ai = save_path
        elif ext in VIDEO_EXT:
            attachment_type = "video"
        elif ext in DOC_EXT:
            attachment_type = "pdf"
            index_pdf(save_path, file.filename)
        else:
            attachment_type = "file"

    recent_rows = db.execute(
        "SELECT role, text FROM messages WHERE conversation_id = ? ORDER BY created_at DESC LIMIT 10",
        (conv_id,)
    ).fetchall()
    recent_messages = [dict(r) for r in reversed(recent_rows)]

    pdf_context = ""
    pdf_sources = []
    if question:
        intent = detect_intent(question)
        if intent == "PDF" or attachment_type == "pdf":
            results = search_pdf(question) if question else []
            if results:
                parts_txt = []
                for r in results:
                    parts_txt.append(f"Source: {r['file']}\nPage: {r['page']}\n\n{r['text']}\n")
                    pdf_sources.append({"file": r["file"], "page": r["page"]})
                pdf_context = "\n".join(parts_txt)

    effective_question = question if question else "(User attached a file — please respond to it.)"
    answer = ask_ai(effective_question, recent_messages, pdf_context, image_path_for_ai)

    now = datetime.now().isoformat()
    db.execute(
        "INSERT INTO messages (id, conversation_id, role, text, attachment_path, attachment_type, sources, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (str(uuid.uuid4()), conv_id, "user", question, attachment_path, attachment_type, "", now)
    )
    db.execute(
        "INSERT INTO messages (id, conversation_id, role, text, attachment_path, attachment_type, sources, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (str(uuid.uuid4()), conv_id, "assistant", answer, None, None,
         ", ".join(f"{s['file']} p.{s['page']}" for s in pdf_sources), now)
    )
    db.commit()

    return jsonify({
        "reply": answer,
        "sources": pdf_sources,
        "conversation_id": conv_id,
        "attachment_path": attachment_path,
        "attachment_type": attachment_type
    })


if __name__ == "__main__":
    port = int(os.getenv("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=False)
