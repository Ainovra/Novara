import os
import re
import sqlite3
import uuid
import base64
import mimetypes
from datetime import datetime
from functools import wraps
from urllib.parse import quote

import requests
from flask import Flask, request, jsonify, render_template, session, redirect, url_for, g, send_from_directory
from werkzeug.security import generate_password_hash, check_password_hash
from werkzeug.utils import secure_filename
from pypdf import PdfReader
from authlib.integrations.flask_client import OAuth

try:
    import psycopg2
    import psycopg2.extras
    PSYCOPG2_AVAILABLE = True
except ImportError:
    PSYCOPG2_AVAILABLE = False

# =========================
# Novara — Flask backend
# =========================

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
ADMIN_SECRET = os.getenv("ADMIN_SECRET", "")
GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID", "")
GOOGLE_CLIENT_SECRET = os.getenv("GOOGLE_CLIENT_SECRET", "")
DATABASE_URL = os.getenv("DATABASE_URL", "")  # set this to use permanent PostgreSQL instead of local SQLite
# Optional: plug in a real video-generation provider later (see README "Video generation").
VIDEO_API_URL = os.getenv("VIDEO_API_URL", "")
VIDEO_API_KEY = os.getenv("VIDEO_API_KEY", "")
MODEL = "gemini-3.5-flash"
THINKING_MODEL = os.getenv("THINKING_MODEL", "gemini-3.5-pro")
OMEGA_MODEL = os.getenv("OMEGA_MODEL", "gemini-3.5-pro")
IMAGE_MODEL = os.getenv("IMAGE_MODEL", "gemini-2.5-flash-image")

# ---- Groq (free, genuinely distinct models for text chat) ----
GROQ_API_KEY = os.getenv("GROQ_API_KEY", "")
GROQ_FAST_MODEL = os.getenv("GROQ_FAST_MODEL", "openai/gpt-oss-20b")
GROQ_THINKING_MODEL = os.getenv("GROQ_THINKING_MODEL", "qwen/qwen3.6-27b")
GROQ_OMEGA_MODEL = os.getenv("GROQ_OMEGA_MODEL", "openai/gpt-oss-120b")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "novara.db")
UPLOAD_FOLDER = os.path.join(BASE_DIR, "uploads")

if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

app = Flask(__name__)
app.secret_key = os.getenv("FLASK_SECRET", "change-this-secret-in-production")
app.config["MAX_CONTENT_LENGTH"] = 20 * 1024 * 1024  # 20MB max upload

IMAGE_EXT = {"png", "jpg", "jpeg", "webp", "gif", "bmp", "heic"}
VIDEO_EXT = {"mp4", "mov", "webm", "mkv", "avi"}
DOC_EXT = {"pdf"}
PLUGIN_EXT = {"doc", "docx", "ppt", "pptx", "xls", "xlsx", "zip", "apk"}
# Plain-text/code files we can read directly and hand to the AI as context —
# previously any non-image/PDF attachment was saved but never actually read.
TEXT_READABLE_EXT = {
    "txt", "csv", "json", "md", "log", "ini", "yaml", "yml", "xml",
    "py", "js", "jsx", "ts", "tsx", "html", "htm", "css", "java", "c", "cpp",
    "h", "cs", "php", "rb", "go", "rs", "swift", "kt", "sql", "sh", "bash"
}

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

class PGConnWrapper:
    """Makes a psycopg2 connection behave like sqlite3's connection.execute()
    shorthand, so the rest of the app's code (written for SQLite) doesn't
    need to change. Also translates '?' placeholders to '%s' automatically."""

    def __init__(self, conn):
        self._conn = conn

    def execute(self, query, params=()):
        cur = self._conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
        cur.execute(query.replace("?", "%s"), params)
        return cur

    def commit(self):
        self._conn.commit()

    def close(self):
        self._conn.close()


