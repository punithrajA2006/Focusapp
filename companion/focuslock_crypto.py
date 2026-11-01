#!/usr/bin/env python3
"""
FocusLock Cryptographic Security Engine
Provides HMAC-SHA256 signature generation and offline token encoding.
Uses strictly Python standard library modules (hmac, hashlib, secrets, time, base64).
"""

import hmac
import hashlib
import json
import base64
import secrets
import time
from typing import Dict, Any, Tuple

def generate_pairing_secret() -> str:
    """Generates a secure 256-bit (32 bytes) hex-encoded shared secret."""
    return secrets.token_hex(32)

def sign_payload(secret_hex: str, payload: Dict[str, Any]) -> str:
    """Computes HMAC-SHA256 hex digest over canonical JSON payload."""
    canonical_json = json.dumps(payload, sort_keys=True, separators=(',', ':')).encode('utf-8')
    secret_bytes = bytes.fromhex(secret_hex)
    return hmac.new(secret_bytes, canonical_json, hashlib.sha256).hexdigest()

def verify_payload(secret_hex: str, payload: Dict[str, Any], signature: str) -> bool:
    """Verifies HMAC-SHA256 using constant-time comparison."""
    expected_sig = sign_payload(secret_hex, payload)
    return hmac.compare_digest(expected_sig, signature)

def create_stop_command(secret_hex: str, session_id: str) -> Dict[str, Any]:
    """
    Creates a signed stop command packet.
    Includes timestamp and random nonce for replay attack prevention.
    """
    timestamp = int(time.time())
    nonce = secrets.token_hex(16)
    payload = {
        "action": "STOP_SESSION",
        "session_id": session_id,
        "timestamp": timestamp,
        "nonce": nonce
    }
    signature = sign_payload(secret_hex, payload)
    return {
        "payload": payload,
        "signature": signature
    }

def create_offline_token(secret_hex: str, session_id: str) -> str:
    """
    Generates a compact Base64-encoded offline stop token that can be typed
    or scanned into the phone when local Wi-Fi is unavailable.
    Format: Base64Url( JSON({payload, signature}) )
    """
    packet = create_stop_command(secret_hex, session_id)
    raw_json = json.dumps(packet).encode('utf-8')
    return base64.urlsafe_b64encode(raw_json).decode('utf-8').rstrip('=')

def verify_offline_token(secret_hex: str, token_str: str, max_age_seconds: int = 300) -> Tuple[bool, str]:
    """
    Validates an offline stop token.
    Ensures HMAC signature is valid and token is within valid window (default 5 minutes).
    """
    try:
        # Add padding back if necessary
        padded = token_str + '=' * (-len(token_str) % 4)
        raw_bytes = base64.urlsafe_b64decode(padded)
        packet = json.loads(raw_bytes.decode('utf-8'))

        payload = packet.get("payload", {})
        signature = packet.get("signature", "")

        # 1. Signature check
        if not verify_payload(secret_hex, payload, signature):
            return False, "Invalid HMAC signature: token forged or wrong pairing key."

        # 2. Timestamp freshness check
        token_time = payload.get("timestamp", 0)
        current_time = int(time.time())
        if abs(current_time - token_time) > max_age_seconds:
            return False, f"Token expired (age: {abs(current_time - token_time)}s > max {max_age_seconds}s)."

        return True, "Token successfully verified."
    except Exception as e:
        return False, f"Malformed token: {str(e)}"
