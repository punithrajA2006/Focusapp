#!/usr/bin/env python3
"""
FocusLock companion server (Fedora laptop).

Two separate listeners, on purpose:

  ADMIN  127.0.0.1:5000  dashboard, pairing secret, "stop phone" button, offline tokens.
                         Loopback only, so the phone (or anyone on Wi-Fi) can never reach it.
  PHONE  0.0.0.0:5001    ONE endpoint, POST /poll. Every request must carry a valid HMAC from
                         the paired phone. The reply is a signed stop command only when YOU armed
                         a stop on the laptop. Nothing else is exposed to the network.
"""
import json
import socket
import threading
import time

from flask import Flask, jsonify, request, render_template_string

from focuslock_crypto import (
    generate_pairing_secret, create_stop_command, create_offline_token, verify_poll_request,
)
from focuslock_db import (
    init_db, get_config, set_config, log_session, get_stats,
    request_stop, clear_stop, stop_pending, save_phone_state, get_phone_state,
)

ADMIN_PORT = 5000
PHONE_PORT = 5001

admin = Flask("focuslock-admin")
phone = Flask("focuslock-phone")

_seen_nonces: dict = {}
_nonce_lock = threading.Lock()


def lan_ip() -> str:
    """Best-effort LAN address of this laptop (no packets are actually sent)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def get_secret() -> str:
    secret = get_config("hmac_secret")
    if not secret:
        secret = generate_pairing_secret()
        set_config("hmac_secret", secret)
    return secret


def nonce_is_fresh(nonce: str) -> bool:
    now = time.time()
    with _nonce_lock:
        for n in [n for n, t in _seen_nonces.items() if now - t > 300]:
            del _seen_nonces[n]
        if not nonce or nonce in _seen_nonces:
            return False
        _seen_nonces[nonce] = now
        return True


# --------------------------------------------------------------------------- phone side
@phone.route("/poll", methods=["POST"])
def poll():
    init_db()
    packet = request.get_json(silent=True) or {}
    ok, why = verify_poll_request(get_secret(), packet)
    if not ok:
        return jsonify({"error": why}), 403
    payload = packet["payload"]
    if not nonce_is_fresh(payload.get("nonce", "")):
        return jsonify({"error": "replayed nonce"}), 403

    code = str(payload.get("session_id", ""))
    save_phone_state(code, int(payload.get("remaining_s", 0)))

    if stop_pending():
        clear_stop()
        log_session(f"stop-{code}-{int(time.time())}", 0, "COMMAND", "LAPTOP_POLL", True)
        return jsonify({"stop": create_stop_command(get_secret(), code)})
    return jsonify({"stop": None})


# --------------------------------------------------------------------------- admin side
DASHBOARD = """
<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>FocusLock Companion</title>
<style>
:root{--bg:#070b14;--card:#101828;--line:#1d2a44;--txt:#f1f5f9;--mut:#8da0bd;--blue:#38bdf8;--red:#f43f5e;--green:#34d399;--amber:#fbbf24}
*{box-sizing:border-box}body{margin:0;background:radial-gradient(900px 500px at 10% -10%,#16264a 0,transparent 60%),var(--bg);color:var(--txt);font:15px/1.5 system-ui,sans-serif;padding:28px}
.wrap{max-width:880px;margin:auto}h1{margin:0 0 4px;font-size:24px}p.sub{margin:0 0 22px;color:var(--mut)}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-bottom:18px}
.card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:18px;margin-bottom:18px}
.stat b{display:block;font:700 28px ui-monospace,monospace;color:var(--blue)}.stat span{color:var(--mut);font-size:12px}
h2{margin:0 0 6px;font-size:16px}.mut{color:var(--mut);font-size:13px}
button{border:0;border-radius:12px;padding:12px 18px;font-weight:700;cursor:pointer;color:#fff;background:var(--red)}
button.alt{background:#1e2d4a;color:var(--txt)}button:disabled{opacity:.5}
.pill{display:inline-flex;align-items:center;gap:8px;padding:6px 12px;border-radius:99px;background:#0b1220;border:1px solid var(--line);font-size:13px}
.dot{width:9px;height:9px;border-radius:50%;background:var(--amber)}.on .dot{background:var(--green)}
pre{background:#080d18;border:1px solid var(--line);border-radius:10px;padding:12px;overflow:auto;color:var(--blue);font-size:12px}
input{background:#080d18;border:1px solid var(--line);color:var(--txt);padding:11px;border-radius:10px;width:200px;font:600 15px ui-monospace,monospace}
.row{display:flex;gap:10px;flex-wrap:wrap;align-items:center;margin-top:12px}
</style></head><body><div class="wrap">
<h1>FocusLock Companion</h1><p class="sub">Only this laptop can end a session early. This page is reachable from this machine only.</p>
<div class="grid">
 <div class="card stat"><b id="h">-</b><span>Total study hours</span></div>
 <div class="card stat"><b id="s">-</b><span>Completed sessions</span></div>
 <div class="card stat"><b id="l">-</b><span>Laptop stops issued</span></div>
</div>
<div class="card"><h2>Phone session</h2>
 <div id="phone" class="pill"><span class="dot"></span><span id="ptxt">checking...</span></div>
 <div class="row"><button id="stopbtn" onclick="armStop()">Stop phone session</button>
  <button class="alt" onclick="cancelStop()">Cancel pending stop</button></div>
 <p class="mut" id="msg"></p></div>
<div class="card"><h2>Offline fallback token</h2>
 <p class="mut">Use only if the phone has no Wi-Fi. Type the 8-character session code shown on the phone.</p>
 <div class="row"><input id="code" maxlength="8" placeholder="session code"><button class="alt" onclick="tok()">Generate token</button></div>
 <pre id="tok" style="display:none"></pre></div>
<div class="card"><h2>Pairing</h2><p class="mut">Phone app → Pair laptop → paste this JSON. Keep it private.</p>
 <pre id="pair">loading...</pre><button class="alt" onclick="navigator.clipboard.writeText(document.getElementById('pair').innerText)">Copy</button></div>
</div><script>
const j=(u,o)=>fetch(u,o).then(r=>r.json());
async function load(){const d=await j('/api/status');h.innerText=d.stats.total_study_hours;s.innerText=d.stats.total_completed_sessions;l.innerText=d.stats.total_laptop_stops;
 const p=d.phone,e=document.getElementById('phone');e.className='pill'+(p.online?' on':'');
 ptxt.innerText=p.online?`Online · session ${p.session_code||'idle'} · ${Math.round((p.remaining_s||0)/60)} min left`:'Phone not connected';
 msg.innerText=d.stop_pending?'Stop armed: waiting for the phone to check in (usually a few seconds).':'';}
async function armStop(){await j('/api/stop',{method:'POST'});load()}
async function cancelStop(){await j('/api/stop/cancel',{method:'POST'});load()}
async function tok(){const r=await j('/api/token',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({session_code:code.value})});
 const t=document.getElementById('tok');t.style.display='block';t.innerText=r.offline_token||r.error}
(async()=>{pair.innerText=JSON.stringify(await j('/api/pair'),null,2);load();setInterval(load,3000)})();
</script></body></html>
"""


@admin.route("/")
def index():
    return render_template_string(DASHBOARD)


@admin.route("/api/status")
def status():
    init_db()
    return jsonify({"stats": get_stats(), "phone": get_phone_state(), "stop_pending": stop_pending()})


@admin.route("/api/pair")
def pair():
    init_db()
    return jsonify({"v": 2, "secret": get_secret(), "laptop_host": lan_ip(), "laptop_port": PHONE_PORT})


@admin.route("/api/stop", methods=["POST"])
def stop():
    init_db()
    request_stop()
    return jsonify({"armed": True})


@admin.route("/api/stop/cancel", methods=["POST"])
def stop_cancel():
    init_db()
    clear_stop()
    return jsonify({"armed": False})


@admin.route("/api/token", methods=["POST"])
def token():
    init_db()
    code = ((request.get_json(silent=True) or {}).get("session_code") or "").strip().lower()
    if len(code) != 8:
        return jsonify({"error": "Enter the 8-character session code shown on the phone."}), 400
    log_session(f"token-{code}-{int(time.time())}", 0, "COMMAND", "LAPTOP_OFFLINE_TOKEN", True)
    return jsonify({"offline_token": create_offline_token(get_secret(), code)})


def main():
    init_db()
    get_secret()
    threading.Thread(
        target=lambda: phone.run(host="0.0.0.0", port=PHONE_PORT, debug=False, use_reloader=False),
        daemon=True,
    ).start()
    print(f"[+] Dashboard (this laptop only): http://127.0.0.1:{ADMIN_PORT}")
    print(f"[+] Phone link: {lan_ip()}:{PHONE_PORT}  (signed requests only)")
    admin.run(host="127.0.0.1", port=ADMIN_PORT, debug=False, use_reloader=False)


if __name__ == "__main__":
    main()