def get_db():
    if "db" not in g:
        if DATABASE_URL and PSYCOPG2_AVAILABLE:
            raw_conn = psycopg2.connect(DATABASE_URL)
            g.db = PGConnWrapper(raw_conn)
        else:
            g.db = sqlite3.connect(DB_PATH)
            g.db.row_factory = sqlite3.Row
    return g.db


def using_postgres():
    return bool(DATABASE_URL and PSYCOPG2_AVAILABLE)


@app.teardown_appcontext
def close_db(exception):
    db = g.pop("db", None)
    if db is not None:
        db.close()


def _add_column_if_missing(conn, table, column, coltype):
    if using_postgres():
        # Postgres supports this directly — no need to check first.
        conn.execute(f"ALTER TABLE {table} ADD COLUMN IF NOT EXISTS {column} {coltype}")
    else:
        cols = [r[1] for r in conn.execute(f"PRAGMA table_info({table})").fetchall()]
        if column not in cols:
            conn.execute(f"ALTER TABLE {table} ADD COLUMN {column} {coltype}")


def init_db():
    if using_postgres():
        raw_conn = psycopg2.connect(DATABASE_URL)
        conn = PGConnWrapper(raw_conn)
    else:
        conn = sqlite3.connect(DB_PATH)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            username TEXT UNIQUE,
            email TEXT UNIQUE,
            google_id TEXT UNIQUE,
            password_hash TEXT,
            terms_accepted INTEGER DEFAULT 0,
            created_at TEXT NOT NULL
        )
    """)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS conversations (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            title TEXT NOT NULL,
            share_id TEXT,
            is_shared INTEGER DEFAULT 0,
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
            feedback TEXT,
            created_at TEXT NOT NULL
        )
    """)
    # Backfill columns for databases created before these features existed.
    _add_column_if_missing(conn, "users", "terms_accepted", "INTEGER DEFAULT 0")
    _add_column_if_missing(conn, "users", "phone_number", "TEXT")
    _add_column_if_missing(conn, "conversations", "share_id", "TEXT")
    _add_column_if_missing(conn, "conversations", "is_shared", "INTEGER DEFAULT 0")
    _add_column_if_missing(conn, "messages", "feedback", "TEXT")
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


def terms_required(f):
    """Blocks access until the user has ticked the Terms & Conditions checkbox."""
    @wraps(f)
    def wrapper(*args, **kwargs):
        db = get_db()
        user = db.execute("SELECT terms_accepted FROM users WHERE id = ?", (session["user_id"],)).fetchone()
        if not user or not user["terms_accepted"]:
            return redirect(url_for("terms_page"))
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
# WELCOME / INTRODUCTION
# =========================

@app.route("/welcome")
def welcome_page():
    if session.get("user_id"):
        return redirect(url_for("index"))
    return render_template("welcome.html")


@app.route("/api/guest", methods=["POST"])
def api_guest():
    """Lets someone use Novara without creating an account. A lightweight
    guest user is created behind the scenes so conversations still save
    normally for this browser session."""
    db = get_db()
    guest_name = "Guest-" + uuid.uuid4().hex[:6]
    user_id = str(uuid.uuid4())
    db.execute(
        "INSERT INTO users (id, username, terms_accepted, created_at) VALUES (?, ?, 0, ?)",
        (user_id, guest_name, datetime.now().isoformat())
    )
    db.commit()
    session["user_id"] = user_id
    session["username"] = guest_name
    return jsonify({"ok": True, "redirect": url_for("terms_page")})


# =========================
# TERMS & CONDITIONS
# =========================

@app.route("/terms")
@login_required
def terms_page():
    db = get_db()
    user = db.execute("SELECT terms_accepted FROM users WHERE id = ?", (session["user_id"],)).fetchone()
    if user and user["terms_accepted"]:
        return redirect(url_for("index"))
    return render_template("terms.html")


