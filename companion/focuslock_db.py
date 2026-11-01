#!/usr/bin/env python3
"""
FocusLock Local SQLite Storage
Handles pairing secret, session history, streak analytics, and allowlists.
"""

import sqlite3
import os
import json
import time
from typing import Optional, Dict, Any, List

DB_PATH = os.path.expanduser("~/.config/focuslock/focuslock.db")

def init_db(db_path: str = DB_PATH):
    os.makedirs(os.path.dirname(db_path), exist_ok=True)
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    # Config table (pairing secret, phone IP, port)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS config (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
    );
    """)

    # Session logs table
    cur.execute("""
    CREATE TABLE IF NOT EXISTS session_history (
        id TEXT PRIMARY KEY,
        start_time INTEGER NOT NULL,
        duration_minutes INTEGER NOT NULL,
        mode TEXT NOT NULL,
        stopped_by TEXT NOT NULL, -- 'TIMER' or 'LAPTOP_CLI' or 'OFFLINE_TOKEN'
        completed_successfully INTEGER NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    """)

    # Whitelist & YouTube filters table
    cur.execute("""
    CREATE TABLE IF NOT EXISTS filters (
        type TEXT NOT NULL, -- 'APP_ALLOWED', 'APP_BLOCKED', 'YT_CHANNEL', 'YT_KEYWORD'
        item TEXT NOT NULL,
        PRIMARY KEY (type, item)
    );
    """)

    conn.commit()
    conn.close()

def get_config(key: str, default: Optional[str] = None, db_path: str = DB_PATH) -> Optional[str]:
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    cur.execute("SELECT value FROM config WHERE key = ?", (key,))
    row = cur.fetchone()
    conn.close()
    return row[0] if row else default

def set_config(key: str, value: str, db_path: str = DB_PATH):
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    cur.execute("INSERT OR REPLACE INTO config (key, value) VALUES (?, ?)", (key, value))
    conn.commit()
    conn.close()

def log_session(session_id: str, duration_mins: int, mode: str, stopped_by: str, completed: bool, db_path: str = DB_PATH):
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    cur.execute("""
    INSERT OR REPLACE INTO session_history (id, start_time, duration_minutes, mode, stopped_by, completed_successfully)
    VALUES (?, ?, ?, ?, ?, ?)
    """, (session_id, int(time.time()), duration_mins, mode, stopped_by, 1 if completed else 0))
    conn.commit()
    conn.close()

def get_stats(db_path: str = DB_PATH) -> Dict[str, Any]:
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    cur.execute("SELECT COUNT(*), COALESCE(SUM(duration_minutes), 0) FROM session_history WHERE completed_successfully = 1")
    total_completed, total_minutes = cur.fetchone()

    cur.execute("SELECT COUNT(*) FROM session_history WHERE stopped_by LIKE '%LAPTOP%'")
    laptop_stops = cur.fetchone()[0]

    cur.execute("SELECT id, start_time, duration_minutes, mode, stopped_by, completed_successfully FROM session_history ORDER BY start_time DESC LIMIT 10")
    recent = cur.fetchall()

    conn.close()

    return {
        "total_completed_sessions": total_completed,
        "total_study_hours": round(total_minutes / 60.0, 1),
        "total_laptop_stops": laptop_stops,
        "recent_sessions": recent
    }
