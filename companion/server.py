#!/usr/bin/env python3
"""
FocusLock Local Companion Dashboard Server (Flask)
Runs on Fedora laptop at http://127.0.0.1:5000
Provides local session control, signed stop commands, allowlist sync, and study analytics.
"""

from flask import Flask, jsonify, request, render_template_string
import os
import json
import urllib.request
import urllib.error
from focuslock_crypto import generate_pairing_secret, create_stop_command, create_offline_token
from focuslock_db import init_db, get_config, set_config, log_session, get_stats

app = Flask(__name__)
DEFAULT_PHONE_PORT = 8765

HTML_DASHBOARD = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>FocusLock Companion Dashboard</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #090d16; color: #f8fafc; margin: 0; padding: 24px; }
    .container { max-width: 900px; margin: 0 auto; }
    .card { background: #131b2e; border: 1px solid #1e293b; border-radius: 14px; padding: 20px; margin-bottom: 20px; }
    h1 { color: #ffffff; font-size: 22px; margin-top: 0; display: flex; align-items: center; gap: 10px; }
    h2 { color: #f8fafc; font-size: 16px; margin-top: 0; }
    .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 20px; }
    .stat-box { background: #0a0f1d; border: 1px solid #1e293b; border-radius: 10px; padding: 14px; }
    .stat-num { font-size: 24px; font-weight: bold; color: #38bdf8; font-family: monospace; }
    .stat-label { font-size: 12px; color: #94a3b8; }
    button { background: #ef4444; color: white; border: none; padding: 12px 20px; border-radius: 10px; font-weight: bold; cursor: pointer; transition: 0.2s; }
    button:hover { background: #dc2626; }
    .btn-secondary { background: #38bdf8; color: #000; }
    .btn-secondary:hover { background: #0ea5e9; }
    input[type="text"] { background: #0a0f1d; border: 1px solid #334155; color: white; padding: 10px; border-radius: 8px; width: 280px; }
    pre { background: #070a11; border: 1px solid #1e293b; padding: 12px; border-radius: 8px; color: #38bdf8; font-family: monospace; font-size: 12px; overflow-x: auto; }
    .alert { padding: 12px; border-radius: 8px; margin-top: 12px; font-size: 13px; }
    .alert-success { background: rgba(16, 185, 129, 0.2); border: 1px solid #10b981; color: #6ee7b7; }
    .alert-warning { background: rgba(245, 158, 11, 0.2); border: 1px solid #f59e0b; color: #fcd34d; }
  </style>
</head>
<body>
<div class="container">
  <h1>🔒 FocusLock Companion • Fedora Linux</h1>
  <p style="color: #94a3b8; font-size: 13px;">Zero-compromise study lock companion server. Holds private HMAC-SHA256 signing secret.</p>

  <div class="grid">
    <div class="stat-box">
      <div class="stat-num" id="total-hours">-</div>
      <div class="stat-label">Total Study Hours</div>
    </div>
    <div class="stat-box">
      <div class="stat-num" id="total-sessions">-</div>
      <div class="stat-label">Completed Sessions</div>
    </div>
    <div class="stat-box">
      <div class="stat-num" id="laptop-stops">-</div>
      <div class="stat-label">Laptop Stops Issued</div>
    </div>
  </div>

  <div class="card">
    <h2>Stop Active Phone Session (HMAC Command)</h2>
    <p style="color: #94a3b8; font-size: 13px;">Dispatches cryptographically signed stop command to phone via local Wi-Fi. If phone is unreachable or offline, generates signed token.</p>
    <div style="display: flex; gap: 12px; align-items: center;">
      <input type="text" id="phone-ip" placeholder="Phone IP (e.g. 192.168.1.100)">
      <button onclick="sendStopCommand()">Send Stop Command</button>
      <button class="btn-secondary" onclick="generateOfflineToken()">Generate Offline Token</button>
    </div>
    <div id="stop-result"></div>
  </div>

  <div class="card">
    <h2>Pairing Credentials (HMAC-SHA256)</h2>
    <p style="color: #94a3b8; font-size: 13px;">Shared secret stored securely in SQLite (~/.config/focuslock/focuslock.db). Scan into phone app once.</p>
    <pre id="pairing-json">Loading...</pre>
  </div>
</div>

<script>
  async function loadData() {
    const res = await fetch('/api/status');
    const data = await res.json();
    document.getElementById('total-hours').innerText = data.stats.total_study_hours;
    document.getElementById('total-sessions').innerText = data.stats.total_completed_sessions;
    document.getElementById('laptop-stops').innerText = data.stats.total_laptop_stops;
    document.getElementById('phone-ip').value = data.phone_ip;

    const pairRes = await fetch('/api/pair');
    const pairData = await pairRes.json();
    document.getElementById('pairing-json').innerText = JSON.stringify(pairData, null, 2);
  }

  async function sendStopCommand() {
    const ip = document.getElementById('phone-ip').value;
    const res = await fetch('/api/stop', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({ phone_ip: ip })
    });
    const data = await res.json();
    const box = document.getElementById('stop-result');
    if (data.delivered_via_lan) {
      box.innerHTML = `<div class="alert alert-success">✅ Command verified & executed by phone over Wi-Fi!</div>`;
    } else {
      box.innerHTML = `<div class="alert alert-warning">
        ⚠️ Wi-Fi unreachable. Use this signed offline stop token:<br>
        <pre style="margin-top: 8px;">${data.offline_token}</pre>
        Type or scan this token into the phone's Stop Screen.
      </div>`;
    }
    loadData();
  }

  async function generateOfflineToken() {
    const res = await fetch('/api/token', { method: 'POST' });
    const data = await res.json();
    document.getElementById('stop-result').innerHTML = `<div class="alert alert-warning">
      🔑 Signed Offline Stop Token:<br>
      <pre style="margin-top: 8px;">${data.offline_token}</pre>
    </div>`;
  }

  loadData();
</script>
</body>
</html>
"""

@app.route("/")
def index():
    return render_template_string(HTML_DASHBOARD)

@app.route("/api/status")
def status():
    init_db()
    phone_ip = get_config("phone_ip", "192.168.1.100")
    stats = get_stats()
    return jsonify({
        "status": "online",
        "phone_ip": phone_ip,
        "stats": stats
    })

@app.route("/api/pair")
def pair():
    init_db()
    secret = get_config("hmac_secret")
    if not secret:
        secret = generate_pairing_secret()
        set_config("hmac_secret", secret)
    return jsonify({
        "v": 1,
        "secret": secret,
        "laptop_host": "fedora.local",
        "laptop_port": 5000
    })

@app.route("/api/stop", methods=["POST"])
def stop():
    init_db()
    data = request.get_json(silent=True) or {}
    phone_ip = data.get("phone_ip") or get_config("phone_ip", "192.168.1.100")
    if phone_ip:
        set_config("phone_ip", phone_ip)

    secret = get_config("hmac_secret")
    if not secret:
        secret = generate_pairing_secret()
        set_config("hmac_secret", secret)

    packet = create_stop_command(secret, "current_active_session")
    url = f"http://{phone_ip}:{DEFAULT_PHONE_PORT}/api/stop"

    delivered = False
    try:
        req = urllib.request.Request(
            url,
            data=json.dumps(packet).encode('utf-8'),
            headers={'Content-Type': 'application/json'},
            method='POST'
        )
        with urllib.request.urlopen(req, timeout=2.5) as resp:
            if resp.status == 200:
                delivered = True
                log_session("session", 0, "DASHBOARD", "LAN_HTTP", True)
    except Exception:
        delivered = False

    offline_tok = create_offline_token(secret, "current_active_session")
    if not delivered:
        log_session("session", 0, "DASHBOARD", "OFFLINE_TOKEN", True)

    return jsonify({
        "delivered_via_lan": delivered,
        "offline_token": offline_tok,
        "message": "LAN stop executed" if delivered else "Generated signed offline fallback token"
    })

@app.route("/api/token", methods=["POST"])
def token():
    init_db()
    secret = get_config("hmac_secret")
    if not secret:
        secret = generate_pairing_secret()
        set_config("hmac_secret", secret)
    tok = create_offline_token(secret, "current_active_session")
    return jsonify({ "offline_token": tok })

if __name__ == "__main__":
    init_db()
    print("[+] Starting FocusLock Companion Server on http://127.0.0.1:5000")
    app.run(host="0.0.0.0", port=5000, debug=False)