@app.route("/api/accept-terms", methods=["POST"])
@login_required
def api_accept_terms():
    db = get_db()
    db.execute("UPDATE users SET terms_accepted = 1 WHERE id = ?", (session["user_id"],))
    db.commit()
    return jsonify({"ok": True})


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
        "INSERT INTO users (id, username, password_hash, terms_accepted, created_at) VALUES (?, ?, ?, 0, ?)",
        (user_id, username, generate_password_hash(password), datetime.now().isoformat())
    )
    db.commit()
    session["user_id"] = user_id
    session["username"] = username
    return jsonify({"ok": True, "redirect": url_for("terms_page")})


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
    next_url = url_for("index") if user["terms_accepted"] else url_for("terms_page")
    return jsonify({"ok": True, "redirect": next_url})


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
            "INSERT INTO users (id, username, email, google_id, terms_accepted, created_at) VALUES (?, ?, ?, ?, 0, ?)",
            (user_id, name, email, google_id, datetime.now().isoformat())
        )
        db.commit()
        session["user_id"] = user_id
        session["username"] = name
        return redirect(url_for("terms_page"))
    else:
        session["user_id"] = user["id"]
        session["username"] = user["username"] or user["email"]
        return redirect(url_for("index") if user["terms_accepted"] else url_for("terms_page"))


@app.route("/logout")
def logout():
    session.clear()
    return redirect(url_for("login_page"))


# =========================
# PROFILE / SETTINGS
# =========================

@app.route("/profile")
@login_required
def profile_page():
    db = get_db()
    user = db.execute(
        "SELECT username, email, created_at FROM users WHERE id = ?", (session["user_id"],)
    ).fetchone()
    return render_template("profile.html", user=user, username=session.get("username"))


@app.route("/api/account/delete", methods=["POST"])
@login_required
def api_delete_account():
    user_id = session["user_id"]
    db = get_db()

    conv_rows = db.execute("SELECT id FROM conversations WHERE user_id = ?", (user_id,)).fetchall()
    for c in conv_rows:
        db.execute("DELETE FROM messages WHERE conversation_id = ?", (c["id"],))
    db.execute("DELETE FROM conversations WHERE user_id = ?", (user_id,))
    db.execute("DELETE FROM users WHERE id = ?", (user_id,))
    db.commit()

    session.clear()
    return jsonify({"ok": True, "redirect": url_for("login_page")})


# =========================
# LEGAL — public, read-only (the acceptance-required version lives at /terms)
# =========================

@app.route("/legal/terms")
def legal_terms_page():
    return render_template("legal_terms.html")


@app.route("/legal/privacy")
def legal_privacy_page():
    return render_template("legal_privacy.html")


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
# WEB SEARCH (no API key needed — DuckDuckGo HTML results)
# For higher quality results, swap this out for a paid search API
# (Serper, Tavily, Bing) — see README "Web search" section.
# =========================

def search_web(query, max_results=5):
    try:
        resp = requests.get(
            f"https://html.duckduckgo.com/html/?q={quote(query)}",
            headers={"User-Agent": "Mozilla/5.0 (compatible; NovaraBot/1.0)"},
            timeout=8,
        )
        results = []
        # Lightweight scrape — no external HTML parser dependency required.
        blocks = re.findall(
            r'result__a"[^>]*>(.*?)</a>.*?result__snippet[^>]*>(.*?)</a>',
            resp.text, re.S
        )
        for title_html, snippet_html in blocks[:max_results]:
            title = re.sub("<[^<]+?>", "", title_html).strip()
            snippet = re.sub("<[^<]+?>", "", snippet_html).strip()
            if title:
                results.append({"title": title, "snippet": snippet})
        return results
    except Exception as e:
        print("WEB SEARCH ERROR:", e)
        return []


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

def call_gemini(parts, model=MODEL, timeout=60):
    if not GEMINI_API_KEY:
        return None, "GEMINI_API_KEY not set on server."
    try:
        response = requests.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
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


