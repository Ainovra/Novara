import os
import sqlite3
from datetime import datetime

BASE = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DB = os.path.join(BASE, "novara.db")

print("=" * 60)
print("NOVARA PHASE 2 — BACKEND + DATABASE")
print("=" * 60)
print("Database:", DB)

os.makedirs(os.path.dirname(DB), exist_ok=True)

conn = sqlite3.connect(DB)
cur = conn.cursor()

# Core account/session data
cur.execute("""
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    username TEXT,
    email TEXT,
    password_hash TEXT,
    google_id TEXT,
    accepted_terms INTEGER DEFAULT 0,
    created_at TEXT
)
""")

# Conversations
cur.execute("""
CREATE TABLE IF NOT EXISTS conversations (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    title TEXT,
    created_at TEXT
)
""")

# Messages + persistent feedback
cur.execute("""
CREATE TABLE IF NOT EXISTS messages (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    role TEXT NOT NULL,
    text TEXT,
    attachment_path TEXT,
    attachment_type TEXT,
    sources TEXT,
    feedback TEXT,
    created_at TEXT
)
""")

# Persistent user settings
cur.execute("""
CREATE TABLE IF NOT EXISTS user_settings (
    user_id TEXT PRIMARY KEY,
    settings_json TEXT,
    updated_at TEXT
)
""")

# Application configuration / update information
cur.execute("""
CREATE TABLE IF NOT EXISTS app_config (
    key TEXT PRIMARY KEY,
    value TEXT,
    updated_at TEXT
)
""")

# Feedback audit/history
cur.execute("""
CREATE TABLE IF NOT EXISTS feedback_events (
    id TEXT PRIMARY KEY,
    message_id TEXT NOT NULL,
    user_id TEXT,
    feedback TEXT,
    created_at TEXT
)
""")

# Safe indexes
cur.execute("""
CREATE INDEX IF NOT EXISTS idx_conversations_user
ON conversations(user_id)
""")

cur.execute("""
CREATE INDEX IF NOT EXISTS idx_messages_conversation
ON messages(conversation_id)
""")

cur.execute("""
CREATE INDEX IF NOT EXISTS idx_feedback_message
ON feedback_events(message_id)
""")

# Version marker
cur.execute("""
INSERT INTO app_config(key,value,updated_at)
VALUES('schema_version','2',?)
ON CONFLICT(key) DO UPDATE SET
value='2',
updated_at=excluded.updated_at
""", (datetime.now().isoformat(),))

conn.commit()

tables = [
    "users",
    "conversations",
    "messages",
    "user_settings",
    "app_config",
    "feedback_events"
]

print()
print("DATABASE CHECK")
print("-" * 60)

for table in tables:
    cur.execute(
        "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
        (table,)
    )
    exists = cur.fetchone() is not None

    if exists:
        cur.execute(f"SELECT COUNT(*) FROM {table}")
        count = cur.fetchone()[0]
        print(f"[OK] {table}: {count} rows")
    else:
        print(f"[FAIL] {table}: NOT FOUND")

print()
print("=" * 60)
print("NOVARA PHASE 2 DATABASE SETUP SUCCESSFUL")
print("=" * 60)

conn.close()
