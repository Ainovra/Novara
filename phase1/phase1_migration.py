import os
import re
import sqlite3
import shutil
from datetime import datetime

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(BASE, "app.py")

print("=" * 60)
print("NOVARA PHASE 1")
print("=" * 60)

# ------------------------------------------------------------
# 1. Discover SQLite database used by app.py
# ------------------------------------------------------------
source = open(APP, "r", encoding="utf-8").read()

candidates = []

patterns = [
    r'["\']([^"\']+\.db)["\']',
    r'["\']([^"\']+\.sqlite)["\']',
    r'["\']([^"\']+\.sqlite3)["\']',
]

for pattern in patterns:
    for match in re.findall(pattern, source):
        path = match
        if not os.path.isabs(path):
            path = os.path.join(BASE, path)
        candidates.append(os.path.abspath(path))

db_path = None

for path in candidates:
    if os.path.exists(path):
        db_path = path
        break

if db_path is None:
    for root, dirs, files in os.walk(BASE):
        dirs[:] = [
            d for d in dirs
            if d not in {".git", ".gradle", "build", "__pycache__", "phase1_backup"}
        ]
        for name in files:
            if name.endswith((".db", ".sqlite", ".sqlite3")):
                db_path = os.path.join(root, name)
                break
        if db_path:
            break

if db_path is None:
    print("WARNING: Existing SQLite database was not found.")
    print("Database migration will be created when the backend initializes it.")
    raise SystemExit(0)

print("Database:", db_path)

# ------------------------------------------------------------
# 2. Backup database
# ------------------------------------------------------------
stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
backup = os.path.join(BASE, "phase1_backup", f"database_{stamp}.bak")
shutil.copy2(db_path, backup)
print("Database backup:", backup)

# ------------------------------------------------------------
# 3. Connect
# ------------------------------------------------------------
conn = sqlite3.connect(db_path)
conn.execute("PRAGMA foreign_keys = ON")
cur = conn.cursor()

def table_exists(name):
    cur.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
        (name,)
    )
    return cur.fetchone() is not None

def column_exists(table, column):
    if not table_exists(table):
        return False
    cur.execute(f"PRAGMA table_info({table})")
    return any(row[1] == column for row in cur.fetchall())

# ------------------------------------------------------------
# 4. User settings table
# ------------------------------------------------------------
cur.execute("""
CREATE TABLE IF NOT EXISTS user_settings (
    user_id INTEGER PRIMARY KEY,
    haptic INTEGER DEFAULT 1,
    web_search INTEGER DEFAULT 1,
    image_gen INTEGER DEFAULT 1,
    files INTEGER DEFAULT 1,
    memory INTEGER DEFAULT 1,
    voice_assistant INTEGER DEFAULT 1,
    artifacts INTEGER DEFAULT 0,
    code_execution INTEGER DEFAULT 0,
    switch_flagged_model INTEGER DEFAULT 1,
    font TEXT DEFAULT 'inter',
    voice TEXT DEFAULT '',
    volume REAL DEFAULT 1.0,
    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
)
""")

print("OK: user_settings")

# ------------------------------------------------------------
# 5. Feedback table
# ------------------------------------------------------------
cur.execute("""
CREATE TABLE IF NOT EXISTS message_feedback (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    message_id TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    feedback INTEGER NOT NULL,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(message_id, user_id)
)
""")

print("OK: message_feedback")

# ------------------------------------------------------------
# 6. Useful indexes
# ------------------------------------------------------------
indexes = [
    (
        "idx_conversations_user_created",
        "CREATE INDEX IF NOT EXISTS idx_conversations_user_created "
        "ON conversations(user_id, created_at DESC)"
    ),
    (
        "idx_messages_conversation_created",
        "CREATE INDEX IF NOT EXISTS idx_messages_conversation_created "
        "ON messages(conversation_id, created_at ASC)"
    ),
    (
        "idx_feedback_message_user",
        "CREATE INDEX IF NOT EXISTS idx_feedback_message_user "
        "ON message_feedback(message_id, user_id)"
    ),
]

for name, sql in indexes:
    cur.execute(sql)
    print("OK:", name)

# ------------------------------------------------------------
# 7. Optional metadata table for future remote configuration
# ------------------------------------------------------------
cur.execute("""
CREATE TABLE IF NOT EXISTS app_config (
    config_key TEXT PRIMARY KEY,
    config_value TEXT,
    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
)
""")

defaults = {
    "api_version": "1",
    "minimum_android_version": "1",
    "maintenance_mode": "false",
    "remote_config_enabled": "true",
}

for key, value in defaults.items():
    cur.execute(
        "INSERT OR IGNORE INTO app_config(config_key, config_value) VALUES (?, ?)",
        (key, value)
    )

print("OK: app_config")

# ------------------------------------------------------------
# 8. Analyze existing database
# ------------------------------------------------------------
print()
print("=== DATABASE STATUS ===")

for table in [
    "users",
    "conversations",
    "messages",
    "message_feedback",
    "user_settings",
    "app_config",
]:
    if table_exists(table):
        cur.execute(f"SELECT COUNT(*) FROM {table}")
        count = cur.fetchone()[0]
        print(f"{table}: {count} rows")
    else:
        print(f"{table}: NOT PRESENT")

conn.commit()
conn.close()

print()
print("=" * 60)
print("PHASE 1 DATABASE MIGRATION SUCCESSFUL")
print("=" * 60)