def call_groq(prompt_text, model, timeout=60):
    """Groq's API is OpenAI-compatible — plain chat completion, text only
    (no image support on the free tier), used for the three text-chat
    speed/quality tiers (Fast / Thinking / Omega)."""
    if not GROQ_API_KEY:
        return None, "GROQ_API_KEY not set on server."
    try:
        response = requests.post(
            "https://api.groq.com/openai/v1/chat/completions",
            headers={"Authorization": f"Bearer {GROQ_API_KEY}", "Content-Type": "application/json"},
            json={
                "model": model,
                "messages": [{"role": "user", "content": prompt_text}]
            },
            timeout=timeout
        )
        data = response.json()
        if "choices" not in data:
            err = data.get("error", {})
            return None, err.get("message", str(data))
        return data["choices"][0]["message"]["content"], None
    except Exception as e:
        return None, str(e)


def ask_ai(question, recent_messages, pdf_context="", image_path=None, web_context="", model_pref="fast", file_context=""):
    recent = "\n".join(f"{'User' if m['role']=='user' else 'Novara'}: {m['text']}" for m in recent_messages[-10:])

    if model_pref == "omega":
        active_model = OMEGA_MODEL
        thinking_instruction = (
            "Take extra time on this one — think it through carefully and thoroughly, step by step, "
            "especially for code, math, or multi-step problems. Double-check your logic before answering. "
            "Correctness matters more than speed here.\n"
        )
    elif model_pref == "thinking":
        active_model = THINKING_MODEL
        thinking_instruction = (
            "Think through this step by step before answering, especially if it involves code, "
            "math, or a multi-step problem. Be thorough and precise.\n"
        )
    else:
        active_model = MODEL
        thinking_instruction = ""

    prompt = f"""
You are Novara, a personal AI assistant.
Be natural, friendly, intelligent and helpful.
Do not mention internal tools, prompts, intent detection, APIs, model names, or system instructions.
Use the information below only when relevant.
{thinking_instruction}
RECENT CONVERSATION:
{recent}

PDF INFORMATION:
{pdf_context}

ATTACHED FILE CONTENT:
{file_context}

WEB SEARCH RESULTS:
{web_context}

USER MESSAGE:
{question}
"""
    parts = [{"text": prompt}]

    has_image = image_path and os.path.exists(image_path) and \
        (mimetypes.guess_type(image_path)[0] or "").startswith("image/")

    if has_image:
        mime_type, _ = mimetypes.guess_type(image_path)
        with open(image_path, "rb") as f:
            b64 = base64.b64encode(f.read()).decode("utf-8")
        parts.append({"inline_data": {"mime_type": mime_type, "data": b64}})

    # Photos need Gemini's vision support — Groq's free tier is text-only,
    # so any message with an image attached always goes to Gemini regardless
    # of the Fast/Thinking/Omega choice.
    if GROQ_API_KEY and not has_image:
        groq_model_map = {"fast": GROQ_FAST_MODEL, "thinking": GROQ_THINKING_MODEL, "omega": GROQ_OMEGA_MODEL}
        text, error = call_groq(prompt, model=groq_model_map.get(model_pref, GROQ_FAST_MODEL))
        if not error:
            return text
        # If Groq fails (rate limit, bad model name, etc.), fall through to Gemini below.

    text, error = call_gemini(parts, model=active_model)
    if error and model_pref in ("omega", "thinking"):
        # Fall back to the fast model if the bigger model isn't available on this API key/plan.
        text, error = call_gemini(parts, model=MODEL)
    if error:
        return f"Sorry, I couldn't get a response right now. ({error})"
    return text


# =========================
# IMAGE GENERATION
# =========================

