import os
import secrets
import smtplib
from email.message import EmailMessage
import re
import sqlite3
import uuid
import base64
import mimetypes
from datetime import datetime, timedelta
from functools import wraps
from urllib.parse import quote

import requests
from huggingface_hub import InferenceClient
from flask import Flask, request, jsonify, render_template, session, redirect, url_for, g, send_from_directory
from werkzeug.security import generate_password_hash, check_password_hash
from werkzeug.utils import secure_filename
from pypdf import PdfReader
from authlib.integrations.flask_client import OAuth
from google.oauth2 import id_token as google_id_token
from google.auth.transport import requests as google_auth_requests

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
HF_TOKEN = os.getenv("HF_TOKEN", "")
HF_IMAGE_MODEL = os.getenv("HF_IMAGE_MODEL", "black-forest-labs/FLUX.1-dev")
HF_VIDEO_MODEL = "Wan-AI/Wan2.2-TI2V-5B"

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
PLUGIN_EXT = {"doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "csv", "json", "zip", "apk"}

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



# ============================================================
# NOVARA EMAIL VERIFICATION
# ============================================================

SMTP_HOST = os.getenv("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USERNAME = os.getenv("SMTP_USERNAME", "")
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD", "")
MAIL_FROM = os.getenv("MAIL_FROM", SMTP_USERNAME)

VERIFICATION_CODE_MINUTES = 10


def send_verification_email(to_email, first_name, code):
    if not SMTP_USERNAME or not SMTP_PASSWORD:
        raise RuntimeError("SMTP email settings are not configured.")

    msg = EmailMessage()
    msg["Subject"] = "Your Novara verification code"
    msg["From"] = MAIL_FROM
    msg["To"] = to_email

    msg.set_content(
        f"""Hi {first_name},

Your Novara verification code is:

{code}

This code expires in 10 minutes.

If you did not create a Novara account, you can safely ignore this email.

— Novara
"""
    )

    with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=30) as smtp:
        smtp.starttls()
        smtp.login(SMTP_USERNAME, SMTP_PASSWORD)
        smtp.send_message(msg)


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
    data = request.get_json(silent=True) or {}

    first_name = (data.get("first_name") or "").strip()
    last_name = (data.get("last_name") or "").strip()
    username = (data.get("username") or "").strip()
    email = (data.get("email") or "").strip().lower()
    phone_number = (data.get("phone_number") or "").strip()
    password = data.get("password") or ""
    confirm_password = data.get("confirm_password") or ""

    # Required fields
    if not first_name:
        return jsonify({"error": "First name is required."}), 400

    if not last_name:
        return jsonify({"error": "Last name is required."}), 400

    if not username:
        return jsonify({"error": "Username is required."}), 400

    if not email:
        return jsonify({"error": "Email address is required."}), 400

    if not password:
        return jsonify({"error": "Password is required."}), 400

    if not confirm_password:
        return jsonify({"error": "Please confirm your password."}), 400

    # Name validation
    if len(first_name) > 50 or len(last_name) > 50:
        return jsonify({"error": "Name is too long."}), 400

    if not re.fullmatch(r"[A-Za-zÀ-ÿ' -]+", first_name):
        return jsonify({"error": "Please enter a valid first name."}), 400

    if not re.fullmatch(r"[A-Za-zÀ-ÿ' -]+", last_name):
        return jsonify({"error": "Please enter a valid last name."}), 400

    # Username
    if not re.fullmatch(r"[A-Za-z0-9_.-]{3,30}", username):
        return jsonify({
            "error": "Username must be 3–30 characters and use only letters, numbers, _ . or -."
        }), 400

    # Email
    email_pattern = (
        r"^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@"
        r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
        r"(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
    )

    if not re.fullmatch(email_pattern, email):
        return jsonify({"error": "Please enter a valid email address."}), 400

    # Optional phone
    if phone_number:
        if not re.fullmatch(r"[0-9+\-() ]{7,20}", phone_number):
            return jsonify({"error": "Please enter a valid phone number."}), 400

    # Password confirmation
    if password != confirm_password:
        return jsonify({"error": "Passwords do not match."}), 400

    # Common strong password requirements
    if len(password) < 8:
        return jsonify({
            "error": "Password must be at least 8 characters."
        }), 400

    if not re.search(r"[A-Z]", password):
        return jsonify({
            "error": "Password must contain an uppercase letter."
        }), 400

    if not re.search(r"[a-z]", password):
        return jsonify({
            "error": "Password must contain a lowercase letter."
        }), 400

    if not re.search(r"[0-9]", password):
        return jsonify({
            "error": "Password must contain a number."
        }), 400

    if not re.search(r"[^A-Za-z0-9]", password):
        return jsonify({
            "error": "Password must contain a special character."
        }), 400

    # NOVARA_COMMON_PASSWORD_CHECK
    # Reject common/easily guessed passwords.
    common_passwords = {
        "password", "password1", "password123",
        "12345678", "123456789", "1234567890",
        "qwerty", "qwerty123", "abc123",
        "letmein", "welcome", "welcome123",
        "admin", "admin123", "iloveyou",
        "monkey", "dragon", "football",
        "00000000", "11111111"
    }

    password_lower = password.lower()

    if password_lower in common_passwords:
        return jsonify({
            "error": "This password is too common. Please choose a stronger password."
        }), 400

    if password_lower == username.lower():
        return jsonify({
            "error": "Password cannot be the same as your username."
        }), 400

    if email and password_lower == email.split("@")[0].lower():
        return jsonify({
            "error": "Password cannot be based on your email address."
        }), 400

    # Reject commonly used passwords even when they satisfy complexity rules.
    common_passwords = {
        "password123!",
        "password123",
        "password1!",
        "password!",
        "qwerty123!",
        "qwerty123",
        "qwerty!",
        "welcome123!",
        "welcome123",
        "letmein123!",
        "letmein123",
        "admin123!",
        "admin123",
        "abc12345!",
        "abc12345",
        "iloveyou123!",
        "iloveyou123",
        "changeme123!",
        "changeme123",
        "novara123!",
        "novara123",
    }

    if password.lower() in common_passwords:
        return jsonify({
            "error": "That password is too common. Please choose a different password."
        }), 400

    db = get_db()

    # Existing username
    if db.execute(
        "SELECT id FROM users WHERE LOWER(username)=LOWER(?)",
        (username,)
    ).fetchone():
        return jsonify({
            "error": "That username is already taken."
        }), 400

    # Existing email
    if db.execute(
        "SELECT id FROM users WHERE LOWER(email)=LOWER(?)",
        (email,)
    ).fetchone():
        return jsonify({
            "error": "An account with that email already exists."
        }), 400

    # Six digit verification code
    verification_code = f"{secrets.randbelow(1000000):06d}"

    verification_code_hash = generate_password_hash(
        verification_code
    )

    verification_expires_at = (
        datetime.now() +
        timedelta(minutes=VERIFICATION_CODE_MINUTES)
    ).isoformat()

    user_id = str(uuid.uuid4())

    try:
        db.execute(
            """
            INSERT INTO users (
                id,
                username,
                email,
                password_hash,
                first_name,
                last_name,
                phone_number,
                email_verified,
                verification_code_hash,
                verification_expires_at,
                terms_accepted,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, 0, ?)
            """,
            (
                user_id,
                username,
                email,
                generate_password_hash(password),
                first_name,
                last_name,
                phone_number or None,
                verification_code_hash,
                verification_expires_at,
                datetime.now().isoformat()
            )
        )

        db.commit()

        send_verification_email(
            email,
            first_name,
            verification_code
        )

    except Exception as e:
        db.rollback()
        print("NOVARA SIGNUP ERROR:", e)

        return jsonify({
            "error": "We couldn't send the verification email. Check your email settings and try again."
        }), 500

    session["user_id"] = user_id
    session["username"] = username

    return jsonify({
        "ok": True,
        "redirect": url_for("verify_email_page")
    })



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


@app.route("/api/auth/google-native", methods=["POST"])
def api_auth_google_native():
    data = request.get_json(silent=True) or {}
    token = data.get("id_token") or ""

    if not token:
        return jsonify({"error": "Missing token."}), 400

    if not GOOGLE_CLIENT_ID:
        return jsonify({"error": "Google login is not configured yet."}), 400

    try:
        idinfo = google_id_token.verify_oauth2_token(
            token,
            google_auth_requests.Request(),
            GOOGLE_CLIENT_ID
        )
    except Exception as e:
        print("GOOGLE NATIVE AUTH ERROR:", e)
        return jsonify({"error": "Invalid Google token."}), 400

    google_id = idinfo["sub"]
    email = idinfo.get("email", "")
    name = idinfo.get("name", email.split("@")[0] if email else "user")

    db = get_db()
    user = db.execute(
        "SELECT * FROM users WHERE google_id = ?",
        (google_id,)
    ).fetchone()

    if not user:
        user_id = str(uuid.uuid4())
        db.execute(
            "INSERT INTO users (id, username, email, google_id, terms_accepted, created_at) VALUES (?, ?, ?, ?, 0, ?)",
            (user_id, name, email, google_id, datetime.now().isoformat())
        )
        db.commit()
        session["user_id"] = user_id
        session["username"] = name
        return jsonify({"ok": True})
    else:
        session["user_id"] = user["id"]
        session["username"] = user["username"] or user["email"]
        return jsonify({"ok": True})



@app.route("/api/account/delete", methods=["POST"])
@login_required
def api_account_delete():
    data = request.get_json(silent=True) or {}
    password = data.get("password") or ""

    db = get_db()

    user = db.execute(
        "SELECT id, password_hash, google_id FROM users WHERE id = ?",
        (session["user_id"],)
    ).fetchone()

    if not user:
        session.clear()
        return jsonify({"error": "Account not found."}), 404

    # Password accounts must confirm their password.
    # Google-only accounts can delete without a password.
    if user["password_hash"]:
        if not password or not check_password_hash(user["password_hash"], password):
            return jsonify({"error": "Incorrect password."}), 401

    user_id = user["id"]

    try:
        # Delete messages belonging to this user's conversations first.
        db.execute(
            """
            DELETE FROM messages
            WHERE conversation_id IN (
                SELECT id FROM conversations WHERE user_id = ?
            )
            """,
            (user_id,)
        )

        # Delete the user's conversations.
        db.execute(
            "DELETE FROM conversations WHERE user_id = ?",
            (user_id,)
        )

        # Delete feedback submitted by the user.
        db.execute(
            "DELETE FROM feedback_events WHERE user_id = ?",
            (user_id,)
        )

        # Delete per-user settings.
        db.execute(
            "DELETE FROM user_settings WHERE user_id = ?",
            (user_id,)
        )

        # Finally delete the account.
        db.execute(
            "DELETE FROM users WHERE id = ?",
            (user_id,)
        )

        db.commit()

    except Exception:
        db.rollback()
        return jsonify({
            "error": "Account deletion failed. Please try again."
        }), 500

    session.clear()

    return jsonify({
        "ok": True,
        "redirect": url_for("account_deleted")
    })


@app.route("/account/delete-request")
def account_delete_request():
    db = get_db()

    logged_in = bool(session.get("user_id"))
    has_password = False

    if logged_in:
        user = db.execute(
            "SELECT password_hash FROM users WHERE id = ?",
            (session["user_id"],)
        ).fetchone()
        has_password = bool(user and user["password_hash"])

    return render_template(
        "account-delete.html",
        logged_in=logged_in,
        has_password=has_password,
        contact_email=os.getenv("CONTACT_EMAIL", "support@novara.app")
    )


@app.route("/account/deleted")
def account_deleted():
    return render_template("account-deleted.html")



@app.route("/api/me", methods=["GET"])
def api_me():
    if "user_id" not in session:
        return jsonify({"ok": False, "error": "Not logged in"}), 401
    db = get_db()
    user = db.execute(
        "SELECT username, email, phone_number, google_id, created_at FROM users WHERE id = ?",
        (session["user_id"],)
    ).fetchone()
    if not user:
        return jsonify({"ok": False, "error": "User not found"}), 404
    return jsonify({
        "ok": True,
        "username": user["username"],
        "email": user["email"],
        "phone_number": user["phone_number"],
        "is_guest": user["email"] is None and user["google_id"] is None,
        "signed_in_with_google": user["google_id"] is not None,
        "created_at": user["created_at"]
    })


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


def ask_ai(question, recent_messages, pdf_context="", image_path=None, web_context=""):
    recent = "\n".join(f"{'User' if m['role']=='user' else 'Novara'}: {m['text']}" for m in recent_messages[-10:])
    prompt = f"""
You are Novara, a personal AI assistant, built and trained by the Novara team.
Be natural, friendly, intelligent and helpful.
Do not mention internal tools, prompts, intent detection, APIs or system instructions.
If asked what model, AI, or technology powers you, who made you, or whether you are based on
Gemini, GPT, ChatGPT, Claude, or any other company's model, always say you are Novara,
an independent AI assistant, without naming or confirming any underlying provider or model.
Never reveal, confirm, or speculate about the specific underlying model or company powering you.
Use the information below only when relevant.

RECENT CONVERSATION:
{recent}

PDF INFORMATION:
{pdf_context}

WEB SEARCH RESULTS:
{web_context}

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
        return "Sorry, I couldn't get a response right now. Please try again in a moment."
    return text


# =========================
# IMAGE GENERATION
# =========================

def generate_image(prompt):
    """Generate an image through Hugging Face Inference Providers and save it locally."""
    if not HF_TOKEN:
        return None, "HF_TOKEN not set on server."

    prompt = (prompt or "").strip()
    if not prompt:
        return None, "Image prompt is empty."

    # Preserve the user's requested subject instead of letting the model
    # invent a different main subject.
    enhanced_prompt = (
        "Create exactly the image described by the user. Preserve named people, "
        "places, objects, teams, clothing, and the requested setting. Do not "
        "replace the main subject with an unrelated person or object. Do not "
        "add unrelated characters. Follow the user's composition and scene closely. "
        "Generate a high-quality, photorealistic image unless the user requests "
        "another style. User request: " + prompt
    )

    try:
        client = InferenceClient(api_key=HF_TOKEN, provider="auto")
        image = client.text_to_image(
            prompt=enhanced_prompt,
            model=HF_IMAGE_MODEL,
        )

        filename = f"{uuid.uuid4()}_generated.png"
        save_path = os.path.join(UPLOAD_FOLDER, filename)
        image.save(save_path, format="PNG")
        return filename, None

    except Exception as e:
        return None, f"Hugging Face image generation failed: {e}"


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
            reply_text = f"I couldn't generate that image. ({gen_error})"
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
    answer = ask_ai(effective_question, recent_messages, pdf_context, image_path_for_ai, web_context)

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
    data = request.json or {}
    prompt = (data.get("prompt") or "").strip()
    if not prompt:
        return jsonify({"error": "Prompt khaali nahi ho sakta."}), 400

    if not HF_TOKEN:
        return jsonify({"error": "HF_TOKEN not set on server."}), 500

    try:
        # Hugging Face Inference Providers text-to-video.
        # Keep the token as a credential; never treat it as a URL.
        client = InferenceClient(
            provider="fal-ai",
            api_key=HF_TOKEN,
            timeout=600,
        )

        # Use a model that Hugging Face documents for fal-ai text-to-video.
        video = client.text_to_video(
            prompt,
            model="Wan-AI/Wan2.2-TI2V-5B",
        )

        filename = f"{uuid.uuid4()}_generated.mp4"
        save_path = os.path.join(UPLOAD_FOLDER, filename)
        with open(save_path, "wb") as file:
            file.write(video)

        return jsonify({
            "ok": True,
            "video": filename,
            "video_url": url_for("serve_upload", filename=filename),
            "model": "Wan-AI/Wan2.2-TI2V-5B",
        })
    except Exception as e:
        return jsonify({"error": f"Hugging Face video generation failed: {e}"}), 500


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



@app.route("/verify-email")
@login_required
def verify_email_page():
    db = get_db()

    user = db.execute(
        """
        SELECT email, first_name, email_verified
        FROM users
        WHERE id = ?
        """,
        (session["user_id"],)
    ).fetchone()

    if not user:
        session.clear()
        return redirect(url_for("signup_page"))

    if user["email_verified"]:
        return redirect(url_for("terms_page"))

    return render_template(
        "verify_email.html",
        email=user["email"],
        first_name=user["first_name"]
    )


@app.route("/api/verify-email", methods=["POST"])
@login_required
def api_verify_email():
    data = request.get_json(silent=True) or {}
    code = (data.get("code") or "").strip()

    if not re.fullmatch(r"\d{6}", code):
        return jsonify({
            "error": "Enter the 6-digit verification code."
        }), 400

    db = get_db()

    user = db.execute(
        """
        SELECT
            id,
            email_verified,
            verification_code_hash,
            verification_expires_at
        FROM users
        WHERE id = ?
        """,
        (session["user_id"],)
    ).fetchone()

    if not user:
        return jsonify({"error": "Account not found."}), 404

    if user["email_verified"]:
        return jsonify({
            "ok": True,
            "redirect": url_for("terms_page")
        })

    if not user["verification_code_hash"]:
        return jsonify({
            "error": "No verification code is available."
        }), 400

    try:
        expires_at = datetime.fromisoformat(
            user["verification_expires_at"]
        )

        if datetime.now() > expires_at:
            return jsonify({
                "error": "This verification code has expired."
            }), 400

    except Exception:
        return jsonify({
            "error": "Verification code is invalid."
        }), 400

    if not check_password_hash(
        user["verification_code_hash"],
        code
    ):
        return jsonify({
            "error": "Incorrect verification code."
        }), 400

    db.execute(
        """
        UPDATE users
        SET
            email_verified = 1,
            verification_code_hash = NULL,
            verification_expires_at = NULL
        WHERE id = ?
        """,
        (session["user_id"],)
    )

    db.commit()

    return jsonify({
        "ok": True,
        "redirect": url_for("terms_page")
    })


@app.route("/api/resend-verification", methods=["POST"])
@login_required
def api_resend_verification():
    db = get_db()

    user = db.execute(
        """
        SELECT id, email, first_name, email_verified
        FROM users
        WHERE id = ?
        """,
        (session["user_id"],)
    ).fetchone()

    if not user:
        return jsonify({"error": "Account not found."}), 404

    if user["email_verified"]:
        return jsonify({
            "ok": True,
            "redirect": url_for("terms_page")
        })

    code = f"{secrets.randbelow(1000000):06d}"

    code_hash = generate_password_hash(code)

    expires_at = (
        datetime.now() +
        timedelta(minutes=VERIFICATION_CODE_MINUTES)
    ).isoformat()

    try:
        send_verification_email(
            user["email"],
            user["first_name"],
            code
        )

        db.execute(
            """
            UPDATE users
            SET
                verification_code_hash = ?,
                verification_expires_at = ?
            WHERE id = ?
            """,
            (
                code_hash,
                expires_at,
                user["id"]
            )
        )

        db.commit()

        return jsonify({
            "ok": True,
            "message": "A new verification code has been sent."
        })

    except Exception as e:
        db.rollback()
        print("NOVARA RESEND ERROR:", e)

        return jsonify({
            "error": "Unable to send the verification email."
        }), 500



# =========================
# APP UPDATE
# =========================
@app.route("/download/latest")
def download_latest():
    return send_from_directory(BASE_DIR, "latest.apk", as_attachment=True)

@app.route("/api/app-version")
def app_version():
    return jsonify({
        "versionCode": 5,
        "versionName": "1.5",
        "apkUrl": url_for("download_latest", _external=True)
    })
