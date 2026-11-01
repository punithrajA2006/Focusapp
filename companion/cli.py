#!/usr/bin/env python3
"""
FocusLock Fedora Linux CLI
Terminal-first companion interface for session management and cryptographic unlocking.
"""

import sys
import argparse
import json
import urllib.request
import urllib.error
from focuslock_crypto import (
    generate_pairing_secret,
    create_stop_command,
    create_offline_token
)
from focuslock_db import init_db, get_config, set_config, log_session, get_stats

DEFAULT_PHONE_PORT = 8765

def cmd_pair(args):
    """Generates or retrieves pairing secret and displays QR pairing string."""
    init_db()
    secret = get_config("hmac_secret")
    if not secret or args.force:
        secret = generate_pairing_secret()
        set_config("hmac_secret", secret)
        print("[+] Generated fresh 256-bit pairing secret.")
    else:
        print("[*] Using existing pairing secret.")

    phone_ip = get_config("phone_ip", "192.168.1.100")

    pairing_payload = {
        "v": 1,
        "secret": secret,
        "laptop_host": "fedora.local",
        "laptop_port": 5000,
        "created_at": 1700000000
    }

    raw_json = json.dumps(pairing_payload)
    print("\n" + "=" * 60)
    print("FOCUSLOCK PAIRING INFORMATION")
    print("=" * 60)
    print(f"HMAC Secret (Hex): {secret}")
    print(f"Configured Phone IP: {phone_ip}")
    print("\nScan this raw payload in FocusLock Phone App -> Pair with Laptop:")
    print("-" * 60)
    print(raw_json)
    print("-" * 60)
    print("To update phone IP on your local Wi-Fi: focuslock config --ip <PHONE_IP>\n")

def cmd_stop(args):
    """Sends HMAC signed stop command over Wi-Fi, or outputs offline signed token."""
    init_db()
    secret = get_config("hmac_secret")
    if not secret:
        print("[!] No pairing secret found. Run 'focuslock pair' first.")
        sys.exit(1)

    phone_ip = get_config("phone_ip", "192.168.1.100")
    session_id = args.session_id or "current_active_session"

    print(f"[*] Preparing signed stop command for session: {session_id}...")
    packet = create_stop_command(secret, session_id)
    url = f"http://{phone_ip}:{DEFAULT_PHONE_PORT}/api/stop"

    dispatched = False
    try:
        req = urllib.request.Request(
            url,
            data=json.dumps(packet).encode('utf-8'),
            headers={'Content-Type': 'application/json'},
            method='POST'
        )
        print(f"[*] Dispatching signed command to phone at {url} (Timeout 3s)...")
        with urllib.request.urlopen(req, timeout=3.0) as resp:
            if resp.status == 200:
                body = json.loads(resp.read().decode('utf-8'))
                print(f"[SUCCESS] Phone verified signature and stopped session: {body.get('message', 'OK')}")
                log_session(session_id, 0, "COMMAND", "LAPTOP_LAN_HTTP", True)
                dispatched = True
    except (urllib.error.URLError, TimeoutError, ConnectionRefusedError, OSError) as e:
        print(f"[-] Local Wi-Fi dispatch failed or phone unreachable ({e}).")

    if not dispatched:
        print("\n" + "!" * 60)
        print("OFFLINE CRYPTOGRAPHIC STOP FALLBACK")
        print("!" * 60)
        print("Local Wi-Fi is unreachable. Because your Fedora laptop holds the private")
        print("signing key, here is your one-time signed offline stop token:")
        print("-" * 60)
        token = create_offline_token(secret, session_id)
        print(token)
        print("-" * 60)
        print("Instructions: On your phone, tap 'Enter Offline Stop Token' and paste or scan the token above.")
        print("The phone will verify the HMAC signature offline without needing Wi-Fi.\n")
        log_session(session_id, 0, "COMMAND", "OFFLINE_TOKEN_GENERATED", True)

def cmd_token(args):
    """Directly generates an offline stop token without attempting network request."""
    init_db()
    secret = get_config("hmac_secret")
    if not secret:
        print("[!] Run 'focuslock pair' first.")
        sys.exit(1)
    session_id = args.session_id or "current_active_session"
    token = create_offline_token(secret, session_id)
    print("\n[+] Generated One-Time Signed Offline Stop Token:")
    print("-" * 60)
    print(token)
    print("-" * 60)

def cmd_config(args):
    """Updates configuration parameters (e.g. phone IP)."""
    init_db()
    if args.ip:
        set_config("phone_ip", args.ip)
        print(f"[+] Updated phone IP: {args.ip}")
    if args.secret:
        set_config("hmac_secret", args.secret)
        print(f"[+] Updated pairing secret.")

def cmd_status(args):
    """Displays local study statistics and pairing status."""
    init_db()
    secret = get_config("hmac_secret")
    phone_ip = get_config("phone_ip", "Not configured")
    stats = get_stats()

    print("=" * 50)
    print("FOCUSLOCK SYSTEM STATUS")
    print("=" * 50)
    print(f"Pairing Status      : {'Configured' if secret else 'Unpaired'}")
    print(f"Target Phone IP     : {phone_ip}:{DEFAULT_PHONE_PORT}")
    print(f"Total Study Hours   : {stats['total_study_hours']} hrs")
    print(f"Completed Sessions  : {stats['total_completed_sessions']}")
    print(f"Laptop Stops Issued : {stats['total_laptop_stops']}")
    print("=" * 50)

def main():
    parser = argparse.ArgumentParser(description="FocusLock Fedora Linux Companion CLI")
    subparsers = parser.add_subparsers(dest="command", help="Command to execute")

    # Pair
    p_pair = subparsers.add_parser("pair", help="Generate or view pairing secret and QR string")
    p_pair.add_argument("--force", action="store_true", help="Force regenerate new secret")

    # Stop
    p_stop = subparsers.add_parser("stop", help="Send signed stop command or generate offline token")
    p_stop.add_argument("--session-id", help="Optional specific session ID to stop")

    # Token
    p_token = subparsers.add_parser("token", help="Generate signed offline stop token")
    p_token.add_argument("--session-id", help="Optional session ID")

    # Config
    p_conf = subparsers.add_parser("config", help="Configure companion settings")
    p_conf.add_argument("--ip", help="Set phone local IP address")
    p_conf.add_argument("--secret", help="Set pairing secret")

    # Status
    subparsers.add_parser("status", help="View local study stats and connection info")

    args = parser.parse_args()
    if args.command == "pair":
        cmd_pair(args)
    elif args.command == "stop":
        cmd_stop(args)
    elif args.command == "token":
        cmd_token(args)
    elif args.command == "config":
        cmd_config(args)
    elif args.command == "status":
        cmd_status(args)
    else:
        parser.print_help()

if __name__ == "__main__":
    main()