def generate_image(prompt):
    """
    Generates an image and saves it to the uploads folder. Returns (filename, error_message).

    Primary: Pollinations.ai — genuinely free, no API key needed at all.
    Fallback: Gemini's image model, in case you ever enable billing on your
    Google account (Gemini's free tier does not include image generation).
    """
    # ---- Primary: Pollinations (free, no key) ----
    try:
        encoded_prompt = requests.utils.quote(prompt)
        seed = uuid.uuid4().int % 1_000_000
        url = f"https://image.pollinations.ai/prompt/{encoded_prompt}?width=1024&height=1024&seed={seed}&nologo=true"

        response = requests.get(url, timeout=60)
        if response.status_code == 200 and response.content:
            content_type = response.headers.get("Content-Type", "image/jpeg")
            ext = content_type.split("/")[-1].split(";")[0] or "jpg"
            if ext not in ("jpeg", "jpg", "png", "webp"):
                ext = "jpg"
            filename = f"{uuid.uuid4()}_generated.{ext}"
            save_path = os.path.join(UPLOAD_FOLDER, filename)
            with open(save_path, "wb") as f:
                f.write(response.content)
            return filename, None
    except Exception:
        pass  # fall through to Gemini below

    # ---- Fallback: Gemini (only works if billing is enabled on your Google account) ----
    if not GEMINI_API_KEY:
        return None, "Image generation is temporarily unavailable — please try again in a moment."

    try:
        response = requests.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{IMAGE_MODEL}:generateContent",
            headers={"x-goog-api-key": GEMINI_API_KEY, "Content-Type": "application/json"},
            json={
                "contents": [{"role": "user", "parts": [{"text": prompt}]}],
                "generationConfig": {"responseModalities": ["TEXT", "IMAGE"]}
            },
            timeout=90
        )
        data = response.json()

        if "candidates" not in data:
            return None, "Image generation is temporarily unavailable — please try again in a moment."

        parts = data["candidates"][0]["content"]["parts"]
        for part in parts:
            inline = part.get("inline_data") or part.get("inlineData")
            if inline:
                mime_type = inline.get("mime_type") or inline.get("mimeType", "image/png")
                ext = mime_type.split("/")[-1].split("+")[0] or "png"
                b64_data = inline.get("data", "")
                filename = f"{uuid.uuid4()}_generated.{ext}"
                save_path = os.path.join(UPLOAD_FOLDER, filename)
                with open(save_path, "wb") as f:
                    f.write(base64.b64decode(b64_data))
                return filename, None

        return None, "Couldn't generate an image for that prompt — try rephrasing it."

    except Exception:
        return None, "Image generation is temporarily unavailable — please try again in a moment."


# =========================
# MAIN APP ROUTES
# =========================

@app.route("/")
def root():
    if not session.get("user_id"):
        return redirect(url_for("welcome_page"))
    return redirect(url_for("index"))


@app.route("/app")
@login_required
@terms_required
def index():
    return render_template("index.html", username=session.get("username"))


@app.route("/uploads/<path:filename>")
def serve_upload(filename):
    # Filenames are random UUIDs (unguessable), so this can be public —
    # that's required for "Save image" to work. Android's download manager
    # (used for long-press-save) does NOT send the browser's login cookie,
    # so keeping this behind @login_required silently broke every save.
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
        "SELECT id, role, text, attachment_path, attachment_type, sources, feedback FROM messages WHERE conversation_id = ? ORDER BY created_at ASC",
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


@app.route("/api/conversations/<conv_id>/rename", methods=["POST"])
@login_required
def api_rename_conversation(conv_id):
    data = request.json or {}
    title = (data.get("title") or "").strip()
    if not title:
        return jsonify({"error": "Naam khaali nahi ho sakta."}), 400
    title = title[:60]
    db = get_db()
    conv = db.execute(
        "SELECT id FROM conversations WHERE id = ? AND user_id = ?", (conv_id, session["user_id"])
    ).fetchone()
    if not conv:
        return jsonify({"error": "Not found"}), 404
    db.execute("UPDATE conversations SET title = ? WHERE id = ?", (title, conv_id))
    db.commit()
    return jsonify({"ok": True, "title": title})


@app.route("/api/conversations/<conv_id>/share", methods=["POST"])
@login_required
def api_share_conversation(conv_id):
    db = get_db()
    conv = db.execute(
        "SELECT id, share_id FROM conversations WHERE id = ? AND user_id = ?", (conv_id, session["user_id"])
    ).fetchone()
    if not conv:
        return jsonify({"error": "Not found"}), 404

    share_id = conv["share_id"] or uuid.uuid4().hex[:12]
    db.execute("UPDATE conversations SET share_id = ?, is_shared = 1 WHERE id = ?", (share_id, conv_id))
    db.commit()
    return jsonify({"ok": True, "share_url": url_for("view_shared", share_id=share_id, _external=True)})


