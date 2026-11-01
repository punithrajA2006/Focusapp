#!/usr/bin/env python3
"""FocusLock CLI: pair the phone, stop a session, or make an offline token. Run server.py alongside."""
import argparse
import json
import sys
import time

from focuslock_crypto import generate_pairing_secret, create_offline_token
from focuslock_db import (
    init_db, get_config, set_config, log_session, get_stats,
    request_stop, clear_stop, stop_pending, get_phone_state,
)
from server import lan_ip, PHONE_PORT


def cmd_pair(args):
    init_db()
    secret = get_config("hmac_secret")
    if not secret or args.force:
        secret = generate_pairing_secret()
        set_config("hmac_secret", secret)
        print("[+] Generated a fresh 256-bit pairing secret.")
    payload = {"v": 2, "secret": secret, "laptop_host": lan_ip(), "laptop_port": PHONE_PORT}
    print("\nPhone app -> Pair laptop -> paste this JSON (keep it private):\n")
    print(json.dumps(payload))
    print()


def cmd_stop(args):
    init_db()
    if not get_config("hmac_secret"):
        sys.exit("[!] Run 'cli.py pair' first.")
    if not get_phone_state()["online"]:
        print("[!] Phone has not checked in recently (is server.py running and the phone on Wi-Fi?).")
        print("    The stop stays armed for 15 minutes. Offline fallback: cli.py token --code <8 chars>")
    request_stop()
    print("[*] Stop armed. Waiting for the phone to pick it up...")
    for _ in range(args.wait):
        time.sleep(1)
        if not stop_pending():
            print("[SUCCESS] Phone received the signed stop command.")
            return
    print("[-] Not delivered yet; still armed. Use 'cli.py cancel' to withdraw it.")


def cmd_cancel(args):
    init_db()
    clear_stop()
    print("[+] Pending stop cancelled.")


def cmd_token(args):
    init_db()
    secret = get_config("hmac_secret")
    if not secret:
        sys.exit("[!] Run 'cli.py pair' first.")
    code = args.code.strip().lower()
    if len(code) != 8:
        sys.exit("[!] --code must be the 8-character session code shown on the phone.")
    log_session(f"token-{code}-{int(time.time())}", 0, "COMMAND", "LAPTOP_OFFLINE_TOKEN", True)
    print(create_offline_token(secret, code))


def cmd_status(args):
    init_db()
    st, ph = get_stats(), get_phone_state()
    print(f"Paired        : {'yes' if get_config('hmac_secret') else 'no'}")
    print(f"Phone         : {'online, session ' + str(ph.get('session_code')) + ', ' + str(ph.get('remaining_s', 0) // 60) + ' min left' if ph['online'] else 'not connected'}")
    print(f"Stop armed    : {stop_pending()}")
    print(f"Study hours   : {st['total_study_hours']}  Sessions: {st['total_completed_sessions']}  Laptop stops: {st['total_laptop_stops']}")


def main():
    ap = argparse.ArgumentParser(description="FocusLock companion CLI")
    sub = ap.add_subparsers(dest="command")
    p = sub.add_parser("pair"); p.add_argument("--force", action="store_true")
    p = sub.add_parser("stop"); p.add_argument("--wait", type=int, default=20)
    sub.add_parser("cancel")
    p = sub.add_parser("token"); p.add_argument("--code", required=True)
    sub.add_parser("status")
    a = ap.parse_args()
    {"pair": cmd_pair, "stop": cmd_stop, "cancel": cmd_cancel, "token": cmd_token, "status": cmd_status}.get(
        a.command, lambda _: ap.print_help())(a)


if __name__ == "__main__":
    main()