@app.route("/api/conversations/<conv_id>/unshare", methods=["POST"])
@login_required
def api_unshare_conversation(conv_id):
    db = get_db()
    db.execute(
        "UPDATE conversations SET is_shared = 0 WHERE id = ? AND user_id = ?", (conv_id, session["user_id"])
    )
    db.commit()
    return jsonify({"ok": True})


@app.route("/share/<share_id>")
def view_shared(share_id):
    db = get_db()
    conv = db.execute(
        "SELECT id, title FROM conversations WHERE share_id = ? AND is_shared = 1", (share_id,)
    ).fetchone()
    if not conv:
        return render_template("share.html", not_found=True, title=None, messages=[])
    rows = db.execute(
        "SELECT role, text, attachment_type FROM messages WHERE conversation_id = ? ORDER BY created_at ASC",
        (conv["id"],)
    ).fetchall()
    return render_template("share.html", not_found=False, title=conv["title"], messages=[dict(r) for r in rows])


@app.route("/api/messages/<message_id>/feedback", methods=["POST"])
@login_required
def api_message_feedback(message_id):
    data = request.json or {}
    feedback = data.get("feedback")  # "like", "dislike", or null to clear
    if feedback not in ("like", "dislike", None):
        return jsonify({"error": "Invalid feedback"}), 400
    db = get_db()
    db.execute("UPDATE messages SET feedback = ? WHERE id = ?", (feedback, message_id))
    db.commit()
    return jsonify({"ok": True})


@app.route("/api/chat", methods=["POST"])
@login_required
@terms_required
def api_chat():
    question = request.form.get("message", "").strip()
    conv_id = request.form.get("conversation_id") or None
    force_search = request.form.get("web_search") == "true"
    force_image_gen = request.form.get("image_gen") == "true"
    model_pref = request.form.get("model", "fast")
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

    # ---- Image generation path ----
    if force_image_gen and question:
        image_filename, gen_error = generate_image(question)
        now = datetime.now().isoformat()

        db.execute(
            "INSERT INTO messages (id, conversation_id, role, text, attachment_path, attachment_type, sources, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (str(uuid.uuid4()), conv_id, "user", question, None, None, "", now)
        )

        assistant_msg_id = str(uuid.uuid4())
        if gen_error:
            reply_text = gen_error
            db.execute(
                "INSERT INTO messages (id, conversation_id, role, text, attachment_path, attachment_type, sources, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (assistant_msg_id, conv_id, "assistant", reply_text, None, None, "", now)
            )
            db.commit()
            return jsonify({"reply": reply_text, "message_id": assistant_msg_id, "conversation_id": conv_id})

        reply_text = "Here's what I created:"
        db.execute(
            "INSERT INTO messages (id, conversation_id, role, text, attachment_path, attachment_type, sources, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (assistant_msg_id, conv_id, "assistant", reply_text, image_filename, "image", "", now)
        )
        db.commit()
        return jsonify({
            "reply": reply_text,
            "message_id": assistant_msg_id,
            "conversation_id": conv_id,
            "generated_image": image_filename
        })

    attachment_path = None
    attachment_type = None
    image_path_for_ai = None
    file_text_context = ""

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
        elif ext in TEXT_READABLE_EXT:
            attachment_type = "file"
            try:
                with open(save_path, "r", encoding="utf-8", errors="replace") as f:
                    raw_text = f.read()
                # Cap it so one huge file can't blow up the prompt — plenty for
                # a code file, README, or CSV; longer files just get truncated.
                if len(raw_text) > 15000:
                    raw_text = raw_text[:15000] + "\n\n[...truncated, file is longer than shown...]"
                file_text_context = f"Filename: {file.filename}\n\n{raw_text}"
            except Exception as e:
                print("FILE READ ERROR:", e)
        elif ext in PLUGIN_EXT:
            attachment_type = "file"
        else:
            attachment_type = "file"

    recent_rows = db.execute(
        "SELECT role, text FROM messages WHERE conversation_id = ? ORDER BY created_at DESC LIMIT 10",
        (conv_id,)
    ).fetchall()
    recent_messages = [dict(r) for r in reversed(recent_rows)]

    pdf_context = ""
    pdf_sources = []
    web_context = ""
    web_sources = []

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

        if force_search or intent == "WEB":
            web_results = search_web(question)
            if web_results:
                web_context = "\n".join(f"- {r['title']}: {r['snippet']}" for r in web_results)
                web_sources = web_results

    effective_question = question if question else "(User attached a file — please respond to it.)"
    answer = ask_ai(effective_question, recent_messages, pdf_context, image_path_for_ai, web_context, model_pref, file_text_context)

    now = datetime.now().isoformat()
    db.execute(
        "INSERT INTO messages (id, conversation_id, role, text, attachment_path, attachment_type, sources, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (str(uuid.uuid4()), conv_id, "user", question, attachment_path, attachment_type, "", now)
    )
    assistant_msg_id = str(uuid.uuid4())
    combined_sources = ", ".join(f"{s['file']} p.{s['page']}" for s in pdf_sources)
    if web_sources:
        combined_sources = (combined_sources + " | " if combined_sources else "") + ", ".join(s["title"] for s in web_sources)

    db.execute(
        "INSERT INTO messages (id, conversation_id, role, text, attachment_path, attachment_type, sources, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (assistant_msg_id, conv_id, "assistant", answer, None, None, combined_sources, now)
    )
    db.commit()

    return jsonify({
        "reply": answer,
        "message_id": assistant_msg_id,
        "sources": pdf_sources,
        "web_sources": web_sources,
        "conversation_id": conv_id,
        "attachment_path": attachment_path,
        "attachment_type": attachment_type
    })


# =========================
# VIDEO STUDIO (generation + editing)
# Not wired to a provider yet — plug in VIDEO_API_URL / VIDEO_API_KEY
# once you pick a video-gen service. Until then these return a clear
# "not configured" message instead of failing silently.
# =========================

@app.route("/video")
@login_required
@terms_required
def video_studio():
    return render_template("video.html", username=session.get("username"), video_enabled=bool(VIDEO_API_URL))


@app.route("/api/video/generate", methods=["POST"])
@login_required
def api_video_generate():
    if not VIDEO_API_URL:
        return jsonify({"error": "Video generation abhi configure nahi hai. VIDEO_API_URL aur VIDEO_API_KEY environment variables set karein (README dekhein)."}), 501
    data = request.json or {}
    prompt = (data.get("prompt") or "").strip()
    if not prompt:
        return jsonify({"error": "Prompt khaali nahi ho sakta."}), 400
    try:
        resp = requests.post(
            VIDEO_API_URL,
            headers={"Authorization": f"Bearer {VIDEO_API_KEY}", "Content-Type": "application/json"},
            json={"prompt": prompt, "aspect_ratio": data.get("aspect_ratio", "16:9"), "duration": data.get("duration", 5)},
            timeout=120
        )
        return jsonify(resp.json())
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/video/edit", methods=["POST"])
@login_required
def api_video_edit():
    if not VIDEO_API_URL:
        return jsonify({"error": "Video editing abhi configure nahi hai. VIDEO_API_URL aur VIDEO_API_KEY environment variables set karein (README dekhein)."}), 501
    file = request.files.get("file")
    action = request.form.get("action", "enhance")
    if not file:
        return jsonify({"error": "Koi video file nahi mili."}), 400
    return jsonify({"error": "Ye provider ke hisaab se implement karna hoga — README ka 'Video editing' section dekhein."}), 501


if __name__ == "__main__":
    port = int(os.getenv("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=False)
